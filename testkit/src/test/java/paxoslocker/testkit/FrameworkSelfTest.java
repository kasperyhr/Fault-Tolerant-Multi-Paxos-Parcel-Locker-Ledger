package paxoslocker.testkit;

import paxoslocker.model.*; import paxoslocker.protocol.HeartbeatMessage; import paxoslocker.transport.*;
import org.junit.jupiter.api.*; import java.time.*; import java.util.*; import java.util.concurrent.*; import static org.junit.jupiter.api.Assertions.*;
class FrameworkSelfTest {
 @Test void dynamicPortAllocation(){int a=PortAllocator.availableLoopbackPort(),b=PortAllocator.availableLoopbackPort();assertTrue(a>0);assertTrue(b>0);}
 @Test void eventTraceIsOrderedAndBounded(){var r=new EventRecorder(200);for(int i=0;i<250;i++)r.record(new NodeId("n"),Role.REPLICA,EventType.MESSAGE_SENT,null,(long)i,null,null,"");assertEquals(200,r.snapshot().size());assertTrue(r.snapshot().getFirst().sequenceNumber()<r.snapshot().getLast().sequenceNumber());}
 @Test void dropDuplicateDelayPartitionAndResume() throws Exception {
   var events=new EventRecorder();try(var transport=new InMemoryTransport(events)){var a=new NodeId("a");var b=new NodeId("b");var received=new CopyOnWriteArrayList<MessageEnvelope>();transport.register(b,received::add);
   transport.dropNext(MessagePredicate.any());transport.send(message(a,b));assertEquals(0,received.size());
   transport.duplicateNext(MessagePredicate.any());transport.send(message(a,b));assertEquals(2,received.size());
   transport.delayNext(MessagePredicate.any(),Duration.ofMillis(30));transport.send(message(a,b));assertEquals(2,received.size());Await.until(()->received.size()==3,Duration.ofSeconds(1),"delayed delivery");
   transport.partition(a,b);transport.send(message(a,b));assertEquals(3,received.size());transport.heal(a,b);
   transport.pause(b);transport.send(message(a,b));assertEquals(3,received.size());transport.resume(b);assertEquals(4,received.size());}}
 @Test void tcpUsesDynamicLoopbackPort() throws Exception {try(var transport=new LocalTcpTransport()){var a=new NodeId("a");var b=new NodeId("b");var latch=new CountDownLatch(1);transport.register(b,e->latch.countDown());assertTrue(transport.port(b)>0);transport.send(message(a,b));assertTrue(latch.await(2,TimeUnit.SECONDS));}}
 @Test void invariantCheckerRejectsConflicts(){var c=new SafetyInvariantChecker();var a=new NoOp(UUID.randomUUID());var b=new NoOp(UUID.randomUUID());c.observeChosen(1,a);assertThrows(SafetyViolationException.class,()->c.observeChosen(1,b));}
 private static MessageEnvelope message(NodeId a,NodeId b){return MessageEnvelope.of(a,b,new HeartbeatMessage(new BallotNumber(1,"a"),true));}
}
