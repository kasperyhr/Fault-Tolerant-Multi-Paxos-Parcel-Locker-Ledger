package paxoslocker.model;

import java.io.Serializable;
import java.util.UUID;

public sealed interface Command extends Serializable permits ReserveLocker, CancelReservation,
        StorePackage, PickupPackage, MarkOutOfService, RestoreLocker, NoOp {
    UUID requestId();
}
