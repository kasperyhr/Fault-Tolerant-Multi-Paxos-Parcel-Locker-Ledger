package paxoslocker.protocol;

import paxoslocker.model.*;

import java.util.Set;

public record P1bMessage(NodeId acceptor, BallotNumber ballot, Set<PValue> accepted) implements ProtocolMessage {
    public P1bMessage {
        accepted = Set.copyOf(accepted);
    }
}
