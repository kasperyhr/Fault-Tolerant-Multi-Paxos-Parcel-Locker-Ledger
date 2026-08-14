package paxoslocker.worker;

import paxoslocker.model.NodeId;

import java.util.HashSet;
import java.util.Set;

class CommanderState {
    private final Set<NodeId> responses;
    private boolean chosen;

    CommanderState() {
        responses = new HashSet<>();
        chosen = false;
    }

    Set<NodeId> responses() { return Set.copyOf(responses); }
    boolean chosen() { return chosen; }
    void setChosen(boolean chosen) { this.chosen = chosen; }
    boolean addResponse(NodeId id) { return responses.add(id); }
}
