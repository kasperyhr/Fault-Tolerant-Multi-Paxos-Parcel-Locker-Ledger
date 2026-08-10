package paxoslocker.persistence;
import org.junit.jupiter.api.Test; import org.junit.jupiter.api.io.TempDir; import java.nio.file.Path; import static org.junit.jupiter.api.Assertions.*;
class FileStoreTest { @Test void roundTripAndPathSafety(@TempDir Path dir){var store=new FileStore(dir);store.save("node/state","hello");assertEquals("hello",store.load("node/state",String.class).orElseThrow());assertThrows(IllegalArgumentException.class,()->store.save("../escape","no"));store.delete("node/state");assertTrue(store.load("node/state",String.class).isEmpty());} }
