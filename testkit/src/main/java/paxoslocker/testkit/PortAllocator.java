package paxoslocker.testkit;
import java.io.IOException; import java.net.*;
public final class PortAllocator { private PortAllocator(){} public static int availableLoopbackPort(){try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){return socket.getLocalPort();}catch(IOException e){throw new RuntimeException(e);}} }
