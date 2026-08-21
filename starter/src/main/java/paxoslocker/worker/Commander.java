package paxoslocker.worker;

import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import paxoslocker.protocol.DecisionMessage;
import paxoslocker.protocol.P2aMessage;
import paxoslocker.protocol.P2bMessage;
import paxoslocker.protocol.PreemptedMessage;
import paxoslocker.transport.MessageEnvelope;
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
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final Object stateLock = new Object();
    private final CommanderState state = new CommanderState();

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
        if (isKilled()) return;
        P2aMessage message = new P2aMessage(pvalue);
        emit(WorkerEventType.P2A_BEFORE_SEND);
        if (isKilled()) return;
        for (NodeId acceptor: acceptors) {
            transport.send(MessageEnvelope.of(leader, acceptor, message));
        }
        emit(WorkerEventType.P2A_AFTER_SEND);
    }

    public void onP2b(P2bMessage response) {
        if (isKilled()) return;
        emit(WorkerEventType.P2B_RECEIVED);
        if (isKilled()) return;
        if (!response.requestedBallot().equals(pvalue.ballot())) return;
        if (response.slot() != pvalue.slot()) return;
        int cmp = response.acceptorBallot().compareTo(pvalue.ballot());
        if (cmp < 0) return;
        synchronized (stateLock) {
            if (isKilled()) return;
            if (cmp > 0) {
                transport.send(MessageEnvelope.of(leader, leader, new PreemptedMessage(response.acceptorBallot())));
                kill();
                return;
            }
            if (!state.addResponse(response.acceptor())) return;
            if (state.responses().size() >= quorum) {
                state.setChosen(true);
                emit(WorkerEventType.COMMANDER_QUORUM_REACHED);
                if (isKilled()) return;
                DecisionMessage message = new DecisionMessage(pvalue.slot(), pvalue().command());
                emit(WorkerEventType.DECISION_BEFORE_SEND);
                for (NodeId replica: replicas) {
                    if (isKilled()) return;
                    transport.send(MessageEnvelope.of(leader, replica, message));
                    emit(WorkerEventType.DECISION_AFTER_SEND);
                }
                if (isKilled()) return;
                kill();
            }
        }
    }

    public void kill() {
        markExited();
    }

    /** Shared terminal transition for kill and normal student-implemented completion. */
    protected final boolean markExited() {
        if (!terminal.compareAndSet(false, true)) return false;
        hook.onEvent(WorkerEventType.COMMANDER_EXITED, pvalue.ballot(), pvalue.slot());
        return true;
    }

    public final NodeId leaderId() { return leader; }
    public final PValue pvalue() { return pvalue; }
    public final boolean isKilled() { return terminal.get(); }
    protected final void emit(WorkerEventType event) { hook.onEvent(event, pvalue.ballot(), pvalue.slot()); }

    public CommanderStatus status() {
        synchronized (stateLock) {
            return new CommanderStatus(pvalue, state.responses(), state.chosen(), !isKilled());
        }
    }

    private static UnsupportedOperationException todo(String text) {
        return new UnsupportedOperationException("TODO(student): " + text);
    }
}
