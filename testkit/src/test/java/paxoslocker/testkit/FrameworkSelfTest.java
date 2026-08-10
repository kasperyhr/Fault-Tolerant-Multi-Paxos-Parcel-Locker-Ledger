package paxoslocker.testkit;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import paxoslocker.acceptor.Acceptor;
import paxoslocker.app.*;
import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import paxoslocker.persistence.FileStore;
import paxoslocker.replica.Replica;
import paxoslocker.protocol.*;
import paxoslocker.transport.*;
import paxoslocker.worker.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class FrameworkSelfTest {
    private static final NodeId A1=new NodeId("A1"),A2=new NodeId("A2"),A3=new NodeId("A3"),R1=new NodeId("R1"),R2=new NodeId("R2"),L1=new NodeId("L1");
    private static ClusterMembership membership(){return new ClusterMembership(Set.of(A1,A2,A3),Set.of(R1,R2),Set.of(L1),2);}

    @Test void membershipIsImmutableAndValidatesMajorityAndDisjointRoles(){
        Set<NodeId> source=new HashSet<>(Set.of(A1,A2,A3));var m=new ClusterMembership(source,Set.of(R1),Set.of(L1),2);source.clear();assertEquals(3,m.acceptors().size());
        assertThrows(IllegalArgumentException.class,()->new ClusterMembership(Set.of(A1,A2,A3),Set.of(R1),Set.of(L1),1));
        assertThrows(IllegalArgumentException.class,()->new ClusterMembership(Set.of(A1,A2,A3),Set.of(A1),Set.of(L1),2));
    }
    @Test void dynamicPortAllocation(){assertTrue(PortAllocator.availableLoopbackPort()>0);}
    @Test void eventTraceIsOrderedAndBounded(){var r=new EventRecorder(200);for(int i=0;i<250;i++)r.record(R1,Role.REPLICA,EventType.MESSAGE_SENT,null,(long)i,null,null,"");assertEquals(200,r.snapshot().size());assertTrue(r.snapshot().getFirst().sequenceNumber()<r.snapshot().getLast().sequenceNumber());}
    @Test void dropDuplicateDelayPartitionAndResume(){var events=new EventRecorder();try(var t=new InMemoryTransport(events)){var received=new CopyOnWriteArrayList<MessageEnvelope>();t.register(R1,received::add);t.dropNext(MessagePredicate.any());t.send(message(L1,R1));assertEquals(0,received.size());t.duplicateNext(MessagePredicate.any());t.send(message(L1,R1));assertEquals(2,received.size());t.delayNext(MessagePredicate.any(),Duration.ofMillis(20));t.send(message(L1,R1));Await.until(()->received.size()==3,Duration.ofSeconds(1),"delay");t.partition(L1,R1);t.send(message(L1,R1));assertEquals(3,received.size());t.heal(L1,R1);t.pause(R1);t.send(message(L1,R1));t.resume(R1);assertEquals(4,received.size());}}
    @Test void deterministicReorderDeliversLaterMessageFirst(){var events=new EventRecorder();try(var t=new InMemoryTransport(events)){var received=new CopyOnWriteArrayList<UUID>();t.register(R1,e->received.add(e.messageId()));var first=message(L1,R1);var second=message(L1,R1);t.reorderNext(MessagePredicate.any());t.send(first);assertTrue(received.isEmpty());t.send(second);assertEquals(List.of(second.messageId(),first.messageId()),received);}}
    @Test void tcpSerializesEnvelopeOnDynamicLoopbackPort() throws Exception {try(var t=new LocalTcpTransport()){var latch=new CountDownLatch(1);var received=new MessageEnvelope[1];t.register(R1,e->{received[0]=e;latch.countDown();});t.send(message(L1,R1));assertTrue(latch.await(2,TimeUnit.SECONDS));assertEquals(L1,received[0].source());assertTrue(received[0].message() instanceof HeartbeatMessage);}}
    @Test void dispatchRoutesP1aAndLifecycleUnregisters(@TempDir Path dir){
        try(var t=new InMemoryTransport(new EventRecorder())){var acceptor=new FakeAcceptor(t,new FileStore(dir));var replies=new CopyOnWriteArrayList<MessageEnvelope>();t.register(L1,replies::add);acceptor.start();assertTrue(t.isRegistered(A1));var ballot=new BallotNumber(1,"L1");t.send(MessageEnvelope.of(L1,A1,new P1aMessage(ballot)));assertEquals(ballot,((P1bMessage)replies.getFirst().message()).requestedBallot());acceptor.stop();assertFalse(t.isRegistered(A1));acceptor.restart();assertTrue(t.isRegistered(A1));acceptor.start();assertTrue(t.isRegistered(A1));}
    }
    @Test void replicaDispatcherRejectsSpoofedSyncAndNonLeaderHeartbeat(@TempDir Path dir){try(var t=new InMemoryTransport(new EventRecorder())){var replica=new FakeReplica(t,new FileStore(dir));replica.start();replica.receive(MessageEnvelope.of(R2,R1,new DecisionSyncRequestMessage(R1,1,10)));assertEquals(0,replica.syncRequests);replica.receive(MessageEnvelope.of(R2,R1,new DecisionSyncRequestMessage(R2,1,10)));assertEquals(1,replica.syncRequests);replica.receive(MessageEnvelope.of(A1,R1,new HeartbeatMessage(new BallotNumber(1,"A1"),true)));assertEquals(0,replica.heartbeats);replica.receive(MessageEnvelope.of(L1,R1,new HeartbeatMessage(new BallotNumber(1,"L1"),true)));assertEquals(1,replica.heartbeats);}}
    @Test void workerRegistryActuallyKillsAndUnregistersWorkers(){try(var t=new InMemoryTransport(new EventRecorder())){var registry=new WorkerRegistry();var probe=new WorkerEventProbe();var factory=new InstrumentedWorkerFactory(new DefaultWorkerFactory(),registry,probe);var ballot=new BallotNumber(1,"L1");var scout=factory.createScout(L1,ballot,Set.of(A1,A2,A3),2,t,WorkerHook.NOOP);assertTrue(registry.killScout(L1,ballot));assertTrue(scout.isKilled());assertTrue(registry.runningWorkers().isEmpty());var pv=new PValue(ballot,1,new NoOp(UUID.randomUUID()));var commander=factory.createCommander(L1,pv,Set.of(A1,A2,A3),Set.of(R1),2,t,WorkerHook.NOOP);assertTrue(registry.killCommander(L1,ballot,1));assertTrue(commander.isKilled());assertTrue(registry.runningWorkers().isEmpty());}}
    @Test void workerHookCanAwaitAndKillOnEvent(){var probe=new WorkerEventProbe();var ballot=new BallotNumber(1,"L1");var killed=new AtomicBoolean();var hook=probe.hookFor(L1,WorkerHook.NOOP);probe.killOnNext(L1,ballot,null,WorkerEventType.P1A_BEFORE_SEND,()->killed.set(true));hook.onEvent(WorkerEventType.P1A_BEFORE_SEND,ballot,null);probe.await(L1,ballot,null,WorkerEventType.P1A_BEFORE_SEND,Duration.ofSeconds(1));assertTrue(killed.get());}
    @Test void diagnosticSinkRecordsAndFeedsSafetyChecker(){var events=new EventRecorder();var checker=new SafetyInvariantChecker();var sink=new RecordingDiagnosticSink(events,checker);var command=new NoOp(UUID.randomUUID());sink.record(new ProtocolDiagnosticEvent(R1,Role.REPLICA,ProtocolDiagnosticType.DECISION_LEARNED,null,1L,command.requestId(),null,command,""));assertEquals(1,events.snapshot().size());}
    @Test void diagnosticMappingIsTotal(){var events=new EventRecorder();var sink=new RecordingDiagnosticSink(events,new SafetyInvariantChecker());for(var type:ProtocolDiagnosticType.values())sink.record(new ProtocolDiagnosticEvent(R1,Role.REPLICA,type,null,null,null,null,null,"mapping"));assertEquals(ProtocolDiagnosticType.values().length,events.snapshot().size());}
    @Test void requestDeduplicationIsPerReplica(){var c=new SafetyInvariantChecker();var command=new NoOp(UUID.randomUUID());c.observeExecuted(R1,1,command);c.observeExecuted(R2,1,command);assertThrows(SafetyViolationException.class,()->c.observeExecuted(R1,2,command));}
    @Test void checkerRejectsChosenA4AndBallotConflicts(){var c=new SafetyInvariantChecker();var one=new NoOp(UUID.randomUUID());var two=new NoOp(UUID.randomUUID());var b=new BallotNumber(1,"L1");c.observeChosen(1,one);assertThrows(SafetyViolationException.class,()->c.observeChosen(1,two));var d=new SafetyInvariantChecker();d.observeAccepted(A1,b,1,one);assertThrows(SafetyViolationException.class,()->d.observeAccepted(A2,b,1,two));}
    @Test void learnedDoesNotAutomaticallyBecomeChosen(){var c=new SafetyInvariantChecker(2,true);var command=new NoOp(UUID.randomUUID());c.observeReplicaDecision(R1,1,command);assertTrue(c.chosenSnapshot().isEmpty());assertEquals(command,c.learnedSnapshot().get(R1).get(1L));}
    @Test void distinctAcceptedQuorumEstablishesChosenEvidence(){var c=new SafetyInvariantChecker(2,true);var command=new NoOp(UUID.randomUUID());var b=new BallotNumber(2,"L1");c.observeAccepted(A1,b,1,command);assertFalse(c.isQuorumChosen(1,command));c.observeAccepted(A2,b,1,command);assertTrue(c.isQuorumChosen(1,command));}
    @Test void strictValueChosenRequiresObservedQuorum(){var c=new SafetyInvariantChecker(2,true);var command=new NoOp(UUID.randomUUID());var failure=assertThrows(SafetyViolationException.class,()->c.observeChosen(new BallotNumber(2,"L1"),1,command));assertEquals(SafetyViolationKind.VALUE_CHOSEN_WITHOUT_QUORUM,failure.kind());}
    @Test void a5CheckUsesQuorumEvidenceAndKnownBallotOrdering(){var c=new SafetyInvariantChecker(2,true);var chosen=new NoOp(UUID.randomUUID());var other=new NoOp(UUID.randomUUID());var b2=new BallotNumber(2,"L1");c.observeAccepted(A1,b2,1,chosen);c.observeAccepted(A2,b2,1,chosen);assertDoesNotThrow(()->c.observeAccepted(A3,new BallotNumber(1,"L1"),1,other));var failure=assertThrows(SafetyViolationException.class,()->c.observeAccepted(A3,new BallotNumber(3,"L2"),1,other));assertEquals(SafetyViolationKind.A5,failure.kind());}
    @Test void chosenConflictHasStructuredKindAndMarker(){var c=new SafetyInvariantChecker();var one=new NoOp(UUID.randomUUID());var two=new NoOp(UUID.randomUUID());c.observeChosen(1,one);var failure=assertThrows(SafetyViolationException.class,()->c.observeChosen(1,two));assertEquals(SafetyViolationKind.CHOSEN_CONFLICT,failure.kind());assertTrue(failure.getMessage().startsWith("SAFETY_CHOSEN_CONFLICT:"));}
    @Test void harnessUsesFreshDirectoryAndCleansIt(){Path path;try(var cluster=ClusterHarness.start(ClusterConfig.small())){path=cluster.dataDirectory();assertTrue(Files.isDirectory(path));assertEquals(3,cluster.membership().acceptors().size());}assertFalse(Files.exists(path));}

    private static MessageEnvelope message(NodeId a,NodeId b){return MessageEnvelope.of(a,b,new HeartbeatMessage(new BallotNumber(1,"L1"),true));}
    private static final class FakeAcceptor extends Acceptor {
        FakeAcceptor(Transport t,FileStore s){super(A1,t,s,membership(),DiagnosticSink.NOOP);}
        @Override public P1bMessage onP1a(P1aMessage m){return new P1bMessage(A1,m.ballot(),m.ballot(),Set.of());}
        @Override public AcceptorStatus status(){return new AcceptorStatus(BallotNumber.BOTTOM,Set.of());}
    }
    private static final class FakeReplica extends Replica {
        int syncRequests,heartbeats;
        FakeReplica(Transport t,FileStore s){super(R1,t,s,List.of("locker-1"),membership(),DiagnosticSink.NOOP);}
        void receive(MessageEnvelope envelope){onEnvelope(envelope);}
        @Override public void onDecisionSyncRequest(DecisionSyncRequestMessage request,NodeId peer){syncRequests++;}
        @Override public void onHeartbeat(HeartbeatMessage heartbeat,NodeId leader){heartbeats++;}
        @Override public ReplicaStatus status(){return new ReplicaStatus(0,Map.of(),Map.of(),null,Map.of());}
    }
}
