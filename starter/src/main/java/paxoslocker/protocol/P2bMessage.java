package paxoslocker.protocol;

import paxoslocker.model.*;
import java.util.Objects;

/** Phase-2 response: requestedBallot+slot identify the Commander; acceptorBallot reports current state. */
public record P2bMessage(NodeId acceptor, BallotNumber requestedBallot,
                         BallotNumber acceptorBallot, long slot) implements ProtocolMessage {
    public P2bMessage {
        Objects.requireNonNull(acceptor, "acceptor");
        Objects.requireNonNull(requestedBallot, "requestedBallot");
        Objects.requireNonNull(acceptorBallot, "acceptorBallot");
        if (slot < 1) throw new IllegalArgumentException("slot must be positive");
    }
}
