package paxoslocker.model;

import java.util.Objects;
import java.util.UUID;

public record NoOp(UUID requestId) implements Command {
    public NoOp {
        Objects.requireNonNull(requestId);
    }
}
