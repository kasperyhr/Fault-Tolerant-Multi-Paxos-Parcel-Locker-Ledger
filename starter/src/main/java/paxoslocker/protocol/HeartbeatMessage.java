package paxoslocker.protocol;

import paxoslocker.model.BallotNumber;

public record HeartbeatMessage(BallotNumber ballot, boolean active) implements ProtocolMessage {
}
