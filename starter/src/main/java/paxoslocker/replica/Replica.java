package paxoslocker.replica;

import paxoslocker.app.NodeLifecycle;
import paxoslocker.diagnostics.ReplicaStatus;
import paxoslocker.model.*;
import paxoslocker.persistence.PersistentStore;
import paxoslocker.protocol.*;
import paxoslocker.transport.Transport;

import java.util.Collection;

/**
 * Student implementation point: ordering, re-proposal, catch-up, replay and deduplication.
 */
public class Replica implements NodeLifecycle {
    protected final NodeId id;
    protected final Transport transport;
    protected final PersistentStore store;
    protected final ParcelLockerStateMachine stateMachine;
    private volatile boolean running;

    public Replica(NodeId id, Transport transport, PersistentStore store, Collection<String> lockerIds) {
        this.id = id;
        this.transport = transport;
        this.store = store;
        this.stateMachine = new ParcelLockerStateMachine(lockerIds);
    }

    public void submit(Command command) {
        throw todo("Replica.submit: choose slot and send PROPOSE");
    }

    public void onDecision(DecisionMessage decision) {
        throw todo("Replica.onDecision: learn, re-propose loser, execute contiguous prefix, persist");
    }

    public ReplicaStatus status() {
        throw todo("Replica.status: immutable observation only");
    }

    @Override
    public void start() {
        running = true; /* TODO recover and catch up */
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private static UnsupportedOperationException todo(String text) {
        return new UnsupportedOperationException("TODO(student): " + text);
    }
}
