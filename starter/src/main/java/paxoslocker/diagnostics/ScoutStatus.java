package paxoslocker.diagnostics;

import paxoslocker.model.*;

import java.util.*;

public record ScoutStatus(BallotNumber ballot, Set<NodeId> responses, boolean running) {
    public ScoutStatus {
        responses = Set.copyOf(responses);
    }
}
