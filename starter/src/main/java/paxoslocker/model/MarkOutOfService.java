package paxoslocker.model;

import java.util.Objects;
import java.util.UUID;

public record MarkOutOfService(UUID requestId, String operatorId, String lockerId) implements Command {
    public MarkOutOfService {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(operatorId);
        Objects.requireNonNull(lockerId);
    }
}
