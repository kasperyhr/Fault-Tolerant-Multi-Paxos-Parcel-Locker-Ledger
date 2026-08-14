package paxoslocker.worker;

import paxoslocker.model.BallotNumber;
import paxoslocker.model.NodeId;
import paxoslocker.model.PValue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

class ScoutState {
    private final Set<NodeId> responses;
    private final Set<PValue> accepted;

    ScoutState() {
        responses = new HashSet<>();
        accepted = new HashSet<>();
    }

    Set<NodeId> responses() {
        return Set.copyOf(responses);
    }

    Set<PValue> accepted() {
        return Set.copyOf(accepted);
    }

    boolean addResponse(NodeId nodeId, Set<PValue> pValues) {
        if (!responses.add(nodeId)) return false;
        for (PValue pValue : pValues) {
            Optional<PValue> optional = findAccepted(pValue.ballot(), pValue.slot());
            if (optional.isEmpty()) {
                accepted.add(pValue);
            } else if (!optional.get().equals(pValue)) {
                throw new IllegalStateException("Contains same ballot, same slot, different command.");
            }
        }
        return true;
    }

    Optional<PValue> findAccepted(BallotNumber ballot, long slot) {
        return accepted.stream().filter(pValue -> pValue.ballot().equals(ballot)).filter(pValue -> pValue.slot() == slot).findAny();
    }
}
