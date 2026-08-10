package paxoslocker.protocol;

import paxoslocker.model.*;

import java.util.Set;

public record AdoptedMessage(BallotNumber ballot, Set<PValue> accepted) implements ProtocolMessage {
    public AdoptedMessage {
        accepted = Set.copyOf(accepted);
    }
}
