package paxoslocker.replica;

import paxoslocker.app.NodeLifecycle;
import paxoslocker.app.ClusterMembership;
import paxoslocker.diagnostics.*;
import paxoslocker.leader.LeaderHeartbeatState;
import paxoslocker.model.*;
import paxoslocker.persistence.PersistentStore;
import paxoslocker.protocol.*;
import paxoslocker.transport.*;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Student implementation point: ordering, re-proposal, catch-up, replay and deduplication.
 */
public class Replica implements NodeLifecycle {
    private static final long LEADER_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(2_000);
    private static final long LEADER_REFRESH_INTERVAL_MS = 250;
    private static final long CATCH_UP_INTERVAL_MS = 500;
    private static final int MAX_ENTRIES = 100;
    protected final NodeId id;
    protected final Transport transport;
    protected final PersistentStore store;
    protected final ParcelLockerStateMachine stateMachine;
    protected final ClusterMembership membership;
    protected final DiagnosticSink diagnostics;
    private static final String STORE_KEY = "state";
    private final AtomicBoolean running = new AtomicBoolean();
    private final Object stateLock = new Object();
    private final ReplicaState state = new ReplicaState();
    private ScheduledExecutorService scheduler;

    public Replica(NodeId id, Transport transport, PersistentStore store, Collection<String> lockerIds,
                   ClusterMembership membership, DiagnosticSink diagnostics) {
        this.id = id;
        this.transport = transport;
        this.store = store;
        this.stateMachine = new ParcelLockerStateMachine(lockerIds);
        this.membership = Objects.requireNonNull(membership);
        this.diagnostics = diagnostics == null ? DiagnosticSink.NOOP : diagnostics;
        if (!membership.replicas().contains(id)) throw new IllegalArgumentException("id is not a replica");
    }

    public void submit(Command command) {
        if (!isRunning()) return;
        UUID requestId = command.requestId();
        synchronized (stateLock) {
            Map<UUID, Command> requestIds = state.executedRequestIds();
            Map<Long, Command> pendingProposals = state.pendingProposals();
            if (requestIds.containsKey(requestId)) {
                if (requestIds.get(requestId).equals(command)) {
                    return;
                } else {
                    throw new IllegalStateException("requestId " + requestId + " does not match command " + command);
                }
            }
            Optional<Map.Entry<Long, Command>> optional = pendingProposals.entrySet().stream()
                    .filter(e -> e.getValue().requestId().equals(requestId)).findFirst();
            if (optional.isPresent()) {
                long slotId = optional.get().getKey();
                if (optional.get().getValue().equals(command)) {
                    sendPendingProposal(slotId, command);
                    return;
                } else {
                    throw new IllegalStateException("requestId " + requestId + " does not match command " + command);
                }
            }
            propose(command);
        }
    }

    public void onDecision(DecisionMessage decision) {
        if (!isRunning()) return;
        long slot =  decision.slot();
        Command command = decision.command();
        learnDecision(slot, command);
        executeCommands();
    }

    public void onDecisionSyncRequest(DecisionSyncRequestMessage request, NodeId peer) {
        if (!isRunning()) return;
        long slot = request.fromSlotInclusive();
        Map<Long, Command> result;
        synchronized (stateLock) {
            result = state.decisions(slot, request.maxEntries());
        }
        transport.send(MessageEnvelope.of(id, peer, new DecisionSyncResponseMessage(slot, result)));
    }

    public void onDecisionSyncResponse(DecisionSyncResponseMessage response, NodeId peer) {
        if (!isRunning()) return;
        learnDecisions(response.decisions());
        executeCommands();
        if (response.decisions().size() == MAX_ENTRIES) {
            sendCatchupMessages();
        }
    }

    public void onHeartbeat(HeartbeatMessage heartbeat, NodeId leader) {
        if (!isRunning()) return;
        synchronized (stateLock) {
            state.updateLeaderHeartbeatState(leader,
                    new LeaderHeartbeatState(heartbeat.active(), System.nanoTime(), heartbeat.ballot()));
        }
        refreshLeader();
    }

