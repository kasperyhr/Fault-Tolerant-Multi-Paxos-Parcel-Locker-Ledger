package paxoslocker.testkit;

import paxoslocker.app.NodeLifecycle; import paxoslocker.model.*;
import java.time.Duration; import java.util.*; import java.util.concurrent.ConcurrentHashMap;

public final class FailureController {
    private final InMemoryTransport transport; private final EventRecorder events; private final WorkerRegistry workers; private final Map<NodeId,NodeLifecycle> nodes=new ConcurrentHashMap<>();
    public FailureController(InMemoryTransport transport,EventRecorder events,WorkerRegistry workers){this.transport=transport;this.events=events;this.workers=workers;}
    public void register(NodeId id,NodeLifecycle node){nodes.put(id,node);}
    public void crashAcceptor(NodeId id){crash(id,Role.ACCEPTOR);} public void restartAcceptor(NodeId id){restart(id,Role.ACCEPTOR);}
    public void crashReplica(NodeId id){crash(id,Role.REPLICA);} public void restartReplica(NodeId id){restart(id,Role.REPLICA);}
    public void crashLeader(NodeId id){crash(id,Role.LEADER);} public void restartLeader(NodeId id){restart(id,Role.LEADER);}
    private void crash(NodeId id,Role role){require(id).stop();events.record(id,role,EventType.NODE_CRASHED,null,null,null,null,"");}
    private void restart(NodeId id,Role role){require(id).restart();events.record(id,role,EventType.NODE_RESTARTED,null,null,null,null,"");}
    public boolean killScout(NodeId leader,BallotNumber ballot){boolean found=workers.killScout(leader,ballot);if(!found)throw new IllegalArgumentException("Scout not found: "+leader+"/"+ballot);events.record(leader,Role.SCOUT,EventType.WORKER_KILLED,ballot,null,null,null,"");return true;}
    public boolean killCommander(NodeId leader,BallotNumber ballot,long slot){boolean found=workers.killCommander(leader,ballot,slot);if(!found)throw new IllegalArgumentException("Commander not found: "+leader+"/"+ballot+"/"+slot);events.record(leader,Role.COMMANDER,EventType.WORKER_KILLED,ballot,slot,null,null,"");return true;}
    public void dropNext(MessagePredicate p){network().dropNext(p);} public void duplicateNext(MessagePredicate p){network().duplicateNext(p);} public void delayNext(MessagePredicate p,Duration d){network().delayNext(p,d);}
    public void reorderNext(MessagePredicate p){network().reorderNext(p);}
    public void partition(NodeId a,NodeId b){network().partition(a,b);} public void partition(Set<NodeId>a,Set<NodeId>b){network().partition(a,b);} public void heal(NodeId a,NodeId b){network().heal(a,b);} public void healAll(){network().healAll();}
    public void pauseNode(NodeId id){network().pause(id);events.record(id,Role.TRANSPORT,EventType.NODE_PAUSED,null,null,null,null,"");} public void resumeNode(NodeId id){network().resume(id);events.record(id,Role.TRANSPORT,EventType.NODE_RESUMED,null,null,null,null,"");}
    private NodeLifecycle require(NodeId id){NodeLifecycle n=nodes.get(id);if(n==null)throw new IllegalArgumentException("unknown node "+id);return n;}
    private InMemoryTransport network(){if(transport==null)throw new UnsupportedOperationException("network fault injection requires IN_MEMORY transport");return transport;}
}
