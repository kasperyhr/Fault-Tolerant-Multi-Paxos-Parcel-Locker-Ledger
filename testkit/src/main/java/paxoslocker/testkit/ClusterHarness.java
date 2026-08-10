package paxoslocker.testkit;

import paxoslocker.acceptor.Acceptor;
import paxoslocker.app.*;
import paxoslocker.diagnostics.*;
import paxoslocker.leader.Leader;
import paxoslocker.model.*;
import paxoslocker.persistence.FileStore;
import paxoslocker.replica.Replica;
import paxoslocker.transport.*;
import paxoslocker.worker.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Lifecycle and fault harness. Consensus behavior always comes from student nodes. */
public final class ClusterHarness implements AutoCloseable {
    private final ClusterConfig config; private final Path dataDirectory;
    private final EventRecorder events=new EventRecorder(); private final SafetyInvariantChecker safety=new SafetyInvariantChecker();
    private final RecordingDiagnosticSink diagnostics=new RecordingDiagnosticSink(events,safety);
    private final WorkerRegistry workerRegistry=new WorkerRegistry(); private final WorkerEventProbe workerEvents=new WorkerEventProbe();
    private final Transport transport; private final InMemoryTransport faultTransport; private final FailureController failures;
    private final ClusterMembership membership; private final InstrumentedWorkerFactory workers;
    private final Map<NodeId,Acceptor> acceptors=new LinkedHashMap<>(); private final Map<NodeId,Replica> replicas=new LinkedHashMap<>(); private final Map<NodeId,Leader> leaders=new LinkedHashMap<>();

