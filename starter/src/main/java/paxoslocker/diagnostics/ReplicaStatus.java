package paxoslocker.diagnostics;

import paxoslocker.model.*;

import java.util.*;

public record ReplicaStatus(long lastExecutedSlot, Map<Long, Command> decisions, Map<String, Locker> applicationState,
                            NodeId knownLeader, Map<Long, Command> pendingProposals) {
    public ReplicaStatus {
        decisions = Map.copyOf(decisions);
        applicationState = Map.copyOf(applicationState);
        pendingProposals = Map.copyOf(pendingProposals);
    }
}
