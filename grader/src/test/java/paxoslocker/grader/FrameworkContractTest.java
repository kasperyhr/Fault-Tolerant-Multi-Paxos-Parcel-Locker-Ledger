package paxoslocker.grader;
import paxoslocker.testkit.*; import org.junit.jupiter.api.*; import java.nio.file.*; import static org.junit.jupiter.api.Assertions.*;
@Tag("framework") class FrameworkContractTest { @Test void seedIsAlwaysExposed(){assertNotNull(System.getProperty("paxos.seed"));} @Test void tempDirectoryIsIsolated() throws Exception {Path p=Files.createTempDirectory("grader-cold-start-");try{assertEquals(0,Files.list(p).count());}finally{Files.deleteIfExists(p);}} }
