# Grading guide

The rubric totals 100 points: Build/API 5, Core Types 5, Replica 10, Acceptor 15,
Leader/pmax 10, Scout 10, Commander 10, Basic Integration 10, Crash Recovery and
Failover 10, Network/Adversarial Safety 10, and Stress/Chaos 5.

A reproducible safety violation (two different chosen commands for one slot) caps
the total score at 60. A liveness timeout is not itself proof of a safety failure.
Graders must include the seed, cluster configuration, status snapshots, and the
last 200 trace events in failure output.

`grade` runs framework, integration, and bounded chaos tests. `gradeFull` adds the
expensive stress and long-log suites. The generated reports are
`build/reports/grading/summary.txt` and `summary.json`.

The checked-in grader is intentionally isolated: `starter` never depends on it,
and grading code may be distributed separately without changing student code.
