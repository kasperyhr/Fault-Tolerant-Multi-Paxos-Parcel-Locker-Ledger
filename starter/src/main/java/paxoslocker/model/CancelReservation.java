package paxoslocker.model;

import java.util.Objects;
import java.util.UUID;

public record CancelReservation(UUID requestId, String clientId, String lockerId) implements Command {
    public CancelReservation {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(clientId);
        Objects.requireNonNull(lockerId);
    }
}
