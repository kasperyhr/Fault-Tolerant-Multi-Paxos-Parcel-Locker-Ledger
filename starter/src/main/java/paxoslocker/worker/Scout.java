package paxoslocker.worker;

import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import paxoslocker.protocol.*;
import paxoslocker.transport.MessageEnvelope;
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
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final Object stateLock = new Object();
    private final ScoutState state = new ScoutState();

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
        if (isKilled()) return;
        P1aMessage message = new P1aMessage(ballot);
        emit(WorkerEventType.P1A_BEFORE_SEND);
        if (isKilled()) return;
        for (NodeId acceptor : acceptors) {
            transport.send(MessageEnvelope.of(leader, acceptor, message));
        }
        emit(WorkerEventType.P1A_AFTER_SEND);
    }

    public void onP1b(P1bMessage response) {
        if (isKilled()) return;
        emit(WorkerEventType.P1B_RECEIVED);
        if (isKilled()) return;
        if (!response.requestedBallot().equals(ballot)) return;
        int cmp = response.acceptorBallot().compareTo(ballot);
        if (cmp < 0) return;
        synchronized (stateLock) {
            if (cmp > 0) {
                transport.send(MessageEnvelope.of(leader, leader, new PreemptedMessage(response.acceptorBallot())));
                kill();
                return;
            }
            if (!state.addResponse(response.acceptor(), response.accepted())) return;
            if (state.responses().size() >= quorum) {
                emit(WorkerEventType.SCOUT_QUORUM_REACHED);
                if (isKilled()) return;
                AdoptedMessage message = new AdoptedMessage(ballot, state.accepted());
                emit(WorkerEventType.ADOPTED_BEFORE_SEND);
                if (isKilled()) return;
                transport.send(MessageEnvelope.of(leader, leader, message));
                kill();
            }
        }
    }

    public void kill() {
        markExited();
    }

    /**
     * Shared terminal transition for kill and normal student-implemented completion.
     */
    protected final boolean markExited() {
        if (!terminal.compareAndSet(false, true)) return false;
        hook.onEvent(WorkerEventType.SCOUT_EXITED, ballot, null);
        return true;
    }

    public final NodeId leaderId() {
        return leader;
    }

    public final BallotNumber ballot() {
        return ballot;
    }

    public final boolean isKilled() {
        return terminal.get();
    }

    protected final void emit(WorkerEventType event) {
        hook.onEvent(event, ballot, null);
    }

    public ScoutStatus status() {
        synchronized (stateLock) {
            return new ScoutStatus(ballot, state.responses(), !isKilled());
        }
    }

    private static UnsupportedOperationException todo(String text) {
        return new UnsupportedOperationException("TODO(student): " + text);
    }
}
