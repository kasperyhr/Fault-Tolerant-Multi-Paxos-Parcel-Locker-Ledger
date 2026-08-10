package paxoslocker.protocol;

import paxoslocker.model.*;

import java.util.Set;
import java.util.Objects;

/** Phase-1 response: requestedBallot identifies the Scout; acceptorBallot reports current state. */
public record P1bMessage(NodeId acceptor, BallotNumber requestedBallot,
                         BallotNumber acceptorBallot, Set<PValue> accepted) implements ProtocolMessage {
    public P1bMessage {
        Objects.requireNonNull(acceptor, "acceptor");
        Objects.requireNonNull(requestedBallot, "requestedBallot");
        Objects.requireNonNull(acceptorBallot, "acceptorBallot");
        accepted = Set.copyOf(Objects.requireNonNull(accepted, "accepted"));
    }
}
