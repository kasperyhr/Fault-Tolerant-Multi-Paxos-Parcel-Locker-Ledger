package paxoslocker.testkit;

import paxoslocker.diagnostics.*;

public final class RecordingDiagnosticSink implements DiagnosticSink {
    private final EventRecorder recorder; private final SafetyInvariantChecker checker;
    public RecordingDiagnosticSink(EventRecorder recorder, SafetyInvariantChecker checker) { this.recorder=recorder; this.checker=checker; }
    @Override public void record(ProtocolDiagnosticEvent e) {
        EventType type = switch(e.eventType()) {
            case NODE_STARTED -> EventType.NODE_STARTED;
            case NODE_STOPPED -> EventType.NODE_STOPPED;
            case ACCEPTOR_BALLOT -> EventType.ACCEPTOR_BALLOT;
            case PVALUE_ACCEPTED -> EventType.PVALUE_ACCEPTED;
            case COMMANDER_CREATED -> EventType.COMMANDER_CREATED;
            case VALUE_CHOSEN -> EventType.VALUE_CHOSEN;
            case DECISION_LEARNED -> EventType.DECISION_LEARNED;
            case COMMAND_EXECUTED -> EventType.COMMAND_EXECUTED;
            case LEADER_PREEMPTED -> EventType.LEADER_PREEMPTED;
            case MESSAGE_IGNORED -> EventType.MESSAGE_IGNORED;
        };
        recorder.record(e.nodeId(),e.role(),type,e.ballot(),e.slot(),e.requestId(),e.peer(),e.detail());
        switch(e.eventType()) {
            case ACCEPTOR_BALLOT -> { if(e.ballot()!=null) checker.observeAcceptorBallot(e.nodeId(),e.ballot()); }
            case PVALUE_ACCEPTED -> { if(e.ballot()!=null&&e.slot()!=null&&e.command()!=null) checker.observeAccepted(e.nodeId(),e.ballot(),e.slot(),e.command()); }
            case COMMANDER_CREATED -> { if(e.ballot()!=null&&e.slot()!=null&&e.command()!=null) checker.observeCommander(e.ballot(),e.slot(),e.command()); }
            case VALUE_CHOSEN -> { if(e.slot()!=null&&e.command()!=null) checker.observeChosen(e.ballot(),e.slot(),e.command()); }
            case DECISION_LEARNED -> { if(e.slot()!=null&&e.command()!=null) checker.observeReplicaDecision(e.nodeId(),e.slot(),e.command()); }
            case COMMAND_EXECUTED -> { if(e.slot()!=null&&e.command()!=null) checker.observeExecuted(e.nodeId(),e.slot(),e.command()); }
            default -> { }
        }
    }
}
