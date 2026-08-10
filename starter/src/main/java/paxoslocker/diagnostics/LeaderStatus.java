package paxoslocker.diagnostics;

import paxoslocker.model.*;

import java.util.*;

public record LeaderStatus(BallotNumber ballot, boolean active, Map<Long, Command> proposals,
                           Optional<BallotNumber> runningScoutBallot,
                           Set<CommanderKey> runningCommanders) {
    public LeaderStatus {
        proposals = Map.copyOf(proposals);
        runningScoutBallot = Objects.requireNonNull(runningScoutBallot);
        runningCommanders = Set.copyOf(runningCommanders);
    }
}
