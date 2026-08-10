# Fault-Tolerant Multi-Paxos Parcel Locker Ledger

Implement the missing protocol behavior in `starter`. The supplied domain state
machine, messages, transport/persistence contracts, observability DTOs, and test
hooks define the compatibility surface. Do not change their public semantics.

## Correctness contract

Safety must hold under crashes, restarts, delay, duplication, reordering,
temporary loss, partitions, stale messages, and multiple leaders. One slot may
never have two chosen commands; correct replicas must agree, execute a contiguous
slot prefix, and apply each `requestId` at most once. Acceptor promises and
accepted pvalues must survive restart.

Liveness is required once failures stop, communication becomes reliable, at least
one leader and replica are alive, and a quorum of acceptors is reachable. A timeout
is only suspicion, not proof that a leader has failed.

For fault tolerance `f`, use at least `2f + 1` acceptors. A quorum is
`floor(N / 2) + 1`; never lower it to preserve availability. The grader covers
`f=1`, `f=2`, and `f=3`.

## Required roles and behavior

Implement Replica, Acceptor, Leader, Scout, and Commander using slots, totally
ordered ballots, PValues, Phase 1/2 messages, PROPOSE, ADOPTED, PREEMPTED, and
DECISION. Complete pmax, proposal re-proposal, heartbeat/failure suspicion,
leader retry/backoff, decision catch-up, persistence integration, and request
deduplication. Preserve the documented A1-A5 and C1-C2 invariants.

Replica and acceptor recovery are durable; leaders and workers may restart from
ephemeral state. A chosen value whose Commander dies before decision broadcast
must be recovered from acceptor history by a later Phase 1.

Snapshotting, checkpointing, log garbage collection, dynamic membership, and
Byzantine fault tolerance are explicitly out of scope. Keeping complete logs is
allowed and expected.

## Commands

The deterministic locker state machine supports ReserveLocker,
CancelReservation, StorePackage, PickupPackage, MarkOutOfService, RestoreLocker,
and NoOp. Commands that fail validation remain in the replicated log. Every
command has exactly one globally unique request ID.

## Submission and grading

Submit the `starter` module. Run `./gradlew test` for public tests and
`./gradlew grade` for the bounded grading workflow. See `GRADING.md` for the
100-point rubric and safety cap. Do not rely on grader or mutable testkit internals.
