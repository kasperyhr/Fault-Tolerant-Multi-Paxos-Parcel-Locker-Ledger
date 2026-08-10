package paxoslocker.diagnostics;

import paxoslocker.model.*;

import java.util.*;

public record AcceptorStatus(BallotNumber ballot, Set<PValue> accepted) {
    public AcceptorStatus {
        accepted = Set.copyOf(accepted);
    }
}
