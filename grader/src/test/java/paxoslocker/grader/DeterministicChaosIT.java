package paxoslocker.grader;

import org.junit.jupiter.api.*;
import paxoslocker.diagnostics.CommanderKey;
import paxoslocker.model.*;
import paxoslocker.testkit.*;
import java.time.Duration;
import java.util.*;

@Tag("chaos")
class DeterministicChaosIT {
    @Test void seededFailuresThenStabilizationConverges() {
        long seed=Long.parseLong(System.getProperty("paxos.seed","123456"));
        System.out.println("DeterministicChaosIT seed="+seed);
        ClusterConfig config=new ClusterConfig(1,3,3,3,Duration.ofSeconds(15),seed);
        try(ClusterHarness cluster=ClusterHarness.start(config)) {
            try {
                cluster.awaitLeader(Duration.ofSeconds(10));
                Random random=new Random(seed); List<Command> submitted=new ArrayList<>();
                for(int step=0;step<40;step++) {
                    int action=random.nextInt(12);
                    switch(action) {
                        case 0,1,2 -> {Command command=new NoOp(UUID.randomUUID());submitted.add(command);cluster.submit(command);}
                        case 3 -> toggle(cluster, pick(cluster.leaderIds(),random), true);
                        case 4 -> toggle(cluster, pick(cluster.acceptorIds(),random), false);
                        case 5 -> toggleReplica(cluster,pick(cluster.replicaIds(),random));
                        case 6 -> cluster.failures().dropNext(MessagePredicate.any());
                        case 7 -> cluster.failures().delayNext(MessagePredicate.any(),Duration.ofMillis(20+random.nextInt(50)));
                        case 8 -> cluster.failures().duplicateNext(MessagePredicate.any());
                        case 9 -> {NodeId a=pick(cluster.leaderIds(),random),b=pick(cluster.acceptorIds(),random);cluster.partition(a,b);if(random.nextBoolean())cluster.heal(a,b);}
                        case 10 -> killScoutIfPresent(cluster);
                        case 11 -> killCommanderIfPresent(cluster);
                    }
                }
                cluster.healAll();
                cluster.acceptorIds().forEach(id->{if(!cluster.isRunning(id))cluster.restartAcceptor(id);});
                cluster.leaderIds().forEach(id->{if(!cluster.isRunning(id))cluster.restartLeader(id);});
                cluster.replicaIds().forEach(id->{if(!cluster.isRunning(id))cluster.restartReplica(id);});
                Command finalCommand=new NoOp(UUID.randomUUID());cluster.submit(finalCommand);
                cluster.awaitReplicaConvergence(Duration.ofSeconds(15));
                Assertions.assertTrue(cluster.inspectReplica(cluster.replicaIds().iterator().next()).decisions().values().stream().anyMatch(c->c.requestId().equals(finalCommand.requestId())));
            } catch(Throwable failure) { throw new AssertionError("seed="+seed+"\n"+cluster.diagnostics(),failure); }
        }
    }
    private static <T>T pick(Set<T> values,Random r){List<T> list=values.stream().toList();return list.get(r.nextInt(list.size()));}
    private static void toggle(ClusterHarness c,NodeId id,boolean leader){if(c.isRunning(id)){if(leader)c.crashLeader(id);else c.crashAcceptor(id);}else{if(leader)c.restartLeader(id);else c.restartAcceptor(id);}}
    private static void toggleReplica(ClusterHarness c,NodeId id){if(c.isRunning(id))c.crashReplica(id);else c.restartReplica(id);}
    private static void killScoutIfPresent(ClusterHarness c){for(NodeId leader:c.leaderIds()){var status=c.inspectLeader(leader);status.runningScoutBallot().ifPresent(ballot->{try{c.failures().killScout(leader,ballot);}catch(IllegalArgumentException ignored){}});}}
    private static void killCommanderIfPresent(ClusterHarness c){for(NodeId leader:c.leaderIds()){for(CommanderKey key:c.inspectLeader(leader).runningCommanders()){try{c.failures().killCommander(leader,key.ballot(),key.slot());}catch(IllegalArgumentException ignored){}return;}}}
}
