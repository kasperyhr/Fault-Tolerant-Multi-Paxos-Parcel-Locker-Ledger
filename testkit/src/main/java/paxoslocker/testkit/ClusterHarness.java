package paxoslocker.testkit;

import paxoslocker.acceptor.Acceptor; import paxoslocker.app.NodeLifecycle; import paxoslocker.diagnostics.*;
import paxoslocker.leader.Leader; import paxoslocker.model.*; import paxoslocker.persistence.FileStore;
import paxoslocker.replica.Replica; import java.io.IOException; import java.nio.file.*; import java.time.Duration; import java.util.*;

/** Lifecycle harness only. All consensus behavior comes from the supplied student node factory. */
public final class ClusterHarness implements AutoCloseable {
    private final ClusterConfig config; private final Path dataDirectory; private final EventRecorder events=new EventRecorder();
    private final InMemoryTransport transport=new InMemoryTransport(events); private final FailureController failures=new FailureController(transport,events);
    private final Map<NodeId,Acceptor> acceptors=new LinkedHashMap<>(); private final Map<NodeId,Replica> replicas=new LinkedHashMap<>(); private final Map<NodeId,Leader> leaders=new LinkedHashMap<>();
    private ClusterHarness(ClusterConfig config,Path dataDirectory){this.config=config;this.dataDirectory=dataDirectory;}
    public static ClusterHarness start(ClusterConfig config,ClusterNodeFactory factory){
        try { ClusterHarness h=new ClusterHarness(config,Files.createTempDirectory("paxos-locker-")); h.create(factory); return h; }
        catch(IOException e){throw new RuntimeException(e);}
    }
    private void create(ClusterNodeFactory factory){
        for(int i=0;i<config.acceptors();i++){NodeId id=new NodeId("acceptor-"+i);Acceptor node=factory.acceptor(id,new FileStore(dataDirectory.resolve(id.value())));acceptors.put(id,node);register(id,node);}
        for(int i=0;i<config.replicas();i++){NodeId id=new NodeId("replica-"+i);Replica node=factory.replica(id,transport,new FileStore(dataDirectory.resolve(id.value())),List.of("locker-1","locker-2","locker-3"));replicas.put(id,node);register(id,node);}
        for(int i=0;i<config.leaders();i++){NodeId id=new NodeId("leader-"+i);Leader node=factory.leader(id,transport);leaders.put(id,node);register(id,node);}
        allNodes().forEach(NodeLifecycle::start);
    }
    private void register(NodeId id,NodeLifecycle node){failures.register(id,node);}
    private List<NodeLifecycle> allNodes(){List<NodeLifecycle> out=new ArrayList<>();out.addAll(acceptors.values());out.addAll(replicas.values());out.addAll(leaders.values());return out;}
    public void submit(Command command){Replica replica=replicas.values().stream().filter(NodeLifecycle::isRunning).findFirst().orElseThrow();replica.submit(command);}
    public NodeId awaitLeader(Duration timeout){
        final NodeId[] found={null};
        Await.until(()->leaders.entrySet().stream().filter(e->e.getValue().isRunning()).filter(e->{try{return e.getValue().status().active();}catch(UnsupportedOperationException ignored){return false;}}).findFirst().map(e->{found[0]=e.getKey();return true;}).orElse(false),timeout,"active leader");
        return found[0];
    }
    public Command awaitDecision(long slot,Duration timeout){
        final Command[] found={null};
        Await.until(()->replicas.values().stream().filter(NodeLifecycle::isRunning).map(r->{try{return r.status().decisions().get(slot);}catch(UnsupportedOperationException ignored){return null;}}).filter(Objects::nonNull).findFirst().map(c->{found[0]=c;return true;}).orElse(false),timeout,"decision for slot "+slot);
        return found[0];
    }
    public void awaitExecutedThrough(long slot,Duration timeout){Await.until(()->replicas.values().stream().filter(NodeLifecycle::isRunning).allMatch(r->{try{return r.status().lastExecutedSlot()>=slot;}catch(UnsupportedOperationException ignored){return false;}}),timeout,"all live replicas executed through "+slot);}
    public void awaitReplicaConvergence(Duration timeout){Await.until(()->{try{List<ReplicaStatus>s=replicas.values().stream().filter(NodeLifecycle::isRunning).map(Replica::status).toList();if(s.isEmpty())return false;ReplicaStatus first=s.getFirst();return s.stream().allMatch(x->x.decisions().equals(first.decisions())&&x.applicationState().equals(first.applicationState()));}catch(UnsupportedOperationException e){return false;}},timeout,"replica convergence");}
    public ReplicaStatus inspectReplica(NodeId id){return replicas.get(id).status();} public LeaderStatus inspectLeader(NodeId id){return leaders.get(id).status();} public AcceptorStatus inspectAcceptor(NodeId id){return acceptors.get(id).status();}
    public Set<NodeId> replicaIds(){return Set.copyOf(replicas.keySet());} public Set<NodeId> leaderIds(){return Set.copyOf(leaders.keySet());} public Set<NodeId> acceptorIds(){return Set.copyOf(acceptors.keySet());}
    public void crashAcceptor(NodeId id){failures.crashAcceptor(id);} public void restartAcceptor(NodeId id){failures.restartAcceptor(id);} public void crashReplica(NodeId id){failures.crashReplica(id);} public void restartReplica(NodeId id){failures.restartReplica(id);} public void crashLeader(NodeId id){failures.crashLeader(id);} public void restartLeader(NodeId id){failures.restartLeader(id);} public void partition(NodeId a,NodeId b){failures.partition(a,b);} public void heal(NodeId a,NodeId b){failures.heal(a,b);} public void healAll(){failures.healAll();}
    public FailureController failures(){return failures;} public EventRecorder events(){return events;} public Path dataDirectory(){return dataDirectory;} public ClusterConfig config(){return config;}
    @Override public void close(){List<NodeLifecycle> nodes=allNodes();Collections.reverse(nodes);nodes.forEach(n->{try{n.stop();}catch(RuntimeException ignored){}});transport.close();deleteTree(dataDirectory);}
    public void shutdown(){close();}
    private static void deleteTree(Path root){try(var paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}}
}