    public ReplicaStatus status() {
        synchronized (stateLock) {
            return new ReplicaStatus(state.nextExecutionSlot() - 1, state.decisions(), stateMachine.snapshot(), state.knownLeader(), state.pendingProposals());
        }
    }

    private void learnDecisions(Map<Long, Command> decisions) {
        for (long slot : decisions.keySet()) {
            learnDecision(slot, decisions.get(slot));
        }
    }

    private void learnDecision(long slot, Command command) {
        synchronized (stateLock) {
            Map<Long, Command> decisions = state.decisions();
            if (decisions.containsKey(slot)) {
                if (!decisions.get(slot).equals(command)) {
                    throw new IllegalStateException("requestId " + command.requestId() + " does not match command " + command);
                }
            } else {
                state.addDecision(slot, command);
                storeState();
                recordDecisionLearned(slot, command);
                if (state.pendingProposals().containsKey(slot)) {
                    Command pendingCommand = state.removeProposal(slot);
                    storeState();
                    if (!pendingCommand.equals(command)) {
                        propose(pendingCommand);
                    }
                }
            }
        }
    }

    private void restoreState() {
        if (!isRunning()) return;
        synchronized (stateLock) {
            Optional<ReplicaState.DurableReplicaState> optional =
                    store.load(STORE_KEY, ReplicaState.DurableReplicaState.class);
            if (optional.isEmpty()) return;
            state.storeDurableReplicaState(optional.get());
            executeCommands();
        }
    }

    private void sendCatchupMessages() {
        if (!isRunning()) return;
        long slotId;
        synchronized (stateLock) {
            slotId = state.nextExecutionSlot();
        }
        DecisionSyncRequestMessage message =  new DecisionSyncRequestMessage(id, slotId, MAX_ENTRIES);
        membership.replicas().stream().filter(replica -> !replica.equals(id)).forEach(replica -> transport.send(MessageEnvelope.of(id, replica, message)));
    }

    private void executeCommands() {
        while (canExecuteNextCommand()) {
            executeNextCommand();
        }
    }

    private boolean canExecuteNextCommand() {
        if (!isRunning()) return false;
        synchronized (stateLock) {
            return state.decisions().containsKey(state.nextExecutionSlot());
        }
    }

    private void executeNextCommand() {
        if (!isRunning()) return;
        long slot;
        Command command;
        synchronized (stateLock) {
            slot = state.nextExecutionSlot();
            Map<Long, Command> decisions = state.decisions();
            if (!decisions.containsKey(slot)) return;
            command = decisions.get(slot);
            stateMachine.apply(command);
            state.updateSlotOut();
            state.addExecutedRequestId(command.requestId(), command);
            recordCommandExecuted(slot, command);
        }
    }

    private void storeState() {
        if (!isRunning()) return;
        synchronized (stateLock) {
            store.save(STORE_KEY, state.getDurableReplicaState());
        }
    }

    private void propose(Command command) {
        if (!isRunning()) return;
        synchronized (stateLock) {
            long slot = state.nextProposalSlotAndUpdate();
            state.addProposal(slot, command);
            storeState();
            if (state.knownLeader() != null) sendPendingProposal(slot, command);
        }
    }

    private void sendPendingProposal(long slot, Command command) {
        if (!isRunning()) return;
        NodeId leaderId;
        synchronized (stateLock) {
            leaderId = state.knownLeader();
            if (leaderId == null) return;
            // TODO may need to record/update when is sent in state.RuntimeReplicaState using System.nanoTime()
        }
        transport.send(MessageEnvelope.of(id, leaderId, new ProposeMessage(slot, command)));
    }

