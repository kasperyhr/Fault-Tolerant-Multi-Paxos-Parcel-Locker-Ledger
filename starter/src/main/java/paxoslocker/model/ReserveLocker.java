package paxoslocker.model;

import java.util.Objects;
import java.util.UUID;

public record ReserveLocker(UUID requestId, String clientId, String lockerId) implements Command {
    public ReserveLocker {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(clientId);
        Objects.requireNonNull(lockerId);
    }
}
