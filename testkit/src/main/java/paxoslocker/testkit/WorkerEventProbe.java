package paxoslocker.testkit;

import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class WorkerEventProbe {
    public record ObservedWorkerEvent(NodeId leader, WorkerKind kind, BallotNumber ballot, Long slot,
                                      WorkerEventType eventType) { }
    private record MatchingTrigger(NodeId leader, WorkerKind kind, WorkerEventType eventType,
                                   Consumer<ObservedWorkerEvent> callback) { }
    private final BlockingQueue<ObservedWorkerEvent> events = new LinkedBlockingQueue<>();
    private final Map<ObservedWorkerEvent, Queue<Runnable>> triggers = new ConcurrentHashMap<>();
    private final Queue<MatchingTrigger> matchingTriggers = new ConcurrentLinkedQueue<>();

    public WorkerHook hookFor(NodeId leader, WorkerHook downstream) {
        WorkerHook next = downstream == null ? WorkerHook.NOOP : downstream;
        return (type, ballot, slot) -> {
            WorkerKind kind = slot == null ? WorkerKind.SCOUT : WorkerKind.COMMANDER;
            ObservedWorkerEvent event = new ObservedWorkerEvent(leader, kind, ballot, slot, type);
            events.add(event);
            Queue<Runnable> callbacks = triggers.get(event);
            Runnable callback = callbacks == null ? null : callbacks.poll();
            if (callback != null) callback.run();
            for (MatchingTrigger trigger : matchingTriggers) {
                if (trigger.leader().equals(leader) && trigger.kind() == kind && trigger.eventType() == type
                        && matchingTriggers.remove(trigger)) {
                    trigger.callback().accept(event);
                    break;
                }
            }
            next.onEvent(type, ballot, slot);
        };
    }

    public ObservedWorkerEvent await(NodeId leader, BallotNumber ballot, Long slot,
                                     WorkerEventType type, Duration timeout) {
        return awaitMatching(leader, null, ballot, slot, type, timeout);
    }

    public ObservedWorkerEvent awaitNext(NodeId leader, WorkerKind kind,
                                         WorkerEventType type, Duration timeout) {
        return awaitMatching(leader, kind, null, null, type, timeout);
    }

    private ObservedWorkerEvent awaitMatching(NodeId leader, WorkerKind kind, BallotNumber ballot,
                                              Long slot, WorkerEventType type, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<ObservedWorkerEvent> deferred = new ArrayList<>();
        try {
            while (System.nanoTime() < deadline) {
                ObservedWorkerEvent event = events.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (event == null) break;
                if (event.leader().equals(leader) && (kind == null || event.kind() == kind)
                        && (ballot == null || event.ballot().equals(ballot))
                        && (ballot == null || Objects.equals(event.slot(), slot)) && event.eventType() == type) {
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
        ObservedWorkerEvent key = new ObservedWorkerEvent(leader,
                slot == null ? WorkerKind.SCOUT : WorkerKind.COMMANDER, ballot, slot, type);
        triggers.computeIfAbsent(key, ignored -> new ConcurrentLinkedQueue<>()).add(killAction);
    }

    /** Arms before worker creation; the callback receives the first matching worker identity. */
    public void onNextMatching(NodeId leader, WorkerKind kind, WorkerEventType type,
                               Consumer<ObservedWorkerEvent> callback) {
        matchingTriggers.add(new MatchingTrigger(leader, kind, type, Objects.requireNonNull(callback)));
    }
}
