package paxoslocker.grader;
import org.junit.jupiter.api.*; import java.util.*; import static org.junit.jupiter.api.Assertions.*;
@Tag("chaos") class ChaosIT { @Test void deterministicSeedIsReproducible(){long seed=Long.parseLong(System.getProperty("paxos.seed"));var a=new Random(seed);var b=new Random(seed);for(int i=0;i<100;i++)assertEquals(a.nextLong(),b.nextLong(),"seed="+seed);} }
