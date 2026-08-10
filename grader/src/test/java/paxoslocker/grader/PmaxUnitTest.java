package paxoslocker.grader;
import org.junit.jupiter.api.*; import paxoslocker.leader.Leader; import paxoslocker.model.*; import java.util.*; import static org.junit.jupiter.api.Assertions.*;
@Tag("student") class PmaxUnitTest {
 @Test void empty(){assertTrue(Leader.pmax(Set.of()).isEmpty());}
 @Test void selectsHighestPerSlot(){Command a=new NoOp(UUID.randomUUID()),b=new NoOp(UUID.randomUUID());var result=Leader.pmax(Set.of(new PValue(new BallotNumber(1,"L1"),1,a),new PValue(new BallotNumber(2,"L2"),1,b),new PValue(new BallotNumber(1,"L1"),2,a)));assertEquals(Map.of(1L,b,2L,a),result);}
}
