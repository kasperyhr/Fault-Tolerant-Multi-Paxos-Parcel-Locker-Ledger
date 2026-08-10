package paxoslocker.grader;
import org.junit.jupiter.api.*; import paxoslocker.diagnostics.*; import paxoslocker.leader.Leader; import paxoslocker.model.*; import paxoslocker.protocol.*; import paxoslocker.testkit.InMemoryTransport; import paxoslocker.worker.DefaultWorkerFactory; import java.util.*; import static org.junit.jupiter.api.Assertions.*;
@Tag("student") class LeaderUnitTest {
 private Leader node(InMemoryTransport t){Leader l=new Leader(UnitFixtures.L1,t,UnitFixtures.membership(),new DefaultWorkerFactory(),UnitFixtures.sink(),WorkerHook.NOOP);l.start();return l;}
 @Test void initiallyPassive(){try(var t=UnitFixtures.transport()){assertFalse(node(t).status().active());}}
 @Test void adoptedActivatesAndPmaxOverrides(){try(var t=UnitFixtures.transport()){var l=node(t);var b=new BallotNumber(1,"L1");Command c=new NoOp(UUID.randomUUID());l.onAdopted(new AdoptedMessage(b,Set.of(new PValue(b,1,c))));assertTrue(l.status().active());assertEquals(c,l.status().proposals().get(1L));}}
 @Test void activeProposalCreatesCommander(){try(var t=UnitFixtures.transport()){var l=node(t);var b=new BallotNumber(1,"L1");l.onAdopted(new AdoptedMessage(b,Set.of()));l.onPropose(new ProposeMessage(1,new NoOp(UUID.randomUUID())));assertEquals(1,l.status().runningCommanders().size());}}
 @Test void preemptionDeactivatesAndAdvancesBallot(){try(var t=UnitFixtures.transport()){var l=node(t);var observed=new BallotNumber(9,"other");l.onPreempted(new PreemptedMessage(observed));assertFalse(l.status().active());assertTrue(l.status().ballot().compareTo(observed)>0);}}
}
