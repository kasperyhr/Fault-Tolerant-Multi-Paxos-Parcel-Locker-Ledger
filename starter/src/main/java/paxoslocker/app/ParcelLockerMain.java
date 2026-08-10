package paxoslocker.app;

import paxoslocker.diagnostics.DiagnosticSink;
import paxoslocker.model.NodeId;
import paxoslocker.persistence.FileStore;
import paxoslocker.transport.LocalTcpTransport;
import paxoslocker.worker.DefaultWorkerFactory;
import java.nio.file.*;
import java.util.*;

/** Localhost cluster launcher skeleton. Protocol progress begins after students complete role TODOs. */
public final class ParcelLockerMain {
    private ParcelLockerMain() { }
    public static void main(String[] args) throws Exception {
        Map<String,String> options=parse(args); if(options.containsKey("help")){usage();return;}
        int f=integer(options,"fault-tolerance",1),acceptorCount=integer(options,"acceptors",2*f+1);
        int replicaCount=integer(options,"replicas",3),leaderCount=integer(options,"leaders",3);
        int holdSeconds=integer(options,"run-seconds",0); new ClusterOptions(f,acceptorCount);
        Set<NodeId> acceptors=ids("acceptor",acceptorCount),replicas=ids("replica",replicaCount),leaders=ids("leader",leaderCount);
        ClusterMembership membership=new ClusterMembership(acceptors,replicas,leaders,acceptorCount/2+1);
        Path data=Paths.get(options.getOrDefault("data-dir","data")).toAbsolutePath();
        RuntimeNodeFactory factory=new DefaultRuntimeNodeFactory(); var workers=new DefaultWorkerFactory();
        List<NodeLifecycle> nodes=new ArrayList<>();
        try(var transport=new LocalTcpTransport()) {
            try {
                acceptors.forEach(id->nodes.add(factory.acceptor(id,transport,new FileStore(data.resolve(id.value())),membership,DiagnosticSink.NOOP)));
                replicas.forEach(id->nodes.add(factory.replica(id,transport,new FileStore(data.resolve(id.value())),List.of("locker-1","locker-2","locker-3"),membership,DiagnosticSink.NOOP)));
                leaders.forEach(id->nodes.add(factory.leader(id,transport,membership,workers,DiagnosticSink.NOOP)));
                nodes.forEach(NodeLifecycle::start);
                System.out.printf("localhost cluster started: f=%d acceptors=%d replicas=%d leaders=%d quorum=%d%n",f,acceptorCount,replicaCount,leaderCount,membership.quorum());
                System.out.println("Protocol progress requires completing the documented TODO(student) handlers.");
                if(holdSeconds>0) Thread.sleep(holdSeconds*1000L);
            } finally { List<NodeLifecycle> reverse=new ArrayList<>(nodes);Collections.reverse(reverse);reverse.forEach(NodeLifecycle::stop); }
        }
    }
    static Map<String,String> parse(String[] args){Map<String,String> out=new LinkedHashMap<>();for(int i=0;i<args.length;i++){String a=args[i];if(a.equals("--help")){out.put("help","true");continue;}if(!a.startsWith("--")||i+1>=args.length)throw new IllegalArgumentException("expected --name value");out.put(a.substring(2),args[++i]);}return out;}
    private static int integer(Map<String,String> options,String key,int fallback){return Integer.parseInt(options.getOrDefault(key,Integer.toString(fallback)));}
    private static Set<NodeId> ids(String prefix,int count){if(count<1)throw new IllegalArgumentException(prefix+" count must be positive");Set<NodeId> ids=new LinkedHashSet<>();for(int i=0;i<count;i++)ids.add(new NodeId(prefix+"-"+i));return Set.copyOf(ids);}
    private static void usage(){System.out.println("Usage: --fault-tolerance <f> [--acceptors n] [--replicas n] [--leaders n] [--data-dir path] [--run-seconds n]");}
}
