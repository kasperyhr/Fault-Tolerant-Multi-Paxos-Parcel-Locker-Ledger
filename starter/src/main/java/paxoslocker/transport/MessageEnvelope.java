package paxoslocker.transport;

import paxoslocker.model.NodeId;
import paxoslocker.protocol.ProtocolMessage;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MessageEnvelope(UUID messageId, NodeId source, NodeId destination,
                              ProtocolMessage message, Instant createdAt) implements Serializable {
    public MessageEnvelope {
        Objects.requireNonNull(messageId);
        Objects.requireNonNull(source);
        Objects.requireNonNull(destination);
        Objects.requireNonNull(message);
        Objects.requireNonNull(createdAt);
    }

    public static MessageEnvelope of(NodeId source, NodeId destination, ProtocolMessage message) {
        return new MessageEnvelope(UUID.randomUUID(), source, destination, message, Instant.now());
    }
}
