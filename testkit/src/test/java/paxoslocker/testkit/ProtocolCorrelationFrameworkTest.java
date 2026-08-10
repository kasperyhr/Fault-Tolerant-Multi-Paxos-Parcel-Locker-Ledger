package paxoslocker.testkit;

import org.junit.jupiter.api.Test;
import paxoslocker.app.ClusterMembership;
import paxoslocker.diagnostics.*;
import paxoslocker.leader.Leader;
import paxoslocker.model.*;
import paxoslocker.protocol.*;
import paxoslocker.transport.*;
import paxoslocker.worker.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolCorrelationFrameworkTest {
    private static final NodeId A1=new NodeId("A1"),A2=new NodeId("A2"),A3=new NodeId("A3"),R1=new NodeId("R1"),L1=new NodeId("L1");
    private static final ClusterMembership MEMBERSHIP=new ClusterMembership(Set.of(A1,A2,A3),Set.of(R1),Set.of(L1),2);

    @Test void staleP1bDoesNotReachNewScout(){
        try(var transport=new InMemoryTransport(new EventRecorder())){
            var factory=new TrackingFactory();var leader=new TestLeader(transport,factory);leader.start();
            var oldBallot=new BallotNumber(21,"B");var newBallot=new BallotNumber(23,"B");
            leader.scout(oldBallot);TrackingScout current=(TrackingScout)leader.scout(newBallot);
            leader.receive(MessageEnvelope.of(A1,L1,new P1bMessage(A1,oldBallot,oldBallot,Set.of())));
            assertEquals(0,current.responses);
        }
    }

    @Test void higherBallotP2bRoutesToOriginalCommander(){
        try(var transport=new InMemoryTransport(new EventRecorder())){
            var factory=new TrackingFactory();var leader=new TestLeader(transport,factory);leader.start();
            var requested=new BallotNumber(21,"B");var higher=new BallotNumber(22,"C");
            TrackingCommander original=(TrackingCommander)leader.commander(new PValue(requested,104,new NoOp(UUID.randomUUID())));
            TrackingCommander wrong=(TrackingCommander)leader.commander(new PValue(higher,104,new NoOp(UUID.randomUUID())));
            leader.receive(MessageEnvelope.of(A1,L1,new P2bMessage(A1,requested,higher,104)));
            assertEquals(1,original.responses);assertEquals(0,wrong.responses);
        }
    }

    @Test void p2bSlotDoesNotCrossCommander(){
        try(var transport=new InMemoryTransport(new EventRecorder())){
            var factory=new TrackingFactory();var leader=new TestLeader(transport,factory);leader.start();
            var ballot=new BallotNumber(21,"B");
            TrackingCommander slot104=(TrackingCommander)leader.commander(new PValue(ballot,104,new NoOp(UUID.randomUUID())));
            TrackingCommander slot105=(TrackingCommander)leader.commander(new PValue(ballot,105,new NoOp(UUID.randomUUID())));
            leader.receive(MessageEnvelope.of(A1,L1,new P2bMessage(A1,ballot,ballot,105)));
            assertEquals(0,slot104.responses);assertEquals(1,slot105.responses);
        }
    }

    private static final class TestLeader extends Leader {
        TestLeader(Transport transport,WorkerFactory factory){super(L1,transport,MEMBERSHIP,factory,DiagnosticSink.NOOP,WorkerHook.NOOP);}
        Scout scout(BallotNumber ballot){return createScout(ballot);} Commander commander(PValue value){return createCommander(value);}
        void receive(MessageEnvelope envelope){onEnvelope(envelope);}
        @Override public LeaderStatus status(){return new LeaderStatus(BallotNumber.BOTTOM,false,Map.of(),Optional.empty(),Set.of());}
    }
    private static final class TrackingFactory implements WorkerFactory {
        @Override public Scout createScout(NodeId leader,BallotNumber ballot,Set<NodeId> acceptors,int quorum,Transport transport,WorkerHook hook){return new TrackingScout(leader,ballot,acceptors,quorum,transport,hook);}
        @Override public Commander createCommander(NodeId leader,PValue value,Set<NodeId> acceptors,Set<NodeId> replicas,int quorum,Transport transport,WorkerHook hook){return new TrackingCommander(leader,value,acceptors,replicas,quorum,transport,hook);}
    }
    private static final class TrackingScout extends Scout {
        int responses;TrackingScout(NodeId l,BallotNumber b,Set<NodeId>a,int q,Transport t,WorkerHook h){super(l,b,a,q,t,h);}
        @Override public void start(){} @Override public void onP1b(P1bMessage response){responses++;}
        @Override public ScoutStatus status(){return new ScoutStatus(ballot,Set.of(),true);}
    }
    private static final class TrackingCommander extends Commander {
        int responses;TrackingCommander(NodeId l,PValue p,Set<NodeId>a,Set<NodeId>r,int q,Transport t,WorkerHook h){super(l,p,a,r,q,t,h);}
        @Override public void start(){} @Override public void onP2b(P2bMessage response){responses++;}
        @Override public CommanderStatus status(){return new CommanderStatus(pvalue,Set.of(),false,true);}
    }
}
