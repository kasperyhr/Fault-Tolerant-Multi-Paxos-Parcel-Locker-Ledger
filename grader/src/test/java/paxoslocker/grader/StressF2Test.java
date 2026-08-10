package paxoslocker.grader;

import org.junit.jupiter.api.*;
import paxoslocker.diagnostics.CommanderKey;
import paxoslocker.model.*;
import paxoslocker.testkit.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Tag("stress")
class StressF2Test {
    @Test void tenThousandRequestsWithF2AndFiftyClients() {
        long seed=Long.parseLong(System.getProperty("paxos.seed","123456"));
        try(var c=ClusterHarness.start(new ClusterConfig(2,5,3,3,Duration.ofMinutes(5),seed))) {
            try {
                NodeId firstLeader=c.awaitLeader(Duration.ofSeconds(20));
                c.crashLeader(firstLeader); c.awaitLeader(Duration.ofSeconds(20)); c.restartLeader(firstLeader);
                try(var clients=Executors.newVirtualThreadPerTaskExecutor()) {
                    List<Future<?>> jobs=new ArrayList<>();
                    for(int client=0;client<50;client++) jobs.add(clients.submit(()->{for(int i=0;i<200;i++)c.submit(new NoOp(UUID.randomUUID()));}));
                    c.failures().delayNext(MessagePredicate.any(),Duration.ofMillis(25));
                    c.failures().duplicateNext(MessagePredicate.any());
                    c.failures().dropNext(MessagePredicate.any());
                    tryKillCommander(c);
                    jobs.forEach(f->{try{f.get();}catch(Exception e){throw new RuntimeException(e);}});
                }
                NodeId a=c.acceptorIds().iterator().next();c.crashAcceptor(a);c.restartAcceptor(a);
                NodeId r=c.replicaIds().iterator().next();c.crashReplica(r);c.restartReplica(r);
                c.healAll();c.awaitDecision(10_000,Duration.ofMinutes(5));c.awaitExecutedThrough(10_000,Duration.ofMinutes(5));c.awaitReplicaConvergence(Duration.ofMinutes(2));
            } catch(Throwable failure) { throw new AssertionError("seed="+seed+"\n"+c.diagnostics(),failure); }
        }
    }
    private static void tryKillCommander(ClusterHarness c){
        try { Await.until(()->c.leaderIds().stream().anyMatch(id->!c.inspectLeader(id).runningCommanders().isEmpty()),Duration.ofSeconds(2),"Commander for stress kill"); }
        catch(AssertionError ignored){return;}
        for(NodeId leader:c.leaderIds()) for(CommanderKey key:c.inspectLeader(leader).runningCommanders()) { c.failures().killCommander(leader,key.ballot(),key.slot()); return; }
    }
}