    private void resendAllPending() {
        if (!isRunning()) return;
        Map<Long, Command> pendingProposals;
        synchronized (stateLock) {
            pendingProposals = state.pendingProposals();
        }
        for (long slot : pendingProposals.keySet()) {
            sendPendingProposal(slot, pendingProposals.get(slot));
        }
    }

    private void refreshLeader() {
        Map<NodeId, LeaderHeartbeatState> leaderHeartbeatStates;
        NodeId oldLeader, newLeader;
        synchronized (stateLock) {
            leaderHeartbeatStates = Map.copyOf(state.leaderHeartbeatStates());
            oldLeader = state.knownLeader();
            newLeader =  leaderHeartbeatStates.entrySet().stream().filter(e -> e.getValue().active())
                    .filter(e -> System.nanoTime() - e.getValue().lastSeen() < LEADER_TIMEOUT_NANOS)
                    .max((a, b) -> a.getValue().ballot().compareTo(b.getValue().ballot()))
                    .map(Map.Entry::getKey).orElse(null);
            if (!Objects.equals(oldLeader, newLeader)) {
                state.leaderId(newLeader);
            }
        }
        if (!Objects.equals(oldLeader, newLeader)) {
            onLeaderChanged(oldLeader, newLeader);
        }
    }

    private void addSchedules() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::refreshLeader, LEADER_REFRESH_INTERVAL_MS, LEADER_REFRESH_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::sendCatchupMessages, CATCH_UP_INTERVAL_MS, CATCH_UP_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        /*
        TODO
         3. proposal resend
         */
    }

    private void onLeaderChanged(NodeId oldLeaderId, NodeId newLeaderId) {
        if (newLeaderId == null) return;
        resendAllPending();
    }

    private void recordCommandExecuted(long slot, Command command) {
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.REPLICA, ProtocolDiagnosticType.COMMAND_EXECUTED, null,
                slot, command.requestId(), null, command, ""));
    }

    private void recordDecisionLearned(long slot, Command command) {
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.REPLICA, ProtocolDiagnosticType.DECISION_LEARNED, null,
                slot, command.requestId(), null, command, ""));
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        restoreState();
        sendCatchupMessages();
        addSchedules();
        transport.register(id, this::onEnvelope);
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.REPLICA,
                ProtocolDiagnosticType.NODE_STARTED, null, null, null, null, null, ""));
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        scheduler.shutdown();
        transport.unregister(id);
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.REPLICA,
                ProtocolDiagnosticType.NODE_STOPPED, null, null, null, null, null, ""));
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    protected void onEnvelope(MessageEnvelope envelope) {
        if (!running.get() || !envelope.destination().equals(id)) return;
        switch (envelope.message()) {
            case DecisionMessage decision -> { if (membership.leaders().contains(envelope.source())) onDecision(decision); else ignored(envelope, "DECISION source is not a Leader"); }
            case DecisionSyncRequestMessage request -> { if (membership.replicas().contains(envelope.source()) && request.requester().equals(envelope.source())) onDecisionSyncRequest(request, envelope.source()); else ignored(envelope, "spoofed DecisionSync requester"); }
            case DecisionSyncResponseMessage response -> { if (membership.replicas().contains(envelope.source())) onDecisionSyncResponse(response, envelope.source()); else ignored(envelope, "DecisionSync response source is not a Replica"); }
            case HeartbeatMessage heartbeat -> { if (membership.leaders().contains(envelope.source())) onHeartbeat(heartbeat, envelope.source()); else ignored(envelope, "HEARTBEAT source is not a Leader"); }
            default -> ignored(envelope, envelope.message().getClass().getSimpleName());
        }
    }

    private void ignored(MessageEnvelope envelope, String detail) {
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.REPLICA,
                ProtocolDiagnosticType.MESSAGE_IGNORED, null, null, null, envelope.source(), null, detail));
    }

    private static UnsupportedOperationException todo(String text) {
        return new UnsupportedOperationException("TODO(student): " + text);
    }
}
