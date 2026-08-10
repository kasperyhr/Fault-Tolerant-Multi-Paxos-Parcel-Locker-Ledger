package paxoslocker.leader;

import paxoslocker.app.NodeLifecycle;
import paxoslocker.diagnostics.LeaderStatus;
import paxoslocker.model.*;
import paxoslocker.protocol.*;
import paxoslocker.transport.Transport;

import java.util.*;

/**
 * Student implementation point: Phase orchestration, pmax, preemption and failover.
 */
public class Leader implements NodeLifecycle {
    protected final NodeId id;
    protected final Transport transport;
    private volatile boolean running;

    public Leader(NodeId id, Transport transport) {
        this.id = id;
        this.transport = transport;
    }

    public void onPropose(ProposeMessage proposal) {
        throw todo("Leader.onPropose: retain one value per slot and spawn Commander when active");
    }

    public void onAdopted(AdoptedMessage adopted) {
        throw todo("Leader.onAdopted: apply pmax and activate ballot");
    }

    public void onPreempted(PreemptedMessage preempted) {
        throw todo("Leader.onPreempted: deactivate, advance ballot, back off/retry");
    }

    public static Map<Long, Command> pmax(Collection<PValue> accepted) {
        throw todo("Leader.pmax");
    }

    public LeaderStatus status() {
        throw todo("Leader.status: immutable observation only");
    }

    @Override
    public void start() {
        running = true; /* TODO create Scout and schedule heartbeat/failure suspicion */
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