    private ClusterHarness(ClusterConfig config,Path directory){
        this.config=config; this.dataDirectory=directory;
        this.faultTransport=config.transportMode()==TransportMode.IN_MEMORY?new InMemoryTransport(events):null;
        this.transport=faultTransport!=null?faultTransport:new LocalTcpTransport();
        this.failures=new FailureController(faultTransport,events,workerRegistry);
        Set<NodeId>a=ids("acceptor",config.acceptors()),r=ids("replica",config.replicas()),l=ids("leader",config.leaders());
        this.membership=new ClusterMembership(a,r,l,config.quorum());
        this.workers=new InstrumentedWorkerFactory(new DefaultWorkerFactory(),workerRegistry,workerEvents,events);
    }
    public static ClusterHarness start(ClusterConfig config){return start(config,new DefaultClusterNodeFactory());}
    public static ClusterHarness start(ClusterConfig config,ClusterNodeFactory factory){
        try{ClusterHarness h=new ClusterHarness(config,Files.createTempDirectory("paxos-locker-"));h.create(factory);return h;}
        catch(IOException e){throw new RuntimeException(e);}
    }
    private static Set<NodeId> ids(String role,int count){Set<NodeId> out=new LinkedHashSet<>();for(int i=0;i<count;i++)out.add(new NodeId(role+"-"+i));return Set.copyOf(out);}
    private void create(ClusterNodeFactory factory){
        membership.acceptors().forEach(id->{Acceptor n=factory.acceptor(id,transport,new FileStore(dataDirectory.resolve(id.value())),membership,diagnostics);acceptors.put(id,n);register(id,n);});
        membership.replicas().forEach(id->{Replica n=factory.replica(id,transport,new FileStore(dataDirectory.resolve(id.value())),List.of("locker-1","locker-2","locker-3"),membership,diagnostics);replicas.put(id,n);register(id,n);});
        membership.leaders().forEach(id->{Leader n=factory.leader(id,transport,membership,workers,diagnostics);leaders.put(id,n);register(id,n);});
        allNodes().forEach(NodeLifecycle::start);
    }
    private void register(NodeId id,NodeLifecycle node){failures.register(id,node);}
    private List<NodeLifecycle> allNodes(){List<NodeLifecycle> out=new ArrayList<>();out.addAll(acceptors.values());out.addAll(replicas.values());out.addAll(leaders.values());return out;}
    public void submit(Command command){replicas.values().stream().filter(NodeLifecycle::isRunning).findFirst().orElseThrow().submit(command);}
    public void submitTo(NodeId replica,Command command){Replica node=require(replicas,replica);if(!node.isRunning())throw new IllegalStateException("replica is stopped: "+replica);node.submit(command);}
    public void sendForTest(MessageEnvelope envelope){transport.send(envelope);}
    public NodeId awaitLeader(Duration timeout){
        leaders.values().stream().filter(NodeLifecycle::isRunning).findFirst().ifPresent(leader->leader.status());
        final NodeId[] result={null};Await.until(()->leaders.entrySet().stream().filter(e->e.getValue().isRunning()).anyMatch(e->{if(e.getValue().status().active()){result[0]=e.getKey();return true;}return false;}),timeout,"active leader");return result[0];}
    public Command awaitDecision(long slot,Duration timeout){
        replicas.values().stream().filter(NodeLifecycle::isRunning).findFirst().ifPresent(replica->replica.status());
        final Command[] result={null};Await.until(()->replicas.values().stream().filter(NodeLifecycle::isRunning).anyMatch(r->{Command c=r.status().decisions().get(slot);if(c!=null){result[0]=c;return true;}return false;}),timeout,"decision for slot "+slot);return result[0];}
    public void awaitExecutedThrough(long slot,Duration timeout){replicas.values().stream().filter(NodeLifecycle::isRunning).findFirst().ifPresent(replica->replica.status());Await.until(()->replicas.values().stream().filter(NodeLifecycle::isRunning).allMatch(r->r.status().lastExecutedSlot()>=slot),timeout,"all live replicas executed through "+slot);}
    public void awaitReplicaConvergence(Duration timeout){Await.until(()->{List<ReplicaStatus>s=replicas.values().stream().filter(NodeLifecycle::isRunning).map(r->{try{return r.status();}catch(UnsupportedOperationException e){return null;}}).filter(Objects::nonNull).toList();if(s.isEmpty())return false;ReplicaStatus f=s.getFirst();return s.stream().allMatch(x->x.decisions().equals(f.decisions())&&x.applicationState().equals(f.applicationState()));},timeout,"replica convergence");}
    public void awaitWorkerEvent(NodeId leader,BallotNumber ballot,Long slot,WorkerEventType type,Duration timeout){workerEvents.await(leader,ballot,slot,type,timeout);}
    public ReplicaStatus inspectReplica(NodeId id){return require(replicas,id).status();} public LeaderStatus inspectLeader(NodeId id){return require(leaders,id).status();} public AcceptorStatus inspectAcceptor(NodeId id){return require(acceptors,id).status();}
    public ScoutStatus inspectScout(NodeId l,BallotNumber b){return workerRegistry.scout(l,b).orElseThrow().status();} public CommanderStatus inspectCommander(NodeId l,BallotNumber b,long s){return workerRegistry.commander(l,b,s).orElseThrow().status();}
    private static <T>T require(Map<NodeId,T> map,NodeId id){T value=map.get(id);if(value==null)throw new IllegalArgumentException("unknown node "+id);return value;}
    public Set<NodeId> replicaIds(){return membership.replicas();} public Set<NodeId> leaderIds(){return membership.leaders();} public Set<NodeId> acceptorIds(){return membership.acceptors();}
    public void crashAcceptor(NodeId id){failures.crashAcceptor(id);}public void restartAcceptor(NodeId id){failures.restartAcceptor(id);}public void crashReplica(NodeId id){failures.crashReplica(id);}public void restartReplica(NodeId id){failures.restartReplica(id);}public void crashLeader(NodeId id){failures.crashLeader(id);}public void restartLeader(NodeId id){failures.restartLeader(id);}
    public void partition(NodeId a,NodeId b){failures.partition(a,b);}public void heal(NodeId a,NodeId b){failures.heal(a,b);}public void healAll(){if(faultTransport!=null)failures.healAll();}
    public void partition(Set<NodeId> a,Set<NodeId> b){failures.partition(a,b);}
    public FailureController failures(){return failures;}public EventRecorder events(){return events;}public SafetyInvariantChecker safety(){return safety;}public WorkerRegistry workerRegistry(){return workerRegistry;}public WorkerEventProbe workerEvents(){return workerEvents;}public ClusterMembership membership(){return membership;}public Path dataDirectory(){return dataDirectory;}public ClusterConfig config(){return config;}
    public boolean isRunning(NodeId id){NodeLifecycle node=acceptors.containsKey(id)?acceptors.get(id):replicas.containsKey(id)?replicas.get(id):leaders.get(id);return node!=null&&node.isRunning();}
    public String diagnostics(){StringBuilder b=new StringBuilder("config=").append(config).append('\n');leaders.forEach((id,n)->{try{b.append(id).append('=').append(n.status()).append('\n');}catch(Exception e){b.append(id).append('=').append(e.getMessage()).append('\n');}});acceptors.forEach((id,n)->{try{b.append(id).append('=').append(n.status()).append('\n');}catch(Exception e){b.append(id).append('=').append(e.getMessage()).append('\n');}});replicas.forEach((id,n)->{try{b.append(id).append('=').append(n.status()).append('\n');}catch(Exception e){b.append(id).append('=').append(e.getMessage()).append('\n');}});b.append("workers=").append(workerRegistry.runningWorkers()).append('\n').append(events.formatTail(200));return b.toString();}
    @Override public void close(){List<NodeLifecycle> nodes=allNodes();Collections.reverse(nodes);nodes.forEach(n->{try{n.stop();}catch(RuntimeException ignored){}});transport.close();deleteTree(dataDirectory);}
    public void shutdown(){close();}
    private static void deleteTree(Path root){try(var paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}}
}
