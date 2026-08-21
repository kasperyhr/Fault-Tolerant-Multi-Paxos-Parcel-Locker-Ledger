package paxoslocker.leader;

import paxoslocker.app.NodeLifecycle;
import paxoslocker.app.ClusterMembership;
import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import paxoslocker.protocol.*;
import paxoslocker.transport.*;
import paxoslocker.worker.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Student implementation point: Phase orchestration, pmax, preemption and failover.
 */
public class Leader implements NodeLifecycle {
    private static final long PREEMPT_BACKOFF_MIN_MS = 200;
    private static final long PREEMPT_BACKOFF_MAX_MS = 800;
    private static final long PREEMPT_BACKOFF_CAP_MS = 5_000;
    private static final long HEARTBEAT_INTERVAL_MS = 500;
    private static final long FAILURE_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(2_000);
    private static final long FAILURE_CHECK_INTERVAL_MS = 250;
    private static final long WORKER_RETRY_INTERVAL_MS = 500;
    protected final NodeId id;
    protected final Transport transport;
    protected final ClusterMembership membership;
    protected final WorkerFactory workerFactory;
    protected final DiagnosticSink diagnostics;
    protected final WorkerHook workerHook;
    protected volatile Scout currentScout;
    protected final Map<CommanderKey, Commander> commanders = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledExecutorService scheduler;
    private final Object stateLock = new Object();
    private final LeaderState state;

    public Leader(NodeId id, Transport transport, ClusterMembership membership,
                  WorkerFactory workerFactory, DiagnosticSink diagnostics, WorkerHook workerHook) {
        this.id = id;
        this.transport = transport;
        this.membership = Objects.requireNonNull(membership);
        this.workerFactory = Objects.requireNonNull(workerFactory);
        this.diagnostics = diagnostics == null ? DiagnosticSink.NOOP : diagnostics;
        this.workerHook = workerHook == null ? WorkerHook.NOOP : workerHook;
        if (!membership.leaders().contains(id)) throw new IllegalArgumentException("id is not a leader");
        state = new LeaderState(id);
    }

    public void onPropose(ProposeMessage proposal) {
        if (!isRunning()) return;
        PValue pValue = null;
        Commander commander = null;
        synchronized (stateLock) {
            if (!isRunning()) return;
            if (!state.proposals().containsKey(proposal.slot())) {
                state.addProposal(proposal.slot(), proposal.command());
                if (state.active()) {
                    pValue = new PValue(state.ballot(), proposal.slot(), proposal.command());
                    commander = createCommander(pValue);
                    cleanCommanders();
                }
            }
        }

        if (commander != null) {
            if (!isRunning()) {
                commander.kill();
                return;
            }
            workerHook.onEvent(WorkerEventType.COMMANDER_CREATED, pValue.ballot(), pValue.slot());
            commander.start();
        }

    }

    public void onAdopted(AdoptedMessage adopted) {
        if (!isRunning()) return;
        Map<PValue, Commander> map = new HashMap<>();
        synchronized (stateLock) {
            if (!isRunning()) return;
            if (adopted.ballot().equals(state.ballot())) {
                state.updateProposal(pmax(adopted.accepted()));
                state.setActive(true);
                state.resetRetries();
                cleanCommanders();
                for (long slot : state.proposals().keySet()) {
                    PValue pValue = new PValue(state.ballot(), slot, state.proposals().get(slot));
                    Commander commander = createCommander(pValue);
                    map.put(pValue, commander);
                }
            }
        }
        sendHeartbeat();
        for (PValue pValue : map.keySet()) {
            if (!isRunning()) break;
            workerHook.onEvent(WorkerEventType.COMMANDER_CREATED, pValue.ballot(), pValue.slot());
            map.get(pValue).start();
        }
        if (!isRunning()) {
            for (PValue pValue : map.keySet()) {
                map.get(pValue).kill();
            }
        }
    }

    public void onPreempted(PreemptedMessage preempted) {
        if (!isRunning()) return;
        synchronized (stateLock) {
            if (!isRunning()) return;
            preemptedMessage(state.ballot());
            handlePreemption(preempted.observedBallot());
        }
    }

    private void setToInactive(BallotNumber ballot) {
        synchronized (stateLock) {
            state.setActive(false);
            state.incrementRetries();
            state.ballotAfter(ballot);
            if (currentScout != null) {
                currentScout.kill();
                currentScout = null;
            }
            for (CommanderKey key: commanders.keySet()) {
                commanders.get(key).kill();
                commanders.remove(key);
            }
        }
        scheduler.schedule(this::retryLogic, getExponentialRetryTime(PREEMPT_BACKOFF_MIN_MS, PREEMPT_BACKOFF_MAX_MS, PREEMPT_BACKOFF_CAP_MS), TimeUnit.MILLISECONDS);
    }

