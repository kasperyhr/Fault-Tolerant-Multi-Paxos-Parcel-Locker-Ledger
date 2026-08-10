package paxoslocker.worker;

import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import paxoslocker.protocol.P2bMessage;
import paxoslocker.transport.Transport;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ephemeral Phase-2 worker. Chosen depends only on an acceptor quorum.
 */
public class Commander {
    protected final NodeId leader;
    protected final PValue pvalue;
    protected final Set<NodeId> acceptors, replicas;
    protected final int quorum;
    protected final Transport transport;
    protected final WorkerHook hook;
    private final AtomicBoolean killed = new AtomicBoolean();

    public Commander(NodeId leader, PValue pvalue, Set<NodeId> acceptors, Set<NodeId> replicas,
                     int quorum, Transport transport, WorkerHook hook) {
        this.leader = leader;
        this.pvalue = pvalue;
        this.acceptors = Set.copyOf(acceptors);
        this.replicas = Set.copyOf(replicas);
        if (quorum <= acceptors.size() / 2 || quorum > acceptors.size())
            throw new IllegalArgumentException("invalid Commander quorum");
        this.quorum = quorum;
        this.transport = transport;
        this.hook = hook == null ? WorkerHook.NOOP : hook;
    }

    public void start() {
        throw todo("Commander.start: send P2A and collect unique quorum responses");
    }

    public void onP2b(P2bMessage response) {
        throw todo("Commander.onP2b: PREEMPTED or broadcast DECISION after quorum");
    }

    public void kill() {
        if (killed.compareAndSet(false, true)) hook.onEvent(WorkerEventType.COMMANDER_EXITED, pvalue.ballot(), pvalue.slot());
    }

    public final NodeId leaderId() { return leader; }
    public final PValue pvalue() { return pvalue; }
    public final boolean isKilled() { return killed.get(); }
    protected final void emit(WorkerEventType event) { hook.onEvent(event, pvalue.ballot(), pvalue.slot()); }

    public CommanderStatus status() {
        throw todo("Commander.status");
    }

    private static UnsupportedOperationException todo(String text) {
        return new UnsupportedOperationException("TODO(student): " + text);
    }
}
