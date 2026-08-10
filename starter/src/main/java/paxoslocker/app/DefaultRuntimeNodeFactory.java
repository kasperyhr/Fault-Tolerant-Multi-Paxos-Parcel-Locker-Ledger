package paxoslocker.app;
import paxoslocker.acceptor.Acceptor; import paxoslocker.diagnostics.*; import paxoslocker.leader.Leader; import paxoslocker.model.NodeId; import paxoslocker.persistence.PersistentStore; import paxoslocker.replica.Replica; import paxoslocker.transport.Transport; import paxoslocker.worker.*; import java.util.Collection;
public final class DefaultRuntimeNodeFactory implements RuntimeNodeFactory {
 public Acceptor acceptor(NodeId id,Transport t,PersistentStore s,ClusterMembership m,DiagnosticSink d){return new Acceptor(id,t,s,m,d);}
 public Replica replica(NodeId id,Transport t,PersistentStore s,Collection<String> l,ClusterMembership m,DiagnosticSink d){return new Replica(id,t,s,l,m,d);}
 public Leader leader(NodeId id,Transport t,ClusterMembership m,WorkerFactory w,DiagnosticSink d){return new Leader(id,t,m,w,d,WorkerHook.NOOP);}
}
