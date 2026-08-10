package paxoslocker.grader;
import org.junit.jupiter.api.*; import paxoslocker.diagnostics.WorkerHook; import paxoslocker.model.*; import paxoslocker.protocol.P1bMessage; import paxoslocker.testkit.InMemoryTransport; import paxoslocker.worker.Scout; import java.util.*; import static org.junit.jupiter.api.Assertions.*;
@Tag("student") class ScoutUnitTest {
 private Scout scout(InMemoryTransport t){return new Scout(UnitFixtures.L1,new BallotNumber(1,"L1"),Set.of(UnitFixtures.A1,UnitFixtures.A2,UnitFixtures.A3),2,t,WorkerHook.NOOP);}
 @Test void quorumAdopts(){try(var t=UnitFixtures.transport()){var s=scout(t);s.start();s.onP1b(new P1bMessage(UnitFixtures.A1,new BallotNumber(1,"L1"),Set.of()));s.onP1b(new P1bMessage(UnitFixtures.A2,new BallotNumber(1,"L1"),Set.of()));assertFalse(s.status().running());}}
 @Test void minorityDoesNotAdoptAndDuplicateDoesNotCount(){try(var t=UnitFixtures.transport()){var s=scout(t);s.start();var m=new P1bMessage(UnitFixtures.A1,new BallotNumber(1,"L1"),Set.of());s.onP1b(m);s.onP1b(m);assertTrue(s.status().running());}}
 @Test void higherBallotPreempts(){try(var t=UnitFixtures.transport()){var s=scout(t);s.start();s.onP1b(new P1bMessage(UnitFixtures.A1,new BallotNumber(2,"other"),Set.of()));assertFalse(s.status().running());}}
 @Test void killIsIdempotent(){try(var t=UnitFixtures.transport()){var s=scout(t);s.kill();s.kill();assertTrue(s.isKilled());}}
}
