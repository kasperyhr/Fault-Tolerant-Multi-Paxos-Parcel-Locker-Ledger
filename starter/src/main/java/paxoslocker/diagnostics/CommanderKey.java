package paxoslocker.diagnostics;

import paxoslocker.model.BallotNumber;
import java.io.Serializable;
import java.util.Objects;

public record CommanderKey(BallotNumber ballot, long slot) implements Serializable {
    public CommanderKey {
        Objects.requireNonNull(ballot, "ballot");
        if (slot < 1) throw new IllegalArgumentException("slot must be positive");
    }
}
