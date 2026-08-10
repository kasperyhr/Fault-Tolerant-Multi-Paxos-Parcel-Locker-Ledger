# Grader test matrix

The executable tests are organized by these required families. Hidden course tests
expand every row; names are stable so failures map to the rubric.

- Core/unit: Ballot ordering/equality/serialization; Acceptor P1A/P2A,
  idempotence, persistence, A1-A3; pmax empty/single/multi-ballot/multi-slot;
  Replica proposal, collision/re-proposal, holes, duplicates, replay; Leader
  passive/adopted/C1/preemption; Scout quorum/preemption/duplicate/failure;
  Commander quorum/preemption/duplicate/partial dissemination.
- Basic integration: `SingleCommandIT`, `SequentialCommandsIT`,
  `ConcurrentClientsIT`, `SameLockerConflictIT`, `CompetingReplicaProposalIT`,
  `OutOfOrderDecisionIT`.
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
- Random/long: deterministic `ChaosIT`, `StressF2Test`, `StressF3Test`, and
  `LongLogStressTest`. Normal `grade` excludes the expensive stress rows.

Every adversarial family runs `SafetyInvariantChecker` continuously and prints the
seed plus the final 200 trace events on failure. Integration tests use fresh data
directories and dynamic localhost ports, and must close all nodes in teardown.
