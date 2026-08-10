package paxoslocker.testkit;
import paxoslocker.acceptor.Acceptor; import paxoslocker.leader.Leader; import paxoslocker.model.NodeId; import paxoslocker.persistence.PersistentStore; import paxoslocker.replica.Replica; import paxoslocker.transport.Transport; import java.util.Collection;
public interface ClusterNodeFactory { Acceptor acceptor(NodeId id,PersistentStore store); Replica replica(NodeId id,Transport transport,PersistentStore store,Collection<String> lockers); Leader leader(NodeId id,Transport transport); }
