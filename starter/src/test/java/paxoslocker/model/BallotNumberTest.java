package paxoslocker.model;
import org.junit.jupiter.api.Test; import java.io.*; import static org.junit.jupiter.api.Assertions.*;
class BallotNumberTest {
 @Test void totalOrderingAndTieBreak(){assertTrue(new BallotNumber(2,"A").compareTo(new BallotNumber(2,"B"))<0);assertTrue(new BallotNumber(3,"A").compareTo(new BallotNumber(2,"Z"))>0);}
 @Test void equality(){assertEquals(new BallotNumber(7,"L"),new BallotNumber(7,"L"));}
 @Test void serializationRoundTrip() throws Exception {var b=new BallotNumber(9,"leader");var bytes=new ByteArrayOutputStream();try(var out=new ObjectOutputStream(bytes)){out.writeObject(b);}try(var in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){assertEquals(b,in.readObject());}}
}
