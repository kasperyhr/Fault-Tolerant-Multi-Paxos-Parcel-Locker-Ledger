package paxoslocker.model;

import java.io.Serializable;

public record Locker(String lockerId, LockerStatus status, String packageId,
                     String reservedForClientId) implements Serializable {
    public static Locker available(String id) {
        return new Locker(id, LockerStatus.AVAILABLE, null, null);
    }
}
