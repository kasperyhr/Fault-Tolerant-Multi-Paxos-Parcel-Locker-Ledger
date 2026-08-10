package paxoslocker.leader;

import paxoslocker.app.NodeLifecycle;
import paxoslocker.app.ClusterMembership;
import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import paxoslocker.protocol.*;
import paxoslocker.transport.*;
import paxoslocker.worker.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Student implementation point: Phase orchestration, pmax, preemption and failover.
 */
public class Leader implements NodeLifecycle {
    protected final NodeId id;
    protected final Transport transport;
    protected final ClusterMembership membership;
    protected final WorkerFactory workerFactory;
    protected final DiagnosticSink diagnostics;
    protected final WorkerHook workerHook;
    protected volatile Scout currentScout;
    protected final Map<CommanderKey, Commander> commanders = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();

    public Leader(NodeId id, Transport transport, ClusterMembership membership,
                  WorkerFactory workerFactory, DiagnosticSink diagnostics, WorkerHook workerHook) {
        this.id = id;
        this.transport = transport;
        this.membership = Objects.requireNonNull(membership);
        this.workerFactory = Objects.requireNonNull(workerFactory);
        this.diagnostics = diagnostics == null ? DiagnosticSink.NOOP : diagnostics;
        this.workerHook = workerHook == null ? WorkerHook.NOOP : workerHook;
        if (!membership.leaders().contains(id)) throw new IllegalArgumentException("id is not a leader");
    }

    public void onPropose(ProposeMessage proposal) {
        throw todo("Leader.onPropose: retain one value per slot and spawn Commander when active");
    }

    public void onAdopted(AdoptedMessage adopted) {
        throw todo("Leader.onAdopted: apply pmax and activate ballot");
    }

    public void onPreempted(PreemptedMessage preempted) {
        throw todo("Leader.onPreempted: deactivate, advance ballot, back off/retry");
    }

    public void onHeartbeat(HeartbeatMessage heartbeat, NodeId peerLeader) {
        throw todo("Leader.onHeartbeat: update failure suspicion without treating timeout as proof");
    }

    public static Map<Long, Command> pmax(Collection<PValue> accepted) {
        throw todo("Leader.pmax");
    }

    public LeaderStatus status() {
        throw todo("Leader.status: immutable observation only");
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        transport.register(id, this::onEnvelope);
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.LEADER,
                ProtocolDiagnosticType.NODE_STARTED, null, null, null, null, null, ""));
        /* TODO(student): create Scout and schedule heartbeat/failure suspicion. */
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        transport.unregister(id);
        Scout scout = currentScout;
        if (scout != null) scout.kill();
        commanders.values().forEach(Commander::kill);
        commanders.clear();
        currentScout = null;
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

    protected final void removeCommander(BallotNumber ballot, long slot) {
        commanders.remove(new CommanderKey(ballot, slot));
    }

    protected void onEnvelope(MessageEnvelope envelope) {
        if (!running.get() || !envelope.destination().equals(id)) return;
        switch (envelope.message()) {
            case ProposeMessage proposal -> { if (membership.replicas().contains(envelope.source())) onPropose(proposal); else ignored(envelope, "PROPOSE source is not a Replica"); }
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
            case HeartbeatMessage heartbeat -> { if (membership.leaders().contains(envelope.source())) onHeartbeat(heartbeat, envelope.source()); else ignored(envelope, "HEARTBEAT source is not a Leader"); }
            default -> ignored(envelope, envelope.message().getClass().getSimpleName());
        }
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
