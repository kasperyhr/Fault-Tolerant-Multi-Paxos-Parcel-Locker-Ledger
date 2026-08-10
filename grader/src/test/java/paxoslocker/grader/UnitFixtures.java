package paxoslocker.grader;

import paxoslocker.app.ClusterMembership;
import paxoslocker.diagnostics.DiagnosticSink;
import paxoslocker.model.NodeId;
import paxoslocker.testkit.*;
import java.util.Set;

final class UnitFixtures {
    static final NodeId A1=new NodeId("A1"),A2=new NodeId("A2"),A3=new NodeId("A3"),R1=new NodeId("R1"),L1=new NodeId("L1");
    static ClusterMembership membership(){return new ClusterMembership(Set.of(A1,A2,A3),Set.of(R1),Set.of(L1),2);}
    static InMemoryTransport transport(){return new InMemoryTransport(new EventRecorder());}
    static DiagnosticSink sink(){return DiagnosticSink.NOOP;}
    private UnitFixtures(){}
}
