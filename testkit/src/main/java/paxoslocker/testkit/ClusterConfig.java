package paxoslocker.testkit;
import java.time.Duration;
public record ClusterConfig(int faultTolerance,int acceptors,int replicas,int leaders,
                            Duration timeout,long seed,TransportMode transportMode) {
    public ClusterConfig { if(faultTolerance<0||acceptors<2*faultTolerance+1||replicas<1||leaders<1||timeout.isNegative()||timeout.isZero()||transportMode==null)throw new IllegalArgumentException("invalid cluster configuration"); }
    public ClusterConfig(int f,int a,int r,int l,Duration t,long s){this(f,a,r,l,t,s,TransportMode.IN_MEMORY);}
    public int quorum(){return acceptors/2+1;}
    public static ClusterConfig small(){return new ClusterConfig(1,3,3,2,Duration.ofSeconds(10),123456L,TransportMode.IN_MEMORY);}
    public static ClusterConfig localTcpSmall(){return new ClusterConfig(1,3,3,2,Duration.ofSeconds(15),123456L,TransportMode.LOCAL_TCP);}
}
