package paxoslocker.worker;

import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import paxoslocker.protocol.P1bMessage;
import paxoslocker.transport.Transport;

import java.util.Set;

/**
 * Ephemeral Phase-1 worker. The hook is observation/fault injection only.
 */
public class Scout {
    protected final NodeId leader;
    protected final BallotNumber ballot;
    protected final Set<NodeId> acceptors;
    protected final Transport transport;
    protected final WorkerHook hook;

    public Scout(NodeId leader, BallotNumber ballot, Set<NodeId> acceptors, Transport transport, WorkerHook hook) {
        this.leader = leader;
        this.ballot = ballot;
        this.acceptors = Set.copyOf(acceptors);
        this.transport = transport;
        this.hook = hook;
    }

    public void start() {
        throw todo("Scout.start: send P1A and collect unique quorum responses");
    }

    public void onP1b(P1bMessage response) {
        throw todo("Scout.onP1b: ADOPTED or PREEMPTED then exit");
    }

    public void kill() {
        throw todo("Scout.kill: stop this ephemeral worker without changing protocol outcome");
    }

    public ScoutStatus status() {
        throw todo("Scout.status");
    }

    private static UnsupportedOperationException todo(String text) {
        return new UnsupportedOperationException("TODO(student): " + text);
    }
}
