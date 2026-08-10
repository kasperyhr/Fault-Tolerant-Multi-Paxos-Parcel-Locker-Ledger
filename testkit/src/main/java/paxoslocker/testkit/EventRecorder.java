package paxoslocker.testkit;
import paxoslocker.model.*; import java.time.Instant; import java.util.*; import java.util.concurrent.*; import java.util.concurrent.atomic.AtomicLong;
public final class EventRecorder {
    private final AtomicLong sequence=new AtomicLong(); private final ConcurrentLinkedDeque<Event> events=new ConcurrentLinkedDeque<>(); private final int capacity;
    public EventRecorder() { this(20_000); } public EventRecorder(int capacity) { if(capacity<200) throw new IllegalArgumentException("capacity"); this.capacity=capacity; }
    public Event record(NodeId node, Role role, EventType type, BallotNumber ballot, Long slot, UUID requestId, NodeId peer, String detail) {
        Event e=new Event(sequence.incrementAndGet(), Instant.now(), node, role, type, ballot, slot, requestId, peer, detail); events.addLast(e); while(events.size()>capacity) events.pollFirst(); return e;
    }
    public List<Event> snapshot() { return List.copyOf(events); }
    public List<Event> tail(int count) { List<Event> all=snapshot(); return all.subList(Math.max(0,all.size()-count),all.size()); }
    public String formatTail(int count) { StringBuilder b=new StringBuilder(); tail(count).forEach(e->b.append(e).append(System.lineSeparator())); return b.toString(); }
}
