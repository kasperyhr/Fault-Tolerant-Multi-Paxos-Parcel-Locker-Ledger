package paxoslocker.grader;
import paxoslocker.testkit.ClusterConfig; import org.junit.jupiter.api.*; import java.time.Duration; import static org.junit.jupiter.api.Assertions.*;
@Tag("stress") class StressConfigurationTest { @Test void f2AndF3Quorums(){assertEquals(3,new ClusterConfig(2,5,3,3,Duration.ofMinutes(2),1).quorum());assertEquals(4,new ClusterConfig(3,7,3,3,Duration.ofMinutes(2),2).quorum());} }
