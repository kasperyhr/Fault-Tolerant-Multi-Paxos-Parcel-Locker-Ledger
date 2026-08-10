package paxoslocker.testkit;

import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import paxoslocker.transport.Transport;
import paxoslocker.worker.*;
import java.util.Set;

public final class InstrumentedWorkerFactory implements WorkerFactory {
    private final WorkerFactory delegate; private final WorkerRegistry registry; private final WorkerEventProbe probe; private final EventRecorder events;
    public InstrumentedWorkerFactory(WorkerFactory delegate, WorkerRegistry registry, WorkerEventProbe probe) {
        this(delegate,registry,probe,null);
    }
    public InstrumentedWorkerFactory(WorkerFactory delegate, WorkerRegistry registry, WorkerEventProbe probe, EventRecorder events) {
        this.delegate=delegate; this.registry=registry; this.probe=probe; this.events=events;
    }
    @Override public Scout createScout(NodeId leader, BallotNumber ballot, Set<NodeId> acceptors,
            int quorum, Transport transport, WorkerHook hook) {
        WorkerHook instrumented = probe.hookFor(leader, (type,b,s) -> {
            if(events!=null)events.record(leader,Role.SCOUT,EventType.WORKER_EVENT,b,s,null,null,type.name());
            if (type == WorkerEventType.SCOUT_EXITED) registry.markScoutTerminal(leader, b);
            if (hook != null) hook.onEvent(type,b,s);
        });
        Scout scout=delegate.createScout(leader,ballot,acceptors,quorum,transport,instrumented);
        registry.register(scout);
        instrumented.onEvent(WorkerEventType.SCOUT_CREATED,ballot,null);
        return scout;
    }
    @Override public Commander createCommander(NodeId leader, PValue pvalue, Set<NodeId> acceptors,
            Set<NodeId> replicas, int quorum, Transport transport, WorkerHook hook) {
        WorkerHook instrumented = probe.hookFor(leader, (type,b,s) -> {
            if(events!=null)events.record(leader,Role.COMMANDER,EventType.WORKER_EVENT,b,s,null,null,type.name());
            if (type == WorkerEventType.COMMANDER_EXITED) registry.markCommanderTerminal(leader,b,s);
            if (hook != null) hook.onEvent(type,b,s);
        });
        Commander commander=delegate.createCommander(leader,pvalue,acceptors,replicas,quorum,transport,instrumented);
        registry.register(commander);
        instrumented.onEvent(WorkerEventType.COMMANDER_CREATED,pvalue.ballot(),pvalue.slot());
        return commander;
    }
}
