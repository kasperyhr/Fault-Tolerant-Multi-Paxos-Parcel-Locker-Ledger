package paxoslocker.model;

import java.io.Serializable;
import java.util.Objects;

public record BallotNumber(long round, String leaderId) implements Comparable<BallotNumber>, Serializable {
    public static final BallotNumber BOTTOM = new BallotNumber(-1, "");

    public BallotNumber {
        Objects.requireNonNull(leaderId);
    }

    @Override
    public int compareTo(BallotNumber other) {
        int byRound = Long.compare(round, other.round);
        return byRound != 0 ? byRound : leaderId.compareTo(other.leaderId);
    }

    public BallotNumber after(BallotNumber observed) {
        return new BallotNumber(Math.max(round, observed.round) + 1, leaderId);
    }
}