    private void yieldToLeader() {
        synchronized (stateLock) {
            state.setActive(false);
            if (currentScout != null) {
                currentScout.kill();
                currentScout = null;
            }
            for (Commander commander : commanders.values()) {
                commander.kill();
            }
            commanders.clear();
        }
    }

    private void handlePreemption(BallotNumber observedBallot) {
        synchronized (stateLock) {
            state.setActive(false);
            state.incrementRetries();
            state.ballotAfter(observedBallot);
            if (currentScout != null) {
                currentScout.kill();
                currentScout = null;
            }
            for (Commander commander : commanders.values()) {
                commander.kill();
            }
            commanders.clear();
        }
        long delay = getExponentialRetryTime(PREEMPT_BACKOFF_MIN_MS, PREEMPT_BACKOFF_MAX_MS, PREEMPT_BACKOFF_CAP_MS);
        scheduler.schedule(this::checkHeartbeat, delay, TimeUnit.MILLISECONDS);
    }

    public void onHeartbeat(HeartbeatMessage heartbeat, NodeId peerLeader) {
        if (!isRunning()) return;
        boolean shouldYield = false;
        synchronized (stateLock) {
            if (!isRunning()) return;
            state.updatePeerState(peerLeader,
                    new LeaderHeartbeatState(heartbeat.active(), System.nanoTime(), heartbeat.ballot()));
            if (state.active() && heartbeat.active() && heartbeat.ballot().compareTo(state.ballot()) > 0) {
                shouldYield = true;
            }
        }
        if (shouldYield) {
            yieldToLeader();
        }
    }

