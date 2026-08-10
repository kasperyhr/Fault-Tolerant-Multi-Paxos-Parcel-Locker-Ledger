package paxoslocker.grader;
import org.junit.jupiter.api.*; import paxoslocker.diagnostics.WorkerHook; import paxoslocker.model.*; import paxoslocker.protocol.P2bMessage; import paxoslocker.testkit.InMemoryTransport; import paxoslocker.worker.Commander; import java.util.*; import static org.junit.jupiter.api.Assertions.*;
@Tag("student") class CommanderUnitTest {
 private Commander commander(InMemoryTransport t){return new Commander(UnitFixtures.L1,new PValue(new BallotNumber(1,"L1"),1,new NoOp(UUID.randomUUID())),Set.of(UnitFixtures.A1,UnitFixtures.A2,UnitFixtures.A3),Set.of(UnitFixtures.R1),2,t,WorkerHook.NOOP);}
    @Test void quorumChooses(){try(var t=UnitFixtures.transport()){var c=commander(t);c.start();var b=c.pvalue().ballot();c.onP2b(new P2bMessage(UnitFixtures.A1,b,b,1));c.onP2b(new P2bMessage(UnitFixtures.A2,b,b,1));assertTrue(c.status().chosen());}}
    @Test void minorityAndDuplicateDoNotChoose(){try(var t=UnitFixtures.transport()){var c=commander(t);c.start();var b=c.pvalue().ballot();var m=new P2bMessage(UnitFixtures.A1,b,b,1);c.onP2b(m);c.onP2b(m);assertFalse(c.status().chosen());}}
    @Test void higherBallotPreempts(){try(var t=UnitFixtures.transport()){var c=commander(t);c.start();var b=c.pvalue().ballot();c.onP2b(new P2bMessage(UnitFixtures.A1,b,new BallotNumber(2,"other"),1));assertFalse(c.status().running());}}
    @Test void wrongRequestedBallotDoesNotCount(){try(var t=UnitFixtures.transport()){var c=commander(t);c.start();var wrong=new BallotNumber(0,"old");c.onP2b(new P2bMessage(UnitFixtures.A1,wrong,c.pvalue().ballot(),1));assertTrue(c.status().responses().isEmpty());}}
 @Test void killIsIdempotent(){try(var t=UnitFixtures.transport()){var c=commander(t);c.kill();c.kill();assertTrue(c.isKilled());}}
}
