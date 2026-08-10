package paxoslocker.protocol;

import paxoslocker.model.NodeId;
import java.util.Objects;

public record DecisionSyncRequestMessage(NodeId requester, long fromSlotInclusive, int maxEntries)
        implements ProtocolMessage {
    public DecisionSyncRequestMessage {
        Objects.requireNonNull(requester, "requester");
        if (fromSlotInclusive < 1) throw new IllegalArgumentException("fromSlotInclusive must be positive");
        if (maxEntries < 1 || maxEntries > 100_000) throw new IllegalArgumentException("invalid maxEntries");
    }
}
