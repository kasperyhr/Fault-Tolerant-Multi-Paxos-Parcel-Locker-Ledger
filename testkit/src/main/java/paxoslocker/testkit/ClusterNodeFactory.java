package paxoslocker.testkit;
import paxoslocker.acceptor.Acceptor; import paxoslocker.app.ClusterMembership; import paxoslocker.diagnostics.*; import paxoslocker.leader.Leader; import paxoslocker.model.NodeId; import paxoslocker.persistence.PersistentStore; import paxoslocker.replica.Replica; import paxoslocker.transport.Transport; import paxoslocker.worker.WorkerFactory; import java.util.Collection;
public interface ClusterNodeFactory {
 Acceptor acceptor(NodeId id,Transport transport,PersistentStore store,ClusterMembership membership,DiagnosticSink sink);
 Replica replica(NodeId id,Transport transport,PersistentStore store,Collection<String> lockers,ClusterMembership membership,DiagnosticSink sink);
 Leader leader(NodeId id,Transport transport,ClusterMembership membership,WorkerFactory workers,DiagnosticSink sink);
}
