package paxoslocker.protocol;

import paxoslocker.model.Command;

public record ProposeMessage(long slot, Command command) implements ProtocolMessage {
}
