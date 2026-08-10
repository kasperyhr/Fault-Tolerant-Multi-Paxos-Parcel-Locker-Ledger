package paxoslocker.app;

import paxoslocker.model.NodeId;
import java.io.Serializable;
import java.util.*;

/** Immutable, role-oriented cluster membership shared by all long-lived nodes. */
public record ClusterMembership(Set<NodeId> acceptors, Set<NodeId> replicas,
                                Set<NodeId> leaders, int quorum) implements Serializable {
    public ClusterMembership {
        acceptors = Set.copyOf(Objects.requireNonNull(acceptors, "acceptors"));
        replicas = Set.copyOf(Objects.requireNonNull(replicas, "replicas"));
        leaders = Set.copyOf(Objects.requireNonNull(leaders, "leaders"));
        if (acceptors.isEmpty() || replicas.isEmpty() || leaders.isEmpty())
            throw new IllegalArgumentException("every long-lived role requires at least one node");
        if (quorum <= acceptors.size() / 2 || quorum > acceptors.size())
            throw new IllegalArgumentException("quorum must be a valid strict majority");
        Set<NodeId> all = new HashSet<>(acceptors); all.addAll(replicas); all.addAll(leaders);
        if (all.size() != acceptors.size() + replicas.size() + leaders.size())
            throw new IllegalArgumentException("a NodeId cannot belong to multiple roles");
    }

    public Set<NodeId> peersOf(NodeId replica) {
        if (!replicas.contains(replica)) throw new IllegalArgumentException("not a replica: " + replica);
        Set<NodeId> peers = new HashSet<>(replicas);
        peers.remove(replica);
        return Set.copyOf(peers);
    }
}
