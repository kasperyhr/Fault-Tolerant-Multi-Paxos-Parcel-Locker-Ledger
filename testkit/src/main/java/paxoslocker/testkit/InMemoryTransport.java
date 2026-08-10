package paxoslocker.testkit;

import paxoslocker.model.*; import paxoslocker.transport.*;
import java.time.Duration; import java.util.*; import java.util.concurrent.*; import java.util.function.Consumer;

/** Thread-safe deterministic fault-injecting transport; one-shot rules are consumed atomically. */
public final class InMemoryTransport implements Transport {
    enum Action { DROP, DELAY, DUPLICATE, REORDER }
    record Rule(Action action, MessagePredicate predicate, Duration duration) {}
    private final Map<NodeId,Consumer<MessageEnvelope>> receivers=new ConcurrentHashMap<>();
    private final Set<Link> partitions=ConcurrentHashMap.newKeySet(); private final Set<NodeId> paused=ConcurrentHashMap.newKeySet();
    private final Deque<Rule> nextRules=new ArrayDeque<>(); private final Deque<MessageEnvelope> pausedMessages=new ArrayDeque<>();
    private MessageEnvelope heldForReorder;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(); private final EventRecorder events;
    public InMemoryTransport(EventRecorder events){ this.events=events; }
    @Override public void register(NodeId node, Consumer<MessageEnvelope> receiver){ if(receivers.putIfAbsent(node,receiver)!=null) throw new IllegalStateException("duplicate "+node); }
    @Override public void unregister(NodeId node){ receivers.remove(node); }
    public boolean isRegistered(NodeId node){ return receivers.containsKey(node); }
    @Override public void send(MessageEnvelope e){
        events.record(e.source(),Role.TRANSPORT,EventType.MESSAGE_SENT,null,null,null,e.destination(),e.message().getClass().getSimpleName());
        Rule rule=null; synchronized(nextRules){ Iterator<Rule> it=nextRules.iterator(); while(it.hasNext()){ Rule candidate=it.next(); if(candidate.predicate.test(e)){ rule=candidate; it.remove(); break; } } }
        if(isPartitioned(e.source(),e.destination()) || (rule!=null && rule.action==Action.DROP)){ events.record(e.source(),Role.TRANSPORT,EventType.MESSAGE_DROPPED,null,null,null,e.destination(),"fault rule"); return; }
        if(rule!=null && rule.action==Action.REORDER){ synchronized(this){heldForReorder=e;} events.record(e.source(),Role.TRANSPORT,EventType.MESSAGE_DELAYED,null,null,null,e.destination(),"held for deterministic reorder"); return; }
        if(rule!=null && rule.action==Action.DELAY){ events.record(e.source(),Role.TRANSPORT,EventType.MESSAGE_DELAYED,null,null,null,e.destination(),rule.duration.toString()); executor.schedule(()->deliver(e),rule.duration.toNanos(),TimeUnit.NANOSECONDS); return; }
        deliver(e);
        if(rule!=null && rule.action==Action.DUPLICATE){ events.record(e.source(),Role.TRANSPORT,EventType.MESSAGE_DUPLICATED,null,null,null,e.destination(),"one extra copy"); deliver(e); }
        MessageEnvelope held; synchronized(this){held=heldForReorder;heldForReorder=null;} if(held!=null)deliver(held);
    }
    private void deliver(MessageEnvelope e){
        synchronized(pausedMessages){ if(paused.contains(e.destination())){ pausedMessages.addLast(e); return; } }
        Consumer<MessageEnvelope> receiver=receivers.get(e.destination()); if(receiver!=null){ receiver.accept(e); events.record(e.destination(),Role.TRANSPORT,EventType.MESSAGE_DELIVERED,null,null,null,e.source(),e.message().getClass().getSimpleName()); }
    }
    public void dropNext(MessagePredicate p){ add(new Rule(Action.DROP,p,Duration.ZERO)); }
    public void delayNext(MessagePredicate p,Duration d){ add(new Rule(Action.DELAY,p,d)); }
    public void duplicateNext(MessagePredicate p){ add(new Rule(Action.DUPLICATE,p,Duration.ZERO)); }
    public void reorderNext(MessagePredicate p){ add(new Rule(Action.REORDER,p,Duration.ZERO)); }
    private void add(Rule r){ synchronized(nextRules){ nextRules.addLast(r); } }
    public void partition(NodeId a,NodeId b){ partitions.add(new Link(a,b)); partitions.add(new Link(b,a)); events.record(a,Role.TRANSPORT,EventType.PARTITIONED,null,null,null,b,"bidirectional"); }
    public void partition(Set<NodeId> a,Set<NodeId> b){ a.forEach(x->b.forEach(y->partition(x,y))); }
    public void heal(NodeId a,NodeId b){ partitions.remove(new Link(a,b)); partitions.remove(new Link(b,a)); events.record(a,Role.TRANSPORT,EventType.PARTITION_HEALED,null,null,null,b,"bidirectional"); }
    public void healAll(){ partitions.clear(); events.record(new NodeId("transport"),Role.TRANSPORT,EventType.PARTITION_HEALED,null,null,null,null,"all"); }
    public void pause(NodeId node){ paused.add(node); }
    public void resume(NodeId node){ paused.remove(node); List<MessageEnvelope> due=new ArrayList<>(); synchronized(pausedMessages){ pausedMessages.removeIf(e->{ if(e.destination().equals(node)){ due.add(e); return true;} return false; }); } due.forEach(this::deliver); }
    private boolean isPartitioned(NodeId a,NodeId b){ return partitions.contains(new Link(a,b)); }
    public int pendingOneShotRules(){ synchronized(nextRules){ return nextRules.size(); } }
    @Override public void close(){ executor.shutdownNow(); receivers.clear(); synchronized(nextRules){nextRules.clear();} }
    private record Link(NodeId from,NodeId to){}
}
