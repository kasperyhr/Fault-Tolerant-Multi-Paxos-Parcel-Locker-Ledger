package paxoslocker.grader;
import org.junit.jupiter.api.*; import paxoslocker.app.ClusterMembership; import paxoslocker.model.*; import paxoslocker.protocol.*; import java.util.*; import static org.junit.jupiter.api.Assertions.*;
@Tag("student") class CoreTypesUnitTest {
 @Test void ballotOrderingAndTieBreak(){assertTrue(new BallotNumber(2,"A").compareTo(new BallotNumber(2,"B"))<0);assertTrue(new BallotNumber(3,"A").compareTo(new BallotNumber(2,"Z"))>0);}
 @Test void catchupMessagesDefensivelyCopyAndValidate(){Map<Long,Command> source=new HashMap<>();source.put(1L,new NoOp(UUID.randomUUID()));var response=new DecisionSyncResponseMessage(1,source);source.clear();assertEquals(1,response.decisions().size());assertThrows(IllegalArgumentException.class,()->new DecisionSyncRequestMessage(UnitFixtures.R1,0,10));assertThrows(IllegalArgumentException.class,()->new DecisionSyncResponseMessage(2,Map.of(1L,new NoOp(UUID.randomUUID()))));assertThrows(NullPointerException.class,()->new DecisionSyncResponseMessage(1,null));}
 @Test void membershipExposesMajority(){ClusterMembership m=UnitFixtures.membership();assertEquals(2,m.quorum());assertEquals(Set.of(UnitFixtures.A1,UnitFixtures.A2,UnitFixtures.A3),m.acceptors());}
}
