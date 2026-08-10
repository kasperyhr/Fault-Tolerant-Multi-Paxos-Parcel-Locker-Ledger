package paxoslocker.model;

import java.util.Objects;
import java.util.UUID;

public record PickupPackage(UUID requestId, String clientId, String packageId, String lockerId) implements Command {
    public PickupPackage {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(clientId);
        Objects.requireNonNull(packageId);
        Objects.requireNonNull(lockerId);
    }
}
