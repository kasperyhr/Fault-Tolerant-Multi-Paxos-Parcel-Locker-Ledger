package paxoslocker.protocol;

import paxoslocker.model.BallotNumber;

public record P1aMessage(BallotNumber ballot) implements ProtocolMessage {
}
