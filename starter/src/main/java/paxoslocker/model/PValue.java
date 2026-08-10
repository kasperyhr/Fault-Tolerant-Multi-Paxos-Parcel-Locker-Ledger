package paxoslocker.model;

import java.io.Serializable;
import java.util.Objects;

public record PValue(BallotNumber ballot, long slot, Command command) implements Serializable {
    public PValue {
        Objects.requireNonNull(ballot);
        Objects.requireNonNull(command);
        if (slot < 1) throw new IllegalArgumentException("slot must be positive");
    }
}
