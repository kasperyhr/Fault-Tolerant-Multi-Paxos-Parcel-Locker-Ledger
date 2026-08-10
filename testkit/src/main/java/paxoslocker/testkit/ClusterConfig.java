package paxoslocker.testkit;
import java.time.Duration;
public record ClusterConfig(int faultTolerance,int acceptors,int replicas,int leaders,Duration timeout,long seed) {
    public ClusterConfig { if(faultTolerance<0||acceptors<2*faultTolerance+1||replicas<1||leaders<1)throw new IllegalArgumentException("invalid cluster configuration"); }
    public int quorum(){return acceptors/2+1;} public static ClusterConfig small(){return new ClusterConfig(1,3,3,2,Duration.ofSeconds(10),123456L);}
}
