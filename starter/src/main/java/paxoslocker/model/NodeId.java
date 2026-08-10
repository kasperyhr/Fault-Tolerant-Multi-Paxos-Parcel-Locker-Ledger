package paxoslocker.model;

import java.io.Serializable;
import java.util.Objects;

public record NodeId(String value) implements Comparable<NodeId>, Serializable {
    public NodeId {
        if (Objects.requireNonNull(value).isBlank()) throw new IllegalArgumentException("blank node id");
    }

    @Override
    public int compareTo(NodeId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
