package paxoslocker.model;

import java.util.*;

/**
 * Complete deterministic business logic; consensus ordering remains student work.
 */
public final class ParcelLockerStateMachine {
    private final Map<String, Locker> lockers = new TreeMap<>();

    public ParcelLockerStateMachine(Collection<String> lockerIds) {
        lockerIds.forEach(id -> lockers.put(id, Locker.available(id)));
    }

    public synchronized CommandResult apply(Command command) {
        if (command instanceof NoOp) return ok(command, "no-op");
        String id = lockerId(command);
        Locker before = lockers.get(id);
        if (before == null) return fail(command, "unknown locker");
        if (command instanceof ReserveLocker c) {
            if (before.status() != LockerStatus.AVAILABLE) return fail(c, "locker unavailable");
            lockers.put(id, new Locker(id, LockerStatus.RESERVED, null, c.clientId()));
        } else if (command instanceof CancelReservation c) {
            if (before.status() != LockerStatus.RESERVED || !c.clientId().equals(before.reservedForClientId()))
                return fail(c, "not reservation owner");
            lockers.put(id, Locker.available(id));
        } else if (command instanceof StorePackage c) {
            if (before.status() != LockerStatus.AVAILABLE && before.status() != LockerStatus.RESERVED)
                return fail(c, "locker cannot accept package");
            lockers.put(id, new Locker(id, LockerStatus.OCCUPIED, c.packageId(), null));
        } else if (command instanceof PickupPackage c) {
            if (before.status() != LockerStatus.OCCUPIED || !c.packageId().equals(before.packageId()))
                return fail(c, "package mismatch");
            lockers.put(id, Locker.available(id));
        } else if (command instanceof MarkOutOfService c) {
            if (before.status() != LockerStatus.AVAILABLE) return fail(c, "only available lockers may be disabled");
            lockers.put(id, new Locker(id, LockerStatus.OUT_OF_SERVICE, null, null));
        } else if (command instanceof RestoreLocker c) {
            if (before.status() != LockerStatus.OUT_OF_SERVICE) return fail(c, "locker is not out of service");
            lockers.put(id, Locker.available(id));
        }
        return ok(command, "applied");
    }

    public synchronized Map<String, Locker> snapshot() {
        return Map.copyOf(lockers);
    }

    private static String lockerId(Command c) {
        return switch (c) {
            case ReserveLocker x -> x.lockerId();
            case CancelReservation x -> x.lockerId();
            case StorePackage x -> x.lockerId();
            case PickupPackage x -> x.lockerId();
            case MarkOutOfService x -> x.lockerId();
            case RestoreLocker x -> x.lockerId();
            case NoOp ignored -> throw new IllegalArgumentException("NoOp has no locker");
        };
    }

    private static CommandResult ok(Command c, String m) {
        return new CommandResult(c.requestId(), true, m);
    }

    private static CommandResult fail(Command c, String m) {
        return new CommandResult(c.requestId(), false, m);
    }
}
