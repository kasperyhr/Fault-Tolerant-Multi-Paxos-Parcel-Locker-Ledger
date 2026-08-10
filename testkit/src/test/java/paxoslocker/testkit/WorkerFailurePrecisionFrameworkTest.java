package paxoslocker.testkit;

import org.junit.jupiter.api.Test;
import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import paxoslocker.protocol.*;
import paxoslocker.transport.*;
import paxoslocker.worker.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WorkerFailurePrecisionFrameworkTest {
    private static final NodeId A1=new NodeId("A1"),A2=new NodeId("A2"),A3=new NodeId("A3"),R1=new NodeId("R1"),L1=new NodeId("L1");

    @Test void killIsArmedBeforeP1aSend(){
        var events=new EventRecorder();try(var transport=new InMemoryTransport(events)){
            var registry=new WorkerRegistry();var probe=new WorkerEventProbe();var factory=new InstrumentedWorkerFactory(new GateFactory(),registry,probe,events);var ballot=new BallotNumber(1,"L1");
            probe.onNextMatching(L1,WorkerKind.SCOUT,WorkerEventType.SCOUT_CREATED,e->probe.killOnNext(L1,e.ballot(),null,WorkerEventType.P1A_BEFORE_SEND,()->registry.killScout(L1,e.ballot())));
            Scout scout=factory.createScout(L1,ballot,Set.of(A1,A2,A3),2,transport,WorkerHook.NOOP);scout.start();
            assertTrue(scout.isKilled());assertFalse(sent(events,P1aMessage.class));
        }
    }

    @Test void killIsArmedBeforeP2aSend(){
        var events=new EventRecorder();try(var transport=new InMemoryTransport(events)){
            var registry=new WorkerRegistry();var probe=new WorkerEventProbe();var factory=new InstrumentedWorkerFactory(new GateFactory(),registry,probe,events);var ballot=new BallotNumber(1,"L1");var value=new PValue(ballot,7,new NoOp(UUID.randomUUID()));
            probe.onNextMatching(L1,WorkerKind.COMMANDER,WorkerEventType.COMMANDER_CREATED,e->probe.killOnNext(L1,e.ballot(),e.slot(),WorkerEventType.P2A_BEFORE_SEND,()->registry.killCommander(L1,e.ballot(),e.slot())));
            Commander commander=factory.createCommander(L1,value,Set.of(A1,A2,A3),Set.of(R1),2,transport,WorkerHook.NOOP);commander.start();
            assertTrue(commander.isKilled());assertFalse(sent(events,P2aMessage.class));
        }
    }

    @Test void repeatedKillIsIdempotentAndExitIsEmittedOnce(){
        try(var transport=new InMemoryTransport(new EventRecorder())){
            var ballot=new BallotNumber(1,"L1");var scoutExits=new AtomicInteger();var commanderExits=new AtomicInteger();
            var scout=new Scout(L1,ballot,Set.of(A1,A2,A3),2,transport,(type,b,s)->{if(type==WorkerEventType.SCOUT_EXITED)scoutExits.incrementAndGet();});
            var commander=new Commander(L1,new PValue(ballot,1,new NoOp(UUID.randomUUID())),Set.of(A1,A2,A3),Set.of(R1),2,transport,(type,b,s)->{if(type==WorkerEventType.COMMANDER_EXITED)commanderExits.incrementAndGet();});
            scout.kill();scout.kill();commander.kill();commander.kill();assertEquals(1,scoutExits.get());assertEquals(1,commanderExits.get());
            var registry=new WorkerRegistry();registry.register(scout);registry.register(commander);assertTrue(registry.killScout(L1,ballot));assertTrue(registry.killScout(L1,ballot));assertTrue(registry.killCommander(L1,ballot,1));assertTrue(registry.killCommander(L1,ballot,1));assertTrue(registry.runningWorkers().isEmpty());
        }
    }

    @Test void aNewWorkerMayReuseAFormerlyTerminalIdentity(){try(var transport=new InMemoryTransport(new EventRecorder())){var registry=new WorkerRegistry();var ballot=new BallotNumber(1,"L1");var first=new Scout(L1,ballot,Set.of(A1,A2,A3),2,transport,WorkerHook.NOOP);registry.register(first);assertTrue(registry.killScout(L1,ballot));registry.unregisterScout(L1,ballot);var retry=new Scout(L1,ballot,Set.of(A1,A2,A3),2,transport,WorkerHook.NOOP);registry.register(retry);assertFalse(registry.runningWorkers().isEmpty());assertTrue(registry.killScout(L1,ballot));assertTrue(retry.isKilled());}}

    private static boolean sent(EventRecorder events,Class<?> type){return events.snapshot().stream().anyMatch(e->e.eventType()==EventType.MESSAGE_SENT&&e.detail().equals(type.getSimpleName()));}
    private static final class GateFactory implements WorkerFactory {
        @Override public Scout createScout(NodeId l,BallotNumber b,Set<NodeId>a,int q,Transport t,WorkerHook h){return new GateScout(l,b,a,q,t,h);}
        @Override public Commander createCommander(NodeId l,PValue p,Set<NodeId>a,Set<NodeId>r,int q,Transport t,WorkerHook h){return new GateCommander(l,p,a,r,q,t,h);}
    }
    private static final class GateScout extends Scout {
        GateScout(NodeId l,BallotNumber b,Set<NodeId>a,int q,Transport t,WorkerHook h){super(l,b,a,q,t,h);}
        @Override public void start(){emit(WorkerEventType.P1A_BEFORE_SEND);if(!isKilled())acceptors.forEach(a->transport.send(MessageEnvelope.of(leader,a,new P1aMessage(ballot))));}
    }
    private static final class GateCommander extends Commander {
        GateCommander(NodeId l,PValue p,Set<NodeId>a,Set<NodeId>r,int q,Transport t,WorkerHook h){super(l,p,a,r,q,t,h);}
        @Override public void start(){emit(WorkerEventType.P2A_BEFORE_SEND);if(!isKilled())acceptors.forEach(a->transport.send(MessageEnvelope.of(leader,a,new P2aMessage(pvalue))));}
    }
}
