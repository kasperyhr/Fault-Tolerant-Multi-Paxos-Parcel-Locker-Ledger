package paxoslocker.acceptor;

import paxoslocker.app.NodeLifecycle;
import paxoslocker.app.ClusterMembership;
import paxoslocker.diagnostics.*;
import paxoslocker.model.NodeId;
import paxoslocker.model.Role;
import paxoslocker.persistence.PersistentStore;
import paxoslocker.protocol.*;
import paxoslocker.transport.*;

import java.util.Objects;
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
    private final AtomicBoolean running = new AtomicBoolean();

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
        throw todo("Acceptor.onP1a: promise/adopt and persist ballot");
    }

    public P2bMessage onP2a(P2aMessage message) {
        throw todo("Acceptor.onP2a: validate ballot, accept, and persist PValue");
    }

    public AcceptorStatus status() {
        throw todo("Acceptor.status: return an immutable snapshot");
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        /* TODO(student): restore durable ballot and accepted PValues before processing messages. */
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
        if (envelope.message() instanceof P1aMessage p1a) response = onP1a(p1a);
        else if (envelope.message() instanceof P2aMessage p2a) response = onP2a(p2a);
        else { diagnostics.record(new ProtocolDiagnosticEvent(id, Role.ACCEPTOR,
                ProtocolDiagnosticType.MESSAGE_IGNORED, null, null, null, envelope.source(), null,
                envelope.message().getClass().getSimpleName())); return; }
        transport.send(MessageEnvelope.of(id, envelope.source(), response));
    }

    private static UnsupportedOperationException todo(String text) {
        return new UnsupportedOperationException("TODO(student): " + text);
    }
}
