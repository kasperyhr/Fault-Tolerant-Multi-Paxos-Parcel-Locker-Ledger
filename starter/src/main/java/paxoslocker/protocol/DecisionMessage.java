package paxoslocker.protocol;

import paxoslocker.model.Command;

public record DecisionMessage(long slot, Command command) implements ProtocolMessage {
}
