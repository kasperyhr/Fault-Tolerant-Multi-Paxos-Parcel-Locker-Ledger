package paxoslocker.diagnostics;

import paxoslocker.model.*;

@FunctionalInterface
public interface WorkerHook {
    WorkerHook NOOP = (e, b, s) -> {
    };

    void onEvent(WorkerEventType event, BallotNumber ballot, Long slot);
}
