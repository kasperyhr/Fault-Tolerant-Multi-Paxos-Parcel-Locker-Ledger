package paxoslocker.model;

import java.io.Serializable;
import java.util.UUID;

public record CommandResult(UUID requestId, boolean success, String message) implements Serializable {
}
