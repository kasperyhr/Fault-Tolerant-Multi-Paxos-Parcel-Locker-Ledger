package paxoslocker.app;

public record ClusterOptions(int faultTolerance, int acceptorCount) {
    public ClusterOptions {
        if (faultTolerance < 0) throw new IllegalArgumentException("f must be non-negative");
        if (acceptorCount < 2 * faultTolerance + 1) throw new IllegalArgumentException("acceptorCount must be >= 2f+1");
    }

    public int quorum() {
        return acceptorCount / 2 + 1;
    }

    public static ClusterOptions defaults(int f) {
        return new ClusterOptions(f, 2 * f + 1);
    }
}