    public static Map<Long, Command> pmax(Collection<PValue> accepted) {
        return accepted.stream().collect(Collectors.toMap(
                PValue::slot,
                pValue -> pValue,
                (a, b) -> {
                    int cmp = a.ballot().compareTo(b.ballot());
                    if (cmp < 0) return b;
                    if (cmp > 0) return a;
                    if (!a.command().equals(b.command()))
                        throw new IllegalStateException("Contains same ballot, same slot, different command.");
                    return a;
                })).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().command()));
    }

    public LeaderStatus status() {
        synchronized (stateLock) {
            return new LeaderStatus(state.ballot(), state.active(), state.proposals(),
                    currentScout == null ? Optional.empty() : Optional.of(currentScout.ballot()), commanders.keySet());
        }
    }

    private void cleanCommanders() {
        for (Map.Entry<CommanderKey, Commander> entry : commanders.entrySet()) {
            if (entry.getValue().isKilled()) {
                commanders.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private void retryScout() {
        if (!isRunning()) return;
        Scout scout = null;
        synchronized (stateLock) {
            if (state.active()) return;
            if (currentScout != null && !currentScout.isKilled() && currentScout.ballot().equals(state.ballot())) {
                scout = currentScout;
            }
        }
        if (scout != null) {
            scout.start();
        }
    }

    private void retryCommanders() {
        if (!isRunning()) return;
        List<Commander> retryCommanders = new ArrayList<>();
        synchronized (stateLock) {
            if (!state.active()) return;
            for (CommanderKey key : commanders.keySet()) {
                Commander commander = commanders.get(key);
                if (commander.isKilled()) {
                    commanders.remove(key, commander);
                }
                if (commander.pvalue().ballot().equals(state.ballot())) {
                    retryCommanders.add(commander);
                }
            }
        }
        for (Commander commander : retryCommanders) {
            commander.start();
        }
    }

    private void addSchedules() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::checkHeartbeat, FAILURE_CHECK_INTERVAL_MS, FAILURE_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::retryScout, WORKER_RETRY_INTERVAL_MS, WORKER_RETRY_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::retryCommanders, WORKER_RETRY_INTERVAL_MS, WORKER_RETRY_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        transport.register(id, this::onEnvelope);
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.LEADER,
                ProtocolDiagnosticType.NODE_STARTED, null, null, null, null, null, ""));
        /* TODO(student): create Scout and schedule heartbeat/failure suspicion. */
        addSchedules();
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (scheduler != null) {
            scheduler.shutdown();
        }
        transport.unregister(id);
        synchronized (stateLock) {
            if (currentScout != null) {
                currentScout.kill();
                currentScout = null;
            }
            commanders.values().forEach(Commander::kill);
            commanders.clear();
            currentScout = null;
            state.setActive(false);
        }
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.LEADER,
                ProtocolDiagnosticType.NODE_STOPPED, null, null, null, null, null, ""));
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    protected final Scout createScout(BallotNumber ballot) {
        Scout scout = workerFactory.createScout(id, ballot, membership.acceptors(), membership.quorum(),
                transport, workerHook);
        currentScout = scout;
        return scout;
    }

    protected final Commander createCommander(PValue pvalue) {
        Commander commander = workerFactory.createCommander(id, pvalue, membership.acceptors(),
                membership.replicas(), membership.quorum(), transport, workerHook);
        commanders.put(new CommanderKey(pvalue.ballot(), pvalue.slot()), commander);
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.LEADER,
                ProtocolDiagnosticType.COMMANDER_CREATED, pvalue.ballot(), pvalue.slot(),
                pvalue.command().requestId(), null, pvalue.command(), ""));
        return commander;
    }

    protected void onEnvelope(MessageEnvelope envelope) {
        if (!running.get() || !envelope.destination().equals(id)) return;
        switch (envelope.message()) {
            case ProposeMessage proposal -> {
                if (membership.replicas().contains(envelope.source())) onPropose(proposal);
                else ignored(envelope, "PROPOSE source is not a Replica");
            }
            case AdoptedMessage adopted -> onAdopted(adopted);
            case PreemptedMessage preempted -> onPreempted(preempted);
            case P1bMessage p1b -> {
                Scout scout = currentScout;
                if (validAcceptorResponse(envelope, p1b.acceptor()) && scout != null
                        && scout.ballot().equals(p1b.requestedBallot())
                        && p1b.acceptorBallot().compareTo(p1b.requestedBallot()) >= 0) scout.onP1b(p1b);
                else ignored(envelope, "stale, malformed, or uncorrelated P1B");
            }
            case P2bMessage p2b -> {
                Commander commander = commanders.get(new CommanderKey(p2b.requestedBallot(), p2b.slot()));
                if (validAcceptorResponse(envelope, p2b.acceptor()) && commander != null
                        && p2b.acceptorBallot().compareTo(p2b.requestedBallot()) >= 0) commander.onP2b(p2b);
                else ignored(envelope, "stale, malformed, or uncorrelated P2B");
            }
            case HeartbeatMessage heartbeat -> {
                if (membership.leaders().contains(envelope.source())) onHeartbeat(heartbeat, envelope.source());
                else ignored(envelope, "HEARTBEAT source is not a Leader");
            }
            default -> ignored(envelope, envelope.message().getClass().getSimpleName());
        }
    }

    private void checkHeartbeat() {
        if (!isRunning()) return;
        synchronized (stateLock) {
            if (state.active()) return;
            boolean hasFreshActivePeer = state.peerStates().values().stream()
                    .anyMatch(peer ->
                            peer.active() && System.nanoTime() - peer.lastSeen() <= FAILURE_TIMEOUT_NANOS);
            if (hasFreshActivePeer) return;
            if (currentScout != null && !currentScout.isKilled()) return;
        }
        retryLogic();
    }

    private void sendHeartbeat() {
        if (!isRunning()) return;
        BallotNumber ballot;
        boolean active;
        synchronized (stateLock) {
            ballot = state.ballot();
            active = state.active();
        }
        HeartbeatMessage heartbeat = new HeartbeatMessage(ballot, active);
        for (NodeId leader: membership.leaders()){
            if (leader.equals(id)) continue;
            transport.send(MessageEnvelope.of(id, leader, heartbeat));
        }
        for (NodeId replica: membership.replicas()){
            transport.send(MessageEnvelope.of(id, replica, heartbeat));
        }
    }

    private void retryLogic() {
        if (!isRunning()) return;
        BallotNumber ballot = null;
        Scout scout = null;
        synchronized (stateLock) {
            if (!isRunning()) return;
            if (currentScout == null || currentScout.isKilled()) {
                ballot = state.ballot();
                scout = createScout(ballot);
            }
        }
        if (scout != null) {
            if (!isRunning()) {
                scout.kill();
                return;
            }
            workerHook.onEvent(WorkerEventType.SCOUT_CREATED, ballot, null);
            scout.start();
        }
    }

    private long getExponentialRetryTime(long min, long max, long cap) {
        long exp;
        synchronized (stateLock) {
            int retries = Math.min(state.retries(), 4);
            exp = 1L << retries;
        }
        return ThreadLocalRandom.current()
                .nextLong(Math.min(min * exp, cap), Math.max(max * exp, cap));
    }

    private boolean isTimedOut(LeaderHeartbeatState state) {
        return System.nanoTime() - state.lastSeen() > FAILURE_TIMEOUT_NANOS;
    }

    private void preemptedMessage(BallotNumber ballot) {
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.LEADER,
                ProtocolDiagnosticType.LEADER_PREEMPTED, ballot, null, null, null, null, ""));
    }

    private boolean validAcceptorResponse(MessageEnvelope envelope, NodeId claimedAcceptor) {
        return membership.acceptors().contains(envelope.source()) && envelope.source().equals(claimedAcceptor);
    }

    private void ignored(MessageEnvelope envelope, String detail) {
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.LEADER,
                ProtocolDiagnosticType.MESSAGE_IGNORED, null, null, null, envelope.source(), null, detail));
    }

    private static UnsupportedOperationException todo(String text) {
        return new UnsupportedOperationException("TODO(student): " + text);
    }
}
