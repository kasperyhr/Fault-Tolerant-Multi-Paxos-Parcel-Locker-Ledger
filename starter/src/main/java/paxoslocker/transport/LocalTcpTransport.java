package paxoslocker.transport;

import paxoslocker.model.NodeId;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Small localhost-only transport. One instance may host several logical nodes.
 */
public final class LocalTcpTransport implements Transport {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<NodeId, Endpoint> endpoints = new ConcurrentHashMap<>();

    @Override
    public void register(NodeId node, Consumer<MessageEnvelope> receiver) {
        try {
            ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Endpoint endpoint = new Endpoint(server, receiver);
            if (endpoints.putIfAbsent(node, endpoint) != null) {
                server.close();
                throw new IllegalStateException("duplicate node " + node);
            }
            executor.submit(() -> acceptLoop(endpoint));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void unregister(NodeId node) {
        Endpoint e = endpoints.remove(node);
        if (e != null) try {
            e.server.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void send(MessageEnvelope envelope) {
        Endpoint destination = endpoints.get(envelope.destination());
        if (destination == null) return;
        executor.submit(() -> {
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), destination.server.getLocalPort());
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                out.writeObject(envelope);
            } catch (IOException ignored) { /* network loss is a legal transport outcome */ }
        });
    }

    public int port(NodeId node) {
        return endpoints.get(node).server.getLocalPort();
    }

    private void acceptLoop(Endpoint e) {
        while (!e.server.isClosed()) try {
            Socket socket = e.server.accept();
            executor.submit(() -> receive(e, socket));
        } catch (SocketException closed) {
            return;
        } catch (IOException ignored) {
        }
    }

    private static void receive(Endpoint e, Socket socket) {
        try (socket; ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            e.receiver.accept((MessageEnvelope) in.readObject());
        } catch (IOException | ClassNotFoundException ignored) {
        }
    }

    @Override
    public void close() {
        endpoints.keySet().forEach(this::unregister);
        executor.close();
    }

    private record Endpoint(ServerSocket server, Consumer<MessageEnvelope> receiver) {
    }
}
