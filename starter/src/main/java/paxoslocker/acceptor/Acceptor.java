package paxoslocker.acceptor;

import paxoslocker.app.NodeLifecycle;
import paxoslocker.diagnostics.AcceptorStatus;
import paxoslocker.model.NodeId;
import paxoslocker.persistence.PersistentStore;
import paxoslocker.protocol.*;

/**
 * Student implementation point: Phase 1/2 state and durable A1-A3 behavior.
 */
public class Acceptor implements NodeLifecycle {
    protected final NodeId id;
    protected final PersistentStore store;
    private volatile boolean running;

    public Acceptor(NodeId id, PersistentStore store) {
        this.id = id;
        this.store = store;
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
        running = true; /* TODO restore durable state */
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
