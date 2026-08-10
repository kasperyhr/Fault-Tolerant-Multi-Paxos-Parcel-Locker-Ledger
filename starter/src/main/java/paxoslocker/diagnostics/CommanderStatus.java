package paxoslocker.diagnostics;

import paxoslocker.model.*;

import java.util.*;

public record CommanderStatus(PValue pvalue, Set<NodeId> responses, boolean chosen, boolean running) {
    public CommanderStatus {
        responses = Set.copyOf(responses);
    }
}
