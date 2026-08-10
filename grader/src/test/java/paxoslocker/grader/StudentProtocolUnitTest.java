package paxoslocker.grader;

import paxoslocker.acceptor.Acceptor; import paxoslocker.leader.Leader; import paxoslocker.model.*;
import paxoslocker.persistence.FileStore; import paxoslocker.protocol.*; import paxoslocker.testkit.*;
import org.junit.jupiter.api.*; import org.junit.jupiter.api.io.TempDir; import java.nio.file.Path; import java.util.*; import static org.junit.jupiter.api.Assertions.*;

@Tag("student") class StudentProtocolUnitTest {
 @TempDir Path data;
 @Test void acceptorFirstP1aAndMonotonicity(){var a=new Acceptor(new NodeId("A"),new FileStore(data));var low=new BallotNumber(1,"L");var high=new BallotNumber(2,"L");assertEquals(low,a.onP1a(new P1aMessage(low)).ballot());assertEquals(high,a.onP1a(new P1aMessage(high)).ballot());assertEquals(high,a.onP1a(new P1aMessage(low)).ballot());}
 @Test void acceptorP2aPersistsAccepted(){var store=new FileStore(data);var a=new Acceptor(new NodeId("A"),store);var ballot=new BallotNumber(1,"L");a.onP1a(new P1aMessage(ballot));var pv=new PValue(ballot,1,new NoOp(UUID.randomUUID()));a.onP2a(new P2aMessage(pv));a.stop();var restarted=new Acceptor(new NodeId("A"),store);restarted.start();assertTrue(restarted.status().accepted().contains(pv));}
 @Test void pmaxSelectsHighestPerSlot(){var c1=new NoOp(UUID.randomUUID());var c2=new NoOp(UUID.randomUUID());var result=Leader.pmax(Set.of(new PValue(new BallotNumber(1,"A"),1,c1),new PValue(new BallotNumber(2,"B"),1,c2),new PValue(new BallotNumber(1,"A"),2,c1)));assertEquals(c2,result.get(1L));assertEquals(c1,result.get(2L));}
}
