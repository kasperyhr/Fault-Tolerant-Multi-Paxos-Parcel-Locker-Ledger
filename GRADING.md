# Grading guide

The grader maps each executed test to one rubric section and awards that section
proportionally to passed tests:

| Section | Points |
|---|---:|
| Build/API | 5 |
| Core Types | 5 |
| Replica | 10 |
| Acceptor | 15 |
| Leader/pmax | 10 |
| Scout | 10 |
| Commander | 10 |
| Basic Integration | 10 |
| Crash Recovery / Failover | 10 |
| Network / Adversarial Safety | 10 |
| Stress / Chaos | 5 |

`grade` runs framework contracts, student unit tests, integration tests, and the
bounded deterministic chaos suite. `gradeFull` additionally runs StressF2,
StressF3, and the 50,000-slot long-log suite. Grader test tasks are forced into a
stable order, and results are collected in a thread-safe queue even though the
root build permits Gradle parallel execution.

The generated `build/reports/grading/summary.json` contains `totalPoints`,
`maximumPoints`, per-section awarded/maximum/test counts, `failedTests`,
`passedTests`, `seed`, `safetyViolation`, and `scoreCapApplied`. `summary.txt`
contains the same information in human-readable form.

A directly observed pair of different chosen commands for one slot is a Safety
violation and caps the final score at 60/100. A timeout, unavailable quorum, or
ordinary TODO failure is not automatically a Safety violation.

Use `./gradlew reproduceFailure -Pseed=123456` to rerun the bounded chaos suite.
On failure, chaos/integration helpers print the seed, cluster config, leader,
acceptor and replica snapshots, running workers, and the final 200 trace events.

The grader remains isolated: `starter` never depends on `testkit` or `grader`,
and test infrastructure never computes pmax, elects a leader, or creates a
decision for the student implementation.
