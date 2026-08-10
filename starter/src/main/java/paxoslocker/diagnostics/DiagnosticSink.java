package paxoslocker.diagnostics;

@FunctionalInterface
public interface DiagnosticSink {
    DiagnosticSink NOOP = ignored -> { };
    void record(ProtocolDiagnosticEvent event);
}
