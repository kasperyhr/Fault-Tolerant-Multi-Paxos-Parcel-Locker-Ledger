package paxoslocker.diagnostics;

import paxoslocker.model.*;
import java.io.Serializable;
import java.util.UUID;

/** Starter-owned observation record; no testkit type leaks into the student module. */
public record ProtocolDiagnosticEvent(NodeId nodeId, Role role, ProtocolDiagnosticType eventType,
        BallotNumber ballot, Long slot, UUID requestId, NodeId peer, Command command,
        String detail) implements Serializable {
    public ProtocolDiagnosticEvent {
        if (nodeId == null || role == null || eventType == null)
            throw new IllegalArgumentException("nodeId, role and eventType are required");
        detail = detail == null ? "" : detail;
    }
}
