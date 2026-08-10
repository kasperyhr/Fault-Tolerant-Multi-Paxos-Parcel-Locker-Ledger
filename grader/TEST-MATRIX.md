# Grader test matrix

The checked-in executable tests are organized by these required families. Every
class named below has a real scenario body; hidden course tests may add parameters
and seeds, but this matrix no longer stands in place of missing Java tests.

- Core/unit: Ballot ordering/equality/serialization; Acceptor P1A/P2A,
  idempotence, persistence, A1-A3; pmax empty/single/multi-ballot/multi-slot;
  Replica proposal, collision/re-proposal, holes, duplicates, replay; Leader
  passive/adopted/C1/preemption; Scout quorum/preemption/duplicate/failure;
  Commander quorum/preemption/duplicate/partial dissemination.
- Basic integration: `SingleCommandIT`, `SequentialCommandsIT`,
  `ConcurrentClientsIT`, `SameLockerConflictIT`, `CompetingReplicaProposalIT`,
  `OutOfOrderDecisionIT`, and real-localhost `LocalTcpSingleCommandIT`.
- Leader: `LeaderCrashBeforeScoutIT`, `LeaderCrashAfterAdoptedIT`,
  `LeaderCrashDuringCommandersIT`, `LeaderCrashAfterChosenIT`,
  `OldLeaderReturnsIT`, `TwoLeadersCompeteIT`, `ThreeLeadersCompeteIT`,
  `EventuallyStableLeaderIT`.
- Acceptors/recovery: `OneAcceptorFailureIT`, `LostQuorumSafetyIT`,
  `F2TwoAcceptorFailureIT`, `F2LostQuorumIT`, `AcceptorPersistenceIT`,
  `AcceptorRepeatedRestartIT`.
- Replicas: `ReplicaCatchupIT`, `ReplicaGapCatchupIT`,
  `MultipleReplicaRecoveryIT`.
- Worker failures: `ScoutCrashBeforeSendIT`, `ScoutCrashAfterMinorityIT`,
  `ScoutCrashAfterQuorumIT`, `ScoutCrashBeforeAdoptedIT`,
  `CommanderCrashBeforeP2aIT`, `CommanderCrashAfterMinorityIT`,
  `CommanderCrashAfterChosenIT`, `CommanderPartialDecisionDeliveryIT`,
  `CommanderAndLeaderCrashAfterChosenIT`.
- Network: `DelayMessagesIT`, `DuplicateMessagesIT`, `ReorderedMessagesIT`,
  `TemporaryDropIT`, `LeaderPartitionIT`, `ReplicaPartitionIT`,
  `MinorityAcceptorPartitionIT`, `PartitionHealIT`, `StaleLeaderMessagesIT`,
  `StaleCommanderMessagesIT`.
- Combined: `LeaderCrashPlusAcceptorDownIT`,
  `CommanderCrashPlusReplicaPartitionIT`, `ScoutCrashPlusStaleLeaderIT`,
  `LeaderFailoverPlusOldCommanderIT`, `AcceptorRestartPlusDuplicateMessagesIT`,
  `ReplicaRestartDuringTrafficIT`, `TwoLeadersPlusAcceptorCrashIT`,
  `PartitionFailoverHealIT`.
- Random/long: seed reproducibility `ChaosIT`, real `DeterministicChaosIT`,
  `StressF2Test`, `StressF3Test`, and `LongLogStressTest`. Normal `grade`
  excludes the expensive stress rows.

Every adversarial family runs `SafetyInvariantChecker` continuously and prints the
seed plus the final 200 trace events on failure. Integration tests use fresh data
directories and dynamic localhost ports, and must close all nodes in teardown.

## Correlation and exact-stage release regressions

- `ProtocolCorrelationFrameworkTest`: stale requested-ballot P1B cannot reach a newer
  Scout; higher `acceptorBallot` P2B routes to the Commander identified by its original
  `requestedBallot`; two slots under one ballot cannot cross-route.
- `WorkerFailurePrecisionFrameworkTest`: trigger is armed from CREATED before worker
  start, before-send kill prevents any P1A/P2A send, repeated kill is idempotent, and
  EXIT is emitted exactly once.
- Framework safety regressions prove learned does not imply chosen, distinct acceptor
  quorum does imply chosen evidence, strict VALUE_CHOSEN without quorum is rejected,
  and structured chosen-conflict markers drive score capping.
- Worker integration scenarios arm before creation/submission and target the event named
  by each class: first P1B/P2B for minority, quorum/adopted events, decision-before-send,
  or first decision-after-send for partial dissemination. No fixed sleep is used.
