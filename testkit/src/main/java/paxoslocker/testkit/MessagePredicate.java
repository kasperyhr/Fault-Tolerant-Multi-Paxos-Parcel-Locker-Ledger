package paxoslocker.testkit;
import paxoslocker.transport.MessageEnvelope;
@FunctionalInterface public interface MessagePredicate { boolean test(MessageEnvelope envelope); static MessagePredicate any(){ return ignored->true; } static MessagePredicate messageType(Class<?> type){ return e->type.isInstance(e.message()); } }
