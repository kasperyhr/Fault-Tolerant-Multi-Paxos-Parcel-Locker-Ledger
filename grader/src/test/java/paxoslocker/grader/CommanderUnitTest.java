package paxoslocker.grader;
import org.junit.jupiter.api.*; import paxoslocker.diagnostics.WorkerHook; import paxoslocker.model.*; import paxoslocker.protocol.P2bMessage; import paxoslocker.testkit.InMemoryTransport; import paxoslocker.worker.Commander; import java.util.*; import static org.junit.jupiter.api.Assertions.*;
@Tag("student") class CommanderUnitTest {
 private Commander commander(InMemoryTransport t){return new Commander(UnitFixtures.L1,new PValue(new BallotNumber(1,"L1"),1,new NoOp(UUID.randomUUID())),Set.of(UnitFixtures.A1,UnitFixtures.A2,UnitFixtures.A3),Set.of(UnitFixtures.R1),2,t,WorkerHook.NOOP);}
 @Test void quorumChooses(){try(var t=UnitFixtures.transport()){var c=commander(t);c.start();c.onP2b(new P2bMessage(UnitFixtures.A1,c.pvalue().ballot(),1));c.onP2b(new P2bMessage(UnitFixtures.A2,c.pvalue().ballot(),1));assertTrue(c.status().chosen());}}
 @Test void minorityAndDuplicateDoNotChoose(){try(var t=UnitFixtures.transport()){var c=commander(t);c.start();var m=new P2bMessage(UnitFixtures.A1,c.pvalue().ballot(),1);c.onP2b(m);c.onP2b(m);assertFalse(c.status().chosen());}}
 @Test void higherBallotPreempts(){try(var t=UnitFixtures.transport()){var c=commander(t);c.start();c.onP2b(new P2bMessage(UnitFixtures.A1,new BallotNumber(2,"other"),1));assertFalse(c.status().running());}}
 @Test void killIsIdempotent(){try(var t=UnitFixtures.transport()){var c=commander(t);c.kill();c.kill();assertTrue(c.isKilled());}}
}
