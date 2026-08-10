package paxoslocker.grader;
import org.junit.jupiter.api.*; import paxoslocker.model.*; import paxoslocker.testkit.*; import java.time.Duration; import java.util.UUID;
@Tag("stress") class LongLogStressTest {
 @Test void fiftyThousandSlotsWithoutSnapshotOrGc(){try(var c=ClusterHarness.start(new ClusterConfig(2,5,3,3,Duration.ofMinutes(10),123456))){c.awaitLeader(Duration.ofSeconds(20));for(int i=0;i<50_000;i++)c.submit(new NoOp(UUID.randomUUID()));c.awaitDecision(50_000,Duration.ofMinutes(10));c.awaitExecutedThrough(50_000,Duration.ofMinutes(10));NodeId a=c.acceptorIds().iterator().next();var before=c.inspectAcceptor(a);c.crashAcceptor(a);c.restartAcceptor(a);Assertions.assertTrue(c.inspectAcceptor(a).accepted().containsAll(before.accepted()));c.awaitReplicaConvergence(Duration.ofMinutes(2));}}
}
