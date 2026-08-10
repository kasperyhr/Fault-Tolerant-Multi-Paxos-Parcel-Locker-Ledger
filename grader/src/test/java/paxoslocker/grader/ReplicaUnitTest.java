package paxoslocker.grader;
import org.junit.jupiter.api.*; import org.junit.jupiter.api.io.TempDir; import paxoslocker.model.*; import paxoslocker.persistence.FileStore; import paxoslocker.protocol.DecisionMessage; import paxoslocker.replica.Replica; import paxoslocker.testkit.InMemoryTransport; import java.nio.file.Path; import java.util.*; import static org.junit.jupiter.api.Assertions.*;
@Tag("student") class ReplicaUnitTest {
 @TempDir Path dir; private Replica node(InMemoryTransport t){Replica r=new Replica(UnitFixtures.R1,t,new FileStore(dir),List.of("locker-1"),UnitFixtures.membership(),UnitFixtures.sink());r.start();return r;}
 @Test void submitCreatesProposal(){try(var t=UnitFixtures.transport()){var r=node(t);Command c=new NoOp(UUID.randomUUID());r.submit(c);assertTrue(r.status().pendingProposals().containsValue(c));}}
 @Test void outOfOrderDecisionLeavesHoleBlocked(){try(var t=UnitFixtures.transport()){var r=node(t);r.onDecision(new DecisionMessage(2,new NoOp(UUID.randomUUID())));assertEquals(0,r.status().lastExecutedSlot());}}
 @Test void duplicateDecisionIsIdempotent(){try(var t=UnitFixtures.transport()){var r=node(t);var d=new DecisionMessage(1,new NoOp(UUID.randomUUID()));r.onDecision(d);r.onDecision(d);assertEquals(1,r.status().lastExecutedSlot());}}
 @Test void competingDecisionReproposesLoser(){try(var t=UnitFixtures.transport()){var r=node(t);Command proposed=new NoOp(UUID.randomUUID()),winner=new NoOp(UUID.randomUUID());r.submit(proposed);r.onDecision(new DecisionMessage(1,winner));assertTrue(r.status().pendingProposals().containsValue(proposed));}}
 @Test void restartReplaysWithoutDuplicateEffect(){try(var t=UnitFixtures.transport()){var r=node(t);Command c=new ReserveLocker(UUID.randomUUID(),"c","locker-1");r.onDecision(new DecisionMessage(1,c));r.stop();var recovered=node(t);assertEquals(1,recovered.status().lastExecutedSlot());assertEquals(LockerStatus.RESERVED,recovered.status().applicationState().get("locker-1").status());}}
}
