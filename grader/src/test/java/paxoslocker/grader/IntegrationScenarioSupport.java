package paxoslocker.grader;

import org.junit.jupiter.api.Assertions;
import paxoslocker.diagnostics.WorkerEventType;
import paxoslocker.model.*;
import paxoslocker.protocol.*;
import paxoslocker.testkit.*;
import paxoslocker.transport.MessageEnvelope;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class IntegrationScenarioSupport {
    static final Duration WAIT=Duration.ofSeconds(8);
    static NoOp command(){return new NoOp(UUID.randomUUID());}
    static void run(ClusterConfig config,Consumer<ClusterHarness> scenario){try(ClusterHarness c=ClusterHarness.start(config)){try{scenario.accept(c);}catch(Throwable failure){throw new AssertionError(failure.getMessage()+"\n"+c.diagnostics(),failure);}}}
    static void stableCommand(ClusterHarness c){c.awaitLeader(WAIT);Command command=command();c.submit(command);Command decided=c.awaitDecision(1,WAIT);c.awaitExecutedThrough(1,WAIT);c.awaitReplicaConvergence(WAIT);Assertions.assertEquals(command.requestId(),decided.requestId());}
    static void sequential(int count){run(ClusterConfig.small(),c->{c.awaitLeader(WAIT);for(int i=0;i<count;i++)c.submit(command());c.awaitDecision(count,WAIT);c.awaitExecutedThrough(count,WAIT);c.awaitReplicaConvergence(WAIT);});}
    static void concurrent(int clients){run(ClusterConfig.small(),c->{c.awaitLeader(WAIT);try(var executor=Executors.newVirtualThreadPerTaskExecutor()){List<Future<?>> futures=new ArrayList<>();for(int i=0;i<clients;i++)futures.add(executor.submit(()->c.submit(command())));futures.forEach(f->{try{f.get();}catch(Exception e){throw new RuntimeException(e);}});}c.awaitDecision(clients,WAIT);c.awaitExecutedThrough(clients,WAIT);c.awaitReplicaConvergence(WAIT);});}
    static void lockerConflict(){run(ClusterConfig.small(),c->{c.awaitLeader(WAIT);c.submit(new ReserveLocker(UUID.randomUUID(),"one","locker-1"));c.submit(new ReserveLocker(UUID.randomUUID(),"two","locker-1"));c.awaitDecision(2,WAIT);c.awaitExecutedThrough(2,WAIT);c.awaitReplicaConvergence(WAIT);c.replicaIds().forEach(r->Assertions.assertEquals(LockerStatus.RESERVED,c.inspectReplica(r).applicationState().get("locker-1").status()));});}
    static void competingProposals(){run(ClusterConfig.small(),c->{c.awaitLeader(WAIT);List<NodeId> r=c.replicaIds().stream().toList();Command a=command(),b=command();try(var ex=Executors.newVirtualThreadPerTaskExecutor()){ex.submit(()->c.submitTo(r.get(0),a));ex.submit(()->c.submitTo(r.get(1),b));}c.awaitDecision(2,WAIT);c.awaitExecutedThrough(2,WAIT);c.awaitReplicaConvergence(WAIT);Set<UUID> ids=new HashSet<>();c.inspectReplica(r.getFirst()).decisions().values().forEach(x->ids.add(x.requestId()));Assertions.assertTrue(ids.containsAll(Set.of(a.requestId(),b.requestId())));});}
    static void outOfOrderDecision(){run(ClusterConfig.small(),c->{NodeId replica=c.replicaIds().iterator().next(),leader=c.leaderIds().iterator().next();Command one=command(),two=command();c.sendForTest(MessageEnvelope.of(leader,replica,new DecisionMessage(2,two)));Assertions.assertEquals(0,c.inspectReplica(replica).lastExecutedSlot());c.sendForTest(MessageEnvelope.of(leader,replica,new DecisionMessage(1,one)));Await.until(()->c.inspectReplica(replica).lastExecutedSlot()>=2,WAIT,"ordered execution after hole fill");});}
    static void leaderScenario(String kind){run(ClusterConfig.small(),c->{
        if(kind.equals("beforeScout")){
            NodeId victim=c.leaderIds().iterator().next();c.crashLeader(victim);long marker=c.events().snapshot().stream().mapToLong(Event::sequenceNumber).max().orElse(0);
            c.workerEvents().onNextMatching(victim,WorkerKind.SCOUT,WorkerEventType.SCOUT_CREATED,e->{c.failures().killScout(victim,e.ballot());c.crashLeader(victim);});
            c.restartLeader(victim);c.awaitNextWorkerEvent(victim,WorkerKind.SCOUT,WorkerEventType.SCOUT_CREATED,WAIT);
            Assertions.assertFalse(c.isRunning(victim));Assertions.assertFalse(messageSentAfter(c,victim,P1aMessage.class,marker));stableCommand(c);return;
        }
        NodeId old=c.awaitLeader(WAIT);Command x=command();
        if(kind.equals("afterAdopted")){
            Assertions.assertTrue(c.inspectLeader(old).active());
            Assertions.assertTrue(c.events().snapshot().stream().anyMatch(e->e.nodeId().equals(old)&&e.eventType()==EventType.WORKER_EVENT&&e.detail().equals(WorkerEventType.ADOPTED_BEFORE_SEND.name())));
        }else if(kind.equals("duringCommander")){
            c.submit(x);Await.until(()->!c.inspectLeader(old).runningCommanders().isEmpty(),WAIT,"Commander before leader crash");
        }else if(kind.equals("afterChosen")){
            c.workerEvents().onNextMatching(old,WorkerKind.COMMANDER,WorkerEventType.COMMANDER_CREATED,created->c.workerEvents().killOnNext(old,created.ballot(),created.slot(),WorkerEventType.DECISION_BEFORE_SEND,()->c.crashLeader(old)));
            c.submit(x);var event=c.awaitNextWorkerEvent(old,WorkerKind.COMMANDER,WorkerEventType.DECISION_BEFORE_SEND,WAIT);
            Assertions.assertTrue(c.safety().isQuorumChosen(event.slot(),x));
        }else if(kind.equals("oldReturns")){
            c.submit(x);c.awaitDecision(1,WAIT);
        }
        if(c.isRunning(old))c.crashLeader(old);NodeId next=c.awaitLeader(WAIT);Assertions.assertNotEquals(old,next);c.restartLeader(old);
        if(!kind.equals("duringCommander")&&!kind.equals("afterChosen")&&!kind.equals("oldReturns"))c.submit(x);
        c.awaitDecision(1,WAIT);c.awaitExecutedThrough(1,WAIT);c.awaitReplicaConvergence(WAIT);
    });}
    static void leadersCompete(int count){run(new ClusterConfig(1,3,3,count,WAIT,123456),c->{c.leaderIds().forEach(id->c.restartLeader(id));stableCommand(c);});}
    static void acceptorsDown(int f,int n,int down,boolean expectProgress){ClusterConfig cfg=new ClusterConfig(f,n,3,3,WAIT,123456);run(cfg,c->{c.awaitLeader(WAIT);c.acceptorIds().stream().limit(down).forEach(c::crashAcceptor);Command x=command();c.submit(x);if(expectProgress){c.awaitDecision(1,WAIT);}else{Assertions.assertThrows(AssertionError.class,()->c.awaitDecision(1,Duration.ofMillis(500)));NodeId restore=c.acceptorIds().stream().filter(id->!c.isRunning(id)).findFirst().orElseThrow();c.restartAcceptor(restore);c.awaitDecision(1,WAIT);}c.awaitExecutedThrough(1,WAIT);});}
    static void acceptorPersistence(boolean repeated){run(ClusterConfig.small(),c->{c.awaitLeader(WAIT);c.submit(command());c.awaitDecision(1,WAIT);NodeId a=c.acceptorIds().iterator().next();int cycles=repeated?10:1;for(int i=0;i<cycles;i++){var before=c.inspectAcceptor(a);c.crashAcceptor(a);c.restartAcceptor(a);var after=c.inspectAcceptor(a);Assertions.assertTrue(after.ballot().compareTo(before.ballot())>=0);Assertions.assertTrue(after.accepted().containsAll(before.accepted()));}});}
    static void replicaRecovery(boolean gap,boolean multiple){run(ClusterConfig.small(),c->{c.awaitLeader(WAIT);List<NodeId> targets=c.replicaIds().stream().limit(multiple?2:1).toList();targets.forEach(c::crashReplica);for(int i=0;i<5;i++)c.submit(command());c.awaitDecision(5,WAIT);targets.forEach(c::restartReplica);c.awaitExecutedThrough(5,WAIT);c.awaitReplicaConvergence(WAIT);if(gap)targets.forEach(r->Assertions.assertEquals(5,c.inspectReplica(r).lastExecutedSlot()));});}
    static void workerCrash(boolean scout,WorkerEventType event){run(ClusterConfig.small(),c->{
        NodeId leader=scout?c.leaderIds().iterator().next():c.awaitLeader(WAIT);long marker=c.events().snapshot().stream().mapToLong(Event::sequenceNumber).max().orElse(0);
        Command original=command();WorkerEventProbe.ObservedWorkerEvent observed;
        if(scout){c.crashLeader(leader);c.armKillNextScoutAt(leader,event);c.restartLeader(leader);observed=c.awaitNextWorkerEvent(leader,WorkerKind.SCOUT,event,WAIT);Assertions.assertTrue(c.workerRegistry().scout(leader,observed.ballot()).isEmpty());}
        else{c.armKillNextCommanderAt(leader,event);c.submit(original);observed=c.awaitNextWorkerEvent(leader,WorkerKind.COMMANDER,event,WAIT);Assertions.assertTrue(c.workerRegistry().commander(leader,observed.ballot(),observed.slot()).isEmpty());if(event==WorkerEventType.DECISION_BEFORE_SEND||event==WorkerEventType.DECISION_AFTER_SEND)Assertions.assertTrue(c.safety().isQuorumChosen(observed.slot(),original));if(event==WorkerEventType.DECISION_AFTER_SEND){long learned=c.safety().learnedSnapshot().values().stream().filter(slots->slots.containsKey(observed.slot())).count();Assertions.assertTrue(learned>=1&&learned<c.replicaIds().size(),"Commander must die after partial, not complete, dissemination");}}
        if(event==WorkerEventType.P1A_BEFORE_SEND)Assertions.assertFalse(messageSentAfter(c,leader,P1aMessage.class,marker));
        if(event==WorkerEventType.P2A_BEFORE_SEND)Assertions.assertFalse(messageSentAfter(c,leader,P2aMessage.class,marker));
        c.submit(command());c.awaitDecision(1,WAIT);c.awaitExecutedThrough(1,WAIT);c.awaitReplicaConvergence(WAIT);
    });}

    static void commanderAndLeaderCrashAfterChosen(){run(ClusterConfig.small(),c->{
        NodeId leader=c.awaitLeader(WAIT);Command chosen=command();
        c.workerEvents().onNextMatching(leader,WorkerKind.COMMANDER,WorkerEventType.COMMANDER_CREATED,created->c.workerEvents().killOnNext(leader,created.ballot(),created.slot(),WorkerEventType.DECISION_AFTER_SEND,()->{c.failures().killCommander(leader,created.ballot(),created.slot());c.crashLeader(leader);}));
        c.submit(chosen);var event=c.awaitNextWorkerEvent(leader,WorkerKind.COMMANDER,WorkerEventType.DECISION_AFTER_SEND,WAIT);
        Assertions.assertTrue(c.safety().isQuorumChosen(event.slot(),chosen));NodeId replacement=c.awaitLeader(WAIT);Assertions.assertNotEquals(leader,replacement);
        Command learned=c.awaitDecision(event.slot(),WAIT);Assertions.assertEquals(chosen,learned);c.awaitExecutedThrough(event.slot(),WAIT);c.awaitReplicaConvergence(WAIT);
    });}

    static void oldLeaderReturns(){run(ClusterConfig.small(),c->{
        NodeId old=c.awaitLeader(WAIT);BallotNumber oldBallot=c.inspectLeader(old).ballot();Command first=command();c.submit(first);c.awaitDecision(1,WAIT);c.crashLeader(old);
        NodeId replacement=c.awaitLeader(WAIT);Assertions.assertNotEquals(old,replacement);Command second=command();c.submit(second);c.awaitDecision(2,WAIT);BallotNumber higher=c.inspectLeader(replacement).ballot();Assertions.assertTrue(higher.compareTo(oldBallot)>0);
        c.restartLeader(old);Command conflicting=command();for(NodeId acceptor:c.acceptorIds()){c.sendForTest(MessageEnvelope.of(old,acceptor,new P1aMessage(oldBallot)));c.sendForTest(MessageEnvelope.of(old,acceptor,new P2aMessage(new PValue(oldBallot,1,conflicting))));}
        c.sendForTest(MessageEnvelope.of(old,replacement,new HeartbeatMessage(oldBallot,true)));NodeId acceptor=c.acceptorIds().iterator().next();c.sendForTest(MessageEnvelope.of(acceptor,old,new P2bMessage(acceptor,oldBallot,higher,1)));
        Assertions.assertEquals(first,c.safety().chosenSnapshot().get(1L));c.awaitExecutedThrough(2,WAIT);c.awaitReplicaConvergence(WAIT);
    });}

    private static boolean messageSentAfter(ClusterHarness c,NodeId source,Class<?> messageType,long sequence){return c.events().snapshot().stream().anyMatch(e->e.sequenceNumber()>sequence&&e.nodeId().equals(source)&&e.eventType()==EventType.MESSAGE_SENT&&e.detail().equals(messageType.getSimpleName()));}
    static void network(String kind){run(ClusterConfig.small(),c->{NodeId leader=c.awaitLeader(WAIT);List<NodeId> acceptors=c.acceptorIds().stream().toList(),replicas=c.replicaIds().stream().toList();switch(kind){case"delay"->c.failures().delayNext(MessagePredicate.any(),Duration.ofMillis(50));case"duplicate"->c.failures().duplicateNext(MessagePredicate.any());case"drop"->c.failures().dropNext(MessagePredicate.any());case"reorder"->c.failures().reorderNext(MessagePredicate.any());case"leaderPartition"->c.partition(Set.of(leader),Set.copyOf(acceptors));case"replicaPartition"->c.partition(Set.of(replicas.getFirst()),Set.copyOf(c.membership().leaders()));case"minorityPartition"->c.partition(Set.of(acceptors.getFirst()),Set.copyOf(c.membership().leaders()));case"heal"->{c.partition(leader,acceptors.getFirst());c.heal(leader,acceptors.getFirst());}case"staleLeader"->{c.failures().delayNext(MessagePredicate.any(),Duration.ofMillis(100));c.crashLeader(leader);c.awaitLeader(WAIT);}case"staleCommander"->c.failures().delayNext(MessagePredicate.messageType(paxoslocker.protocol.P2aMessage.class),Duration.ofMillis(100));default->throw new IllegalArgumentException(kind);}c.submit(command());c.healAll();c.awaitDecision(1,WAIT);c.awaitExecutedThrough(1,WAIT);c.awaitReplicaConvergence(WAIT);});}
    static void combined(String kind){run(ClusterConfig.small(),c->{NodeId leader=c.awaitLeader(WAIT),acceptor=c.acceptorIds().iterator().next(),replica=c.replicaIds().iterator().next();switch(kind){case"leaderAcceptor"->{c.crashAcceptor(acceptor);c.crashLeader(leader);}case"commanderReplica"->{c.submit(command());Await.until(()->!c.inspectLeader(leader).runningCommanders().isEmpty(),WAIT,"Commander");var key=c.inspectLeader(leader).runningCommanders().iterator().next();c.partition(replica,leader);c.failures().killCommander(leader,key.ballot(),key.slot());}case"scoutStale"->{var ballot=c.inspectLeader(leader).runningScoutBallot().orElseThrow();c.failures().delayNext(MessagePredicate.any(),Duration.ofMillis(100));c.failures().killScout(leader,ballot);}case"oldCommander"->{c.submit(command());Await.until(()->!c.inspectLeader(leader).runningCommanders().isEmpty(),WAIT,"Commander");c.crashLeader(leader);}case"acceptorDuplicate"->{c.crashAcceptor(acceptor);c.restartAcceptor(acceptor);c.failures().duplicateNext(MessagePredicate.any());}case"replicaTraffic"->{c.crashReplica(replica);c.submit(command());c.restartReplica(replica);}case"twoLeaders"->{c.crashAcceptor(acceptor);c.leaderIds().forEach(c::restartLeader);}case"partitionFailover"->{c.partition(Set.of(leader),c.acceptorIds());c.awaitLeader(WAIT);c.healAll();}default->throw new IllegalArgumentException(kind);}c.healAll();if(!c.isRunning(acceptor))c.restartAcceptor(acceptor);if(!c.isRunning(leader))c.restartLeader(leader);if(!c.isRunning(replica))c.restartReplica(replica);c.submit(command());c.awaitDecision(1,WAIT);c.awaitExecutedThrough(1,WAIT);c.awaitReplicaConvergence(WAIT);});}
    static void localTcp(){run(ClusterConfig.localTcpSmall(),IntegrationScenarioSupport::stableCommand);}
    private IntegrationScenarioSupport(){}
}
