package paxoslocker.leader;

import paxoslocker.model.BallotNumber;
import paxoslocker.model.Command;
import paxoslocker.model.NodeId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class LeaderState {
    private int retries;
    private boolean active;
    private BallotNumber ballot;
    private final Map<Long, Command> proposals;
    private final Map<NodeId, LeaderHeartbeatState> peerStates;

    LeaderState(NodeId id) {
        retries = 0;
        active = false;
        ballot = new BallotNumber(1, id.toString());
        proposals = new HashMap<>();
        peerStates = new HashMap<>();
    }

    boolean active() {
        return active;
    }

    BallotNumber ballot() {
        return ballot;
    }

    Map<Long, Command> proposals() {
        return Map.copyOf(proposals);
    }

    int retries() {
        return retries;
    }

    Optional<LeaderHeartbeatState> findHigherBallotPeer(BallotNumber ballot) {
        for (LeaderHeartbeatState leaderHeartbeatState : peerStates.values()) {
            if (leaderHeartbeatState.active() && leaderHeartbeatState.ballot().compareTo(ballot) > 0) {
                return Optional.of(leaderHeartbeatState);
            }
        }
        return Optional.empty();
    }

    void setActive(boolean active) {
        this.active = active;
    }

    void incrementRetries() {
        retries++;
    }

    void resetRetries() {
        retries = 0;
    }

    void ballotAfter(BallotNumber observed) {
        ballot = ballot.after(observed);
    }

    void addProposal(long slot, Command command) {
        proposals.put(slot, command);
    }

    void updateProposal(Map<Long, Command> pmax) {
        proposals.putAll(pmax);
    }

    void updatePeerState(NodeId nodeId, LeaderHeartbeatState leaderHeartbeatState) {
        peerStates.put(nodeId, leaderHeartbeatState);
    }
}
