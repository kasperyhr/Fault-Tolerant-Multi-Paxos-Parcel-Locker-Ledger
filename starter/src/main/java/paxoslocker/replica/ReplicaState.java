package paxoslocker.replica;

import paxoslocker.leader.LeaderHeartbeatState;
import paxoslocker.model.Command;
import paxoslocker.model.NodeId;

import java.io.Serializable;
import java.util.*;

class ReplicaState {
    private DurableReplicaState durableReplicaState;
    private final RuntimeReplicaState runtimeReplicaState;

    ReplicaState() {
        durableReplicaState = new DurableReplicaState();
        runtimeReplicaState = new RuntimeReplicaState();
    }

    void storeDurableReplicaState(DurableReplicaState durableReplicaState) {
        this.durableReplicaState = durableReplicaState;
    }

    void addDecision(long slot, Command command) {
        durableReplicaState.decisions.put(slot, command);
    }

    void addProposal(long slot, Command command) {
        durableReplicaState.pendingProposals.put(slot, command);
    }

    Command removeProposal(long slot) {
        return durableReplicaState.pendingProposals.remove(slot);
    }

    private long updateSlotIn() {
        long lastKeyDecision = durableReplicaState.decisions.isEmpty() ?
                1L : durableReplicaState.decisions.lastKey();
        long lastKeyProposal = durableReplicaState.pendingProposals.isEmpty() ?
                1L : durableReplicaState.pendingProposals.lastKey();
        return runtimeReplicaState.slotIn = Math.max(runtimeReplicaState.slotIn,
                Math.max(lastKeyDecision, lastKeyProposal));
    }

    void updateSlotOut() {
        runtimeReplicaState.slotOut += 1;
    }

    void leaderId(NodeId leaderId) {
        runtimeReplicaState.knownLeader = leaderId;
    }

    void addExecutedRequestId(UUID executedRequestId, Command cmd) {
        runtimeReplicaState.executedRequestIds.put(executedRequestId, cmd);
    }

    void updateLeaderHeartbeatState(NodeId id, LeaderHeartbeatState leaderHeartbeatState) {
        runtimeReplicaState.leaderHeartbeatStates.put(id, leaderHeartbeatState);
    }

    DurableReplicaState getDurableReplicaState() {
        return new DurableReplicaState(durableReplicaState);
    }

    Map<Long, Command> decisions() {
        return Map.copyOf(durableReplicaState.decisions);
    }

    Map<Long, Command> decisions(long fromSlotInclusive, int maxEntries) {
        NavigableMap<Long, Command> tailMap = durableReplicaState.decisions.tailMap(fromSlotInclusive, true);
        if (tailMap.isEmpty()) return tailMap;
        Long endKey = tailMap.keySet().stream().limit(maxEntries).reduce((a, b) -> b).orElse(null);
        return tailMap.headMap(endKey, true);
    }

    Map<Long, Command> pendingProposals() {
        return Map.copyOf(durableReplicaState.pendingProposals);
    }

    long nextExecutionSlot() { // next execution slot
        return runtimeReplicaState.slotOut;
    }

    long nextProposalSlotAndUpdate() {
        long currentSlot = updateSlotIn();
        runtimeReplicaState.slotIn++;
        return currentSlot;
    }

    NodeId knownLeader() {
        return runtimeReplicaState.knownLeader;
    }

    Map<UUID, Command> executedRequestIds() {
        return Map.copyOf(runtimeReplicaState.executedRequestIds);
    }

    Map<NodeId, LeaderHeartbeatState> leaderHeartbeatStates() {
        return runtimeReplicaState.leaderHeartbeatStates;
    }

    static class DurableReplicaState implements Serializable {
        TreeMap<Long, Command> decisions;
        TreeMap<Long, Command> pendingProposals;

        private DurableReplicaState() {
            decisions = new TreeMap<>();
            pendingProposals = new TreeMap<>();
        }

        private DurableReplicaState(DurableReplicaState durableReplicaState) {
            decisions = new TreeMap<>(durableReplicaState.decisions);
            pendingProposals = new TreeMap<>(durableReplicaState.pendingProposals);
        }
    }

    private static class RuntimeReplicaState {
        long slotIn; // next proposal slot；
        long slotOut; // next execution slot
        NodeId knownLeader;
        Map<UUID, Command> executedRequestIds;
        Map<NodeId, LeaderHeartbeatState>  leaderHeartbeatStates;

        private RuntimeReplicaState() {
            slotIn = 1L;
            slotOut = 1L;
            executedRequestIds = new HashMap<>();
            leaderHeartbeatStates = new HashMap<>();
        }
    }
}
