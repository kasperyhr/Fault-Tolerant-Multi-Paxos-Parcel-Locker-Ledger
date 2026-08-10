package paxoslocker.protocol;

import paxoslocker.model.PValue;

public record P2aMessage(PValue pvalue) implements ProtocolMessage {
}
