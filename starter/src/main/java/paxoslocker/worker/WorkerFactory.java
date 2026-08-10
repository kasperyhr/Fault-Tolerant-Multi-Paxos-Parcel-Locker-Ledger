package paxoslocker.worker;

import paxoslocker.diagnostics.WorkerHook;
import paxoslocker.model.*;
import paxoslocker.transport.Transport;
import java.util.Set;

public interface WorkerFactory {
    Scout createScout(NodeId leader, BallotNumber ballot, Set<NodeId> acceptors,
                      int quorum, Transport transport, WorkerHook hook);
    Commander createCommander(NodeId leader, PValue pvalue, Set<NodeId> acceptors,
                              Set<NodeId> replicas, int quorum, Transport transport, WorkerHook hook);
}
