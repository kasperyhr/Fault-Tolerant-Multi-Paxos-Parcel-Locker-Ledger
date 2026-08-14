package paxoslocker.acceptor;

import paxoslocker.app.NodeLifecycle;
import paxoslocker.app.ClusterMembership;
import paxoslocker.diagnostics.*;
import paxoslocker.model.BallotNumber;
import paxoslocker.model.NodeId;
import paxoslocker.model.PValue;
import paxoslocker.model.Role;
import paxoslocker.persistence.PersistentStore;
import paxoslocker.protocol.*;
import paxoslocker.transport.*;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Student implementation point: Phase 1/2 state and durable A1-A3 behavior.
 */
public class Acceptor implements NodeLifecycle {
    protected final NodeId id;
    protected final Transport transport;
    protected final PersistentStore store;
    protected final ClusterMembership membership;
    protected final DiagnosticSink diagnostics;
    private static final String STORE_KEY = "state";
    private final AtomicBoolean running = new AtomicBoolean();
    private final Object stateLock = new Object();
    private AcceptorState state = new AcceptorState();

    public Acceptor(NodeId id, Transport transport, PersistentStore store,
                    ClusterMembership membership, DiagnosticSink diagnostics) {
        this.id = id;
        this.transport = Objects.requireNonNull(transport);
        this.store = Objects.requireNonNull(store);
        this.membership = Objects.requireNonNull(membership);
        this.diagnostics = diagnostics == null ? DiagnosticSink.NOOP : diagnostics;
        if (!membership.acceptors().contains(id)) throw new IllegalArgumentException("id is not an acceptor");
    }

    public P1bMessage onP1a(P1aMessage message) {
        synchronized (stateLock) {
            if (state.ballot().compareTo(message.ballot()) < 0) {
                state.setBallot(message.ballot());
                storeState();
                acceptorBallotEvent(message.ballot());
            }
            return new P1bMessage(id, message.ballot(), state.ballot(), Set.copyOf(state.accepted()));
        }
    }

    public P2bMessage onP2a(P2aMessage message) {
        PValue pValue = message.pvalue();
        synchronized (stateLock) {
            int cmp = state.ballot().compareTo(message.pvalue().ballot());
            if (cmp < 0) {
                state.setBallot(message.pvalue().ballot());
                state.addAccepted(message.pvalue());
                storeState();
                acceptorBallotEvent(pValue.ballot());
                pValueAcceptedEvent(pValue);
            } else if (cmp == 0) {
                Optional<PValue> existing = state.findAccepted(message.pvalue().ballot(), message.pvalue().slot());
                if (existing.isEmpty()) {
                    state.addAccepted(message.pvalue());
                    storeState();
                    pValueAcceptedEvent(pValue);
                } else if (!existing.get().equals(message.pvalue())) {
                    throw new IllegalStateException("Contains same ballot, same slot, different command.");
                }
            }
            return new P2bMessage(id, message.pvalue().ballot(), state.ballot(), message.pvalue().slot());
        }
    }

    public AcceptorStatus status() {
        synchronized (stateLock) {
            return state.toAcceptorStatus();
        }
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        restoreState();
        transport.register(id, this::onEnvelope);
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.ACCEPTOR,
                ProtocolDiagnosticType.NODE_STARTED, null, null, null, null, null, ""));
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        transport.unregister(id);
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.ACCEPTOR,
                ProtocolDiagnosticType.NODE_STOPPED, null, null, null, null, null, ""));
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    protected void onEnvelope(MessageEnvelope envelope) {
        if (!running.get() || !envelope.destination().equals(id)) return;
        ProtocolMessage response;
        if (!membership.leaders().contains(envelope.source())) {
            ignored(envelope, "Phase request source is not a Leader");
            return;
        }
        if (envelope.message() instanceof P1aMessage p1a) response = onP1a(p1a);
        else if (envelope.message() instanceof P2aMessage p2a) response = onP2a(p2a);
        else {
            diagnostics.record(new ProtocolDiagnosticEvent(id, Role.ACCEPTOR,
                    ProtocolDiagnosticType.MESSAGE_IGNORED, null, null, null, envelope.source(), null,
                    envelope.message().getClass().getSimpleName()));
            return;
        }
        transport.send(MessageEnvelope.of(id, envelope.source(), response));
    }

    private void restoreState() {
        synchronized (stateLock) {
            state = store.load(STORE_KEY, AcceptorState.class).orElseGet(() -> state);
        }
    }

    private void storeState() {
        store.save(STORE_KEY, state);
    }

    private void acceptorBallotEvent(BallotNumber ballot) {
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.ACCEPTOR,
                ProtocolDiagnosticType.ACCEPTOR_BALLOT, ballot, null, null, null, null, ""));
    }

    private void pValueAcceptedEvent(PValue pValue) {
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.ACCEPTOR,
                ProtocolDiagnosticType.PVALUE_ACCEPTED, pValue.ballot(), pValue.slot(), pValue.command().requestId(), null, pValue.command(), ""));
    }

    private void ignored(MessageEnvelope envelope, String detail) {
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.ACCEPTOR,
                ProtocolDiagnosticType.MESSAGE_IGNORED, null, null, null, envelope.source(), null, detail));
    }

    private static UnsupportedOperationException todo(String text) {
        return new UnsupportedOperationException("TODO(student): " + text);
    }
}
