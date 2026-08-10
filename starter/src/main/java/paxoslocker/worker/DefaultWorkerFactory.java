package paxoslocker.worker;

import paxoslocker.diagnostics.WorkerHook;
import paxoslocker.model.*;
import paxoslocker.transport.Transport;
import java.util.Set;

/** Construction only; Scout and Commander protocol algorithms remain student work. */
public final class DefaultWorkerFactory implements WorkerFactory {
    @Override public Scout createScout(NodeId leader, BallotNumber ballot, Set<NodeId> acceptors,
            int quorum, Transport transport, WorkerHook hook) {
        return new Scout(leader, ballot, acceptors, quorum, transport, hook);
    }
    @Override public Commander createCommander(NodeId leader, PValue pvalue, Set<NodeId> acceptors,
            Set<NodeId> replicas, int quorum, Transport transport, WorkerHook hook) {
        return new Commander(leader, pvalue, acceptors, replicas, quorum, transport, hook);
    }
}
