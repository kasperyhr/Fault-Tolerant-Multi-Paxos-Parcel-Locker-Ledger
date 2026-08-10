package paxoslocker.protocol;

import java.io.Serializable;

public sealed interface ProtocolMessage extends Serializable permits ProposeMessage, DecisionMessage, P1aMessage, P1bMessage, P2aMessage, P2bMessage, AdoptedMessage, PreemptedMessage, HeartbeatMessage {
}
