package paxoslocker.protocol;

import paxoslocker.model.*;

public record P2bMessage(NodeId acceptor, BallotNumber ballot, long slot) implements ProtocolMessage {
}
