package paxoslocker.replica;

import paxoslocker.app.NodeLifecycle;
import paxoslocker.app.ClusterMembership;
import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import paxoslocker.persistence.PersistentStore;
import paxoslocker.protocol.*;
import paxoslocker.transport.*;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Student implementation point: ordering, re-proposal, catch-up, replay and deduplication.
 */
public class Replica implements NodeLifecycle {
    protected final NodeId id;
    protected final Transport transport;
    protected final PersistentStore store;
    protected final ParcelLockerStateMachine stateMachine;
    protected final ClusterMembership membership;
    protected final DiagnosticSink diagnostics;
    private final AtomicBoolean running = new AtomicBoolean();

    public Replica(NodeId id, Transport transport, PersistentStore store, Collection<String> lockerIds,
                   ClusterMembership membership, DiagnosticSink diagnostics) {
        this.id = id;
        this.transport = transport;
        this.store = store;
        this.stateMachine = new ParcelLockerStateMachine(lockerIds);
        this.membership = Objects.requireNonNull(membership);
        this.diagnostics = diagnostics == null ? DiagnosticSink.NOOP : diagnostics;
        if (!membership.replicas().contains(id)) throw new IllegalArgumentException("id is not a replica");
    }

    public void submit(Command command) {
        throw todo("Replica.submit: choose slot and send PROPOSE");
    }

    public void onDecision(DecisionMessage decision) {
        throw todo("Replica.onDecision: learn, re-propose loser, execute contiguous prefix, persist");
    }

    public void onDecisionSyncRequest(DecisionSyncRequestMessage request, NodeId peer) {
        throw todo("Replica.onDecisionSyncRequest: return a bounded range from the complete decision log");
    }

    public void onDecisionSyncResponse(DecisionSyncResponseMessage response, NodeId peer) {
        throw todo("Replica.onDecisionSyncResponse: validate, merge, replay and retry gaps");
    }

    public void onHeartbeat(HeartbeatMessage heartbeat, NodeId leader) {
        throw todo("Replica.onHeartbeat: update leader knowledge/failure detector state");
    }

    public ReplicaStatus status() {
        throw todo("Replica.status: immutable observation only");
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        /* TODO(student): restore durable state and initiate decision catch-up. */
        transport.register(id, this::onEnvelope);
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.REPLICA,
                ProtocolDiagnosticType.NODE_STARTED, null, null, null, null, null, ""));
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        transport.unregister(id);
        diagnostics.record(new ProtocolDiagnosticEvent(id, Role.REPLICA,
                ProtocolDiagnosticType.NODE_STOPPED, null, null, null, null, null, ""));
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    protected void onEnvelope(MessageEnvelope envelope) {
        if (!running.get() || !envelope.destination().equals(id)) return;
        switch (envelope.message()) {
            case DecisionMessage decision -> onDecision(decision);
            case DecisionSyncRequestMessage request -> onDecisionSyncRequest(request, envelope.source());
            case DecisionSyncResponseMessage response -> onDecisionSyncResponse(response, envelope.source());
            case HeartbeatMessage heartbeat -> onHeartbeat(heartbeat, envelope.source());
            default -> diagnostics.record(new ProtocolDiagnosticEvent(id, Role.REPLICA,
                    ProtocolDiagnosticType.MESSAGE_IGNORED, null, null, null, envelope.source(), null,
                    envelope.message().getClass().getSimpleName()));
        }
    }

    private static UnsupportedOperationException todo(String text) {
        return new UnsupportedOperationException("TODO(student): " + text);
    }
}
