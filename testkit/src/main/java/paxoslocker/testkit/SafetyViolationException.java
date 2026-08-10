package paxoslocker.testkit;

public final class SafetyViolationException extends AssertionError {
    private final SafetyViolationKind kind;

    public SafetyViolationException(SafetyViolationKind kind, String message) {
        super("SAFETY_" + kind.name() + ": " + message);
        this.kind = kind;
    }

    public SafetyViolationKind kind() { return kind; }
}
