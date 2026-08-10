package paxoslocker.transport;

import paxoslocker.model.NodeId;

import java.util.function.Consumer;

public interface Transport extends AutoCloseable {
    void register(NodeId node, Consumer<MessageEnvelope> receiver);

    void unregister(NodeId node);

    void send(MessageEnvelope envelope);

    @Override
    void close();
}
