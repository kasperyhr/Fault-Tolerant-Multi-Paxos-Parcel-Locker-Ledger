package paxoslocker.grader;
import org.junit.jupiter.api.*; import org.junit.jupiter.api.io.TempDir; import paxoslocker.acceptor.Acceptor; import paxoslocker.model.*; import paxoslocker.persistence.FileStore; import paxoslocker.protocol.*; import paxoslocker.testkit.InMemoryTransport; import java.nio.file.Path; import java.util.UUID; import static org.junit.jupiter.api.Assertions.*;
@Tag("student") class AcceptorUnitTest {
 @TempDir Path dir;
 private Acceptor node(InMemoryTransport t){Acceptor a=new Acceptor(UnitFixtures.A1,t,new FileStore(dir),UnitFixtures.membership(),UnitFixtures.sink());a.start();return a;}
 @Test void firstP1aIsAdopted(){try(var t=UnitFixtures.transport()){var a=node(t);var b=new BallotNumber(1,"L1");assertEquals(b,a.onP1a(new P1aMessage(b)).ballot());}}
 @Test void lowerP1aDoesNotRegress(){try(var t=UnitFixtures.transport()){var a=node(t);var high=new BallotNumber(2,"L1");a.onP1a(new P1aMessage(high));assertEquals(high,a.onP1a(new P1aMessage(new BallotNumber(1,"L1"))).ballot());}}
 @Test void equalP1aIsIdempotent(){try(var t=UnitFixtures.transport()){var a=node(t);var b=new BallotNumber(2,"L1");assertEquals(a.onP1a(new P1aMessage(b)),a.onP1a(new P1aMessage(b)));}}
 @Test void validP2aIsAcceptedAndDuplicateIsIdempotent(){try(var t=UnitFixtures.transport()){var a=node(t);var b=new BallotNumber(1,"L1");a.onP1a(new P1aMessage(b));var m=new P2aMessage(new PValue(b,1,new NoOp(UUID.randomUUID())));assertEquals(a.onP2a(m),a.onP2a(m));}}
 @Test void lowerP2aIsRejected(){try(var t=UnitFixtures.transport()){var a=node(t);a.onP1a(new P1aMessage(new BallotNumber(2,"L1")));assertEquals(new BallotNumber(2,"L1"),a.onP2a(new P2aMessage(new PValue(new BallotNumber(1,"L1"),1,new NoOp(UUID.randomUUID())))).ballot());}}
 @Test void ballotAndAcceptedSurviveRestart(){try(var t=UnitFixtures.transport()){var a=node(t);var b=new BallotNumber(4,"L1");a.onP1a(new P1aMessage(b));var pv=new PValue(b,1,new NoOp(UUID.randomUUID()));a.onP2a(new P2aMessage(pv));a.stop();var recovered=node(t);assertEquals(b,recovered.status().ballot());assertTrue(recovered.status().accepted().contains(pv));}}
}
