package paxoslocker.leader;

import paxoslocker.model.BallotNumber;

import java.util.Objects;

public record LeaderHeartbeatState(boolean active, long lastSeen, BallotNumber ballot) {
    public LeaderHeartbeatState {
        Objects.requireNonNull(ballot);
    }
}
