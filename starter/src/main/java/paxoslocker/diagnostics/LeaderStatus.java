package paxoslocker.diagnostics;

import paxoslocker.model.*;

import java.util.*;

public record LeaderStatus(BallotNumber ballot, boolean active, Map<Long, Command> proposals, boolean runningScout,
                           Set<Long> runningCommanders) {
    public LeaderStatus {
        proposals = Map.copyOf(proposals);
        runningCommanders = Set.copyOf(runningCommanders);
    }
}
