package paxoslocker.model;
import org.junit.jupiter.api.Test; import java.util.*; import static org.junit.jupiter.api.Assertions.*;
class ParcelLockerStateMachineTest {
 @Test void deterministicTransitionsAndFailures(){var sm=new ParcelLockerStateMachine(List.of("L1"));var reserve=new ReserveLocker(UUID.randomUUID(),"client","L1");assertTrue(sm.apply(reserve).success());assertFalse(sm.apply(new ReserveLocker(UUID.randomUUID(),"other","L1")).success());assertEquals(LockerStatus.RESERVED,sm.snapshot().get("L1").status());}
}
