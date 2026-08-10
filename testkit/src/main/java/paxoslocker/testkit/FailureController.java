package paxoslocker.testkit;

import paxoslocker.app.NodeLifecycle; import paxoslocker.model.*;
import java.time.Duration; import java.util.*; import java.util.concurrent.ConcurrentHashMap;

public final class FailureController {
    private final InMemoryTransport transport; private final EventRecorder events; private final Map<NodeId,NodeLifecycle> nodes=new ConcurrentHashMap<>();
    private final Set<String> killedScouts=ConcurrentHashMap.newKeySet(), killedCommanders=ConcurrentHashMap.newKeySet();
    public FailureController(InMemoryTransport transport,EventRecorder events){this.transport=transport;this.events=events;}
    public void register(NodeId id,NodeLifecycle node){nodes.put(id,node);}
    public void crashAcceptor(NodeId id){crash(id,Role.ACCEPTOR);} public void restartAcceptor(NodeId id){restart(id,Role.ACCEPTOR);}
    public void crashReplica(NodeId id){crash(id,Role.REPLICA);} public void restartReplica(NodeId id){restart(id,Role.REPLICA);}
    public void crashLeader(NodeId id){crash(id,Role.LEADER);} public void restartLeader(NodeId id){restart(id,Role.LEADER);}
    private void crash(NodeId id,Role role){require(id).stop();events.record(id,role,EventType.NODE_CRASHED,null,null,null,null,"");}
    private void restart(NodeId id,Role role){require(id).restart();events.record(id,role,EventType.NODE_RESTARTED,null,null,null,null,"");}
    public void killScout(NodeId leader,BallotNumber ballot){killedScouts.add(leader+"@"+ballot);events.record(leader,Role.SCOUT,EventType.WORKER_KILLED,ballot,null,null,null,"");}
    public void killCommander(NodeId leader,BallotNumber ballot,long slot){killedCommanders.add(leader+"@"+ballot+"#"+slot);events.record(leader,Role.COMMANDER,EventType.WORKER_KILLED,ballot,slot,null,null,"");}
    public boolean isScoutKilled(NodeId l,BallotNumber b){return killedScouts.contains(l+"@"+b);} public boolean isCommanderKilled(NodeId l,BallotNumber b,long s){return killedCommanders.contains(l+"@"+b+"#"+s);}
    public void dropNext(MessagePredicate p){transport.dropNext(p);} public void duplicateNext(MessagePredicate p){transport.duplicateNext(p);} public void delayNext(MessagePredicate p,Duration d){transport.delayNext(p,d);}
    public void partition(NodeId a,NodeId b){transport.partition(a,b);} public void partition(Set<NodeId>a,Set<NodeId>b){transport.partition(a,b);} public void heal(NodeId a,NodeId b){transport.heal(a,b);} public void healAll(){transport.healAll();}
    public void pauseNode(NodeId id){transport.pause(id);events.record(id,Role.TRANSPORT,EventType.NODE_PAUSED,null,null,null,null,"");} public void resumeNode(NodeId id){transport.resume(id);events.record(id,Role.TRANSPORT,EventType.NODE_RESUMED,null,null,null,null,"");}
    private NodeLifecycle require(NodeId id){NodeLifecycle n=nodes.get(id);if(n==null)throw new IllegalArgumentException("unknown node "+id);return n;}
}
