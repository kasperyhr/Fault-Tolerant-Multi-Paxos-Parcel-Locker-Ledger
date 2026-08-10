package paxoslocker.model;

import java.util.Objects;
import java.util.UUID;

public record RestoreLocker(UUID requestId, String operatorId, String lockerId) implements Command {
    public RestoreLocker {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(operatorId);
        Objects.requireNonNull(lockerId);
    }
}
