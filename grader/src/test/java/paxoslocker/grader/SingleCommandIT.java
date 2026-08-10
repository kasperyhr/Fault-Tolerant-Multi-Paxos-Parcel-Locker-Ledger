package paxoslocker.grader;

import org.junit.jupiter.api.*;
import paxoslocker.model.*;
import paxoslocker.testkit.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class SingleCommandIT {
    @Test void decidesExecutesAndConverges() {
        try (ClusterHarness cluster = ClusterHarness.start(ClusterConfig.small())) {
            cluster.awaitLeader(Duration.ofSeconds(5));
            ReserveLocker command = new ReserveLocker(UUID.randomUUID(), "client-1", "locker-1");
            cluster.submit(command);
            Command decided = cluster.awaitDecision(1, Duration.ofSeconds(5));
            cluster.awaitExecutedThrough(1, Duration.ofSeconds(5));
            cluster.awaitReplicaConvergence(Duration.ofSeconds(5));
            assertEquals(command.requestId(), decided.requestId());
            for (NodeId replica : cluster.replicaIds()) {
                var status = cluster.inspectReplica(replica);
                assertEquals(decided, status.decisions().get(1L));
                assertEquals(LockerStatus.RESERVED, status.applicationState().get("locker-1").status());
            }
            assertEquals(decided, cluster.safety().chosenSnapshot().get(1L),
                    "student implementation must emit VALUE_CHOSEN diagnostics");
        }
    }
}
