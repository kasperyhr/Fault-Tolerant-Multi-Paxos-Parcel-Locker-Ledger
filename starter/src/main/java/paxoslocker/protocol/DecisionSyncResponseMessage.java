package paxoslocker.protocol;

import paxoslocker.model.Command;
import java.util.*;

public record DecisionSyncResponseMessage(long fromSlotInclusive, Map<Long, Command> decisions)
        implements ProtocolMessage {
    public DecisionSyncResponseMessage {
        if (fromSlotInclusive < 1) throw new IllegalArgumentException("fromSlotInclusive must be positive");
        decisions = Map.copyOf(Objects.requireNonNull(decisions, "decisions"));
        if (decisions.keySet().stream().anyMatch(slot -> slot < fromSlotInclusive))
            throw new IllegalArgumentException("response contains a slot before fromSlotInclusive");
    }
}
