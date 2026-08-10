package paxoslocker.grader;
import org.junit.jupiter.api.*; import paxoslocker.model.*; import paxoslocker.testkit.*; import java.time.Duration; import java.util.*;
@Tag("stress") class StressF3Test {
 @Test void threeFailuresProgressFourFailuresPauseAndRecoveryResumes(){try(var c=ClusterHarness.start(new ClusterConfig(3,7,3,3,Duration.ofMinutes(3),123456))){c.awaitLeader(Duration.ofSeconds(20));List<NodeId>a=c.acceptorIds().stream().toList();a.stream().limit(3).forEach(c::crashAcceptor);c.submit(new NoOp(UUID.randomUUID()));c.awaitDecision(1,Duration.ofSeconds(20));c.crashAcceptor(a.get(3));c.submit(new NoOp(UUID.randomUUID()));Assertions.assertThrows(AssertionError.class,()->c.awaitDecision(2,Duration.ofSeconds(1)));c.restartAcceptor(a.get(0));c.awaitDecision(2,Duration.ofSeconds(20));c.awaitReplicaConvergence(Duration.ofSeconds(20));}}
}
