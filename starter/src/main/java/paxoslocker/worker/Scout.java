package paxoslocker.worker;

import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import paxoslocker.protocol.P1bMessage;
import paxoslocker.transport.Transport;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ephemeral Phase-1 worker. The hook is observation/fault injection only.
 */
public class Scout {
    protected final NodeId leader;
    protected final BallotNumber ballot;
    protected final Set<NodeId> acceptors;
    protected final int quorum;
    protected final Transport transport;
    protected final WorkerHook hook;
    private final AtomicBoolean killed = new AtomicBoolean();

    public Scout(NodeId leader, BallotNumber ballot, Set<NodeId> acceptors, int quorum,
                 Transport transport, WorkerHook hook) {
        this.leader = leader;
        this.ballot = ballot;
        this.acceptors = Set.copyOf(acceptors);
        if (quorum <= acceptors.size() / 2 || quorum > acceptors.size())
            throw new IllegalArgumentException("invalid Scout quorum");
        this.quorum = quorum;
        this.transport = transport;
        this.hook = hook == null ? WorkerHook.NOOP : hook;
    }

    public void start() {
        throw todo("Scout.start: send P1A and collect unique quorum responses");
    }

    public void onP1b(P1bMessage response) {
        throw todo("Scout.onP1b: ADOPTED or PREEMPTED then exit");
    }

    public void kill() {
        if (killed.compareAndSet(false, true)) hook.onEvent(WorkerEventType.SCOUT_EXITED, ballot, null);
    }

    public final NodeId leaderId() { return leader; }
    public final BallotNumber ballot() { return ballot; }
    public final boolean isKilled() { return killed.get(); }
    protected final void emit(WorkerEventType event) { hook.onEvent(event, ballot, null); }

    public ScoutStatus status() {
        throw todo("Scout.status");
    }

    private static UnsupportedOperationException todo(String text) {
        return new UnsupportedOperationException("TODO(student): " + text);
    }
}
