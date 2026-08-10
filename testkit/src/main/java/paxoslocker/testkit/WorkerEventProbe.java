package paxoslocker.testkit;

import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public final class WorkerEventProbe {
    public record ObservedWorkerEvent(NodeId leader, BallotNumber ballot, Long slot,
                                      WorkerEventType eventType) { }
    private final BlockingQueue<ObservedWorkerEvent> events = new LinkedBlockingQueue<>();
    private final Map<ObservedWorkerEvent, Queue<Runnable>> triggers = new ConcurrentHashMap<>();

    public WorkerHook hookFor(NodeId leader, WorkerHook downstream) {
        WorkerHook next = downstream == null ? WorkerHook.NOOP : downstream;
        return (type, ballot, slot) -> {
            ObservedWorkerEvent event = new ObservedWorkerEvent(leader, ballot, slot, type);
            events.add(event); next.onEvent(type, ballot, slot);
            Queue<Runnable> callbacks = triggers.get(event);
            Runnable callback = callbacks == null ? null : callbacks.poll();
            if (callback != null) callback.run();
        };
    }

    public ObservedWorkerEvent await(NodeId leader, BallotNumber ballot, Long slot,
                                     WorkerEventType type, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<ObservedWorkerEvent> deferred = new ArrayList<>();
        try {
            while (System.nanoTime() < deadline) {
                ObservedWorkerEvent event = events.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (event == null) break;
                if (event.leader().equals(leader) && event.ballot().equals(ballot)
                        && Objects.equals(event.slot(), slot) && event.eventType() == type) {
                    events.addAll(deferred); return event;
                }
                deferred.add(event);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError("interrupted", e); }
        finally { events.addAll(deferred); }
        throw new AssertionError("timed out awaiting worker event " + type + " for " + leader + "/" + ballot + "/" + slot);
    }

    public void killOnNext(NodeId leader, BallotNumber ballot, Long slot,
                           WorkerEventType type, Runnable killAction) {
        ObservedWorkerEvent key = new ObservedWorkerEvent(leader, ballot, slot, type);
        triggers.computeIfAbsent(key, ignored -> new ConcurrentLinkedQueue<>()).add(killAction);
    }
}
