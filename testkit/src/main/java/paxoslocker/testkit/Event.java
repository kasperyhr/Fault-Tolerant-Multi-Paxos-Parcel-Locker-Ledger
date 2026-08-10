package paxoslocker.testkit;
import paxoslocker.model.*; import java.time.Instant; import java.util.UUID;
public record Event(long sequenceNumber, Instant timestamp, NodeId nodeId, Role role, EventType eventType, BallotNumber ballot, Long slot, UUID requestId, NodeId peer, String detail) {}
