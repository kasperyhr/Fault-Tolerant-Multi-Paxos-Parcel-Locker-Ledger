package paxoslocker.model;

import java.util.Objects;
import java.util.UUID;

public record StorePackage(UUID requestId, String courierId, String packageId, String lockerId) implements Command {
    public StorePackage {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(courierId);
        Objects.requireNonNull(packageId);
        Objects.requireNonNull(lockerId);
    }
}
