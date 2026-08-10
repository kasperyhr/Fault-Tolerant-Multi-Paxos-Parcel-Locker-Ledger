package paxoslocker.worker;

import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import paxoslocker.protocol.P2bMessage;
import paxoslocker.transport.Transport;

import java.util.Set;

/**
 * Ephemeral Phase-2 worker. Chosen depends only on an acceptor quorum.
 */
public class Commander {
    protected final NodeId leader;
    protected final PValue pvalue;
    protected final Set<NodeId> acceptors, replicas;
    protected final Transport transport;
    protected final WorkerHook hook;

    public Commander(NodeId leader, PValue pvalue, Set<NodeId> acceptors, Set<NodeId> replicas, Transport transport, WorkerHook hook) {
        this.leader = leader;
        this.pvalue = pvalue;
        this.acceptors = Set.copyOf(acceptors);
        this.replicas = Set.copyOf(replicas);
        this.transport = transport;
        this.hook = hook;
    }

    public void start() {
        throw todo("Commander.start: send P2A and collect unique quorum responses");
    }

    public void onP2b(P2bMessage response) {
        throw todo("Commander.onP2b: PREEMPTED or broadcast DECISION after quorum");
    }

    public void kill() {
        throw todo("Commander.kill: stop without undoing accepted/chosen values");
    }

    public CommanderStatus status() {
        throw todo("Commander.status");
    }

    private static UnsupportedOperationException todo(String text) {
        return new UnsupportedOperationException("TODO(student): " + text);
    }
}
