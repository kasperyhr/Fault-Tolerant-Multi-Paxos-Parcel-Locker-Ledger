package paxoslocker.protocol;

import paxoslocker.model.BallotNumber;

public record PreemptedMessage(BallotNumber observedBallot) implements ProtocolMessage {
}
