# CS Distributed Systems Project

# Fault-Tolerant Multi-Paxos Parcel Locker Ledger

## 1. 项目目标

你需要使用 Java 实现一个基于 Multi-Paxos 的容错分布式状态机。

系统模拟一个现实中的“智能包裹柜控制系统”。

一个小区或校园存在多个智能包裹柜。物流人员、用户和维护人员可能同时对系统发送操作，例如：

* 将包裹放入某个柜子；
* 用户取出包裹；
* 预约一个空柜；
* 取消预约；
* 将某个柜子设为故障；
* 将故障柜恢复；
* 查询当前柜子状态。

整个系统不能依赖单台服务器。

多个 Replica 必须最终按照完全相同的命令顺序更新自己的 Parcel Locker State Machine。

系统必须能够在多个进程、线程或协议角色发生 crash、restart、网络延迟、消息重复和 Leader 切换时继续满足 Paxos Safety，并在故障条件恢复后最终取得 progress。

本项目要求实现：

* Multi-Paxos；
* Replica；
* Leader；
* Acceptor；
* Scout；
* Commander；
* ballot；
* slot；
* Phase 1；
* Phase 2；
* leader preemption；
* leader failover；
* crash-recovery；
* Replica recovery；
* duplicate message handling；
* delayed / reordered message handling；
* concurrent proposals；
* quorum；
* configurable fault tolerance `f`；
* deterministic replicated state machine。

本项目明确不要求：

* Snapshot；
* Checkpoint；
* Garbage Collection；
* Byzantine Fault Tolerance；
* 云端部署；
* 动态 membership / Paxos reconfiguration。

因此测试规模会受到控制，使完整日志能够一直保留。

---

# 2. 运行环境

必须使用：

```text
Oracle Java
version "25.0.4"
2026-07-21 LTS
```

项目使用 Gradle，并提供 Gradle Wrapper：

```text
gradlew
gradlew.bat
```

TA 必须能够执行：

```text
./gradlew test
```

以及：

```text
./gradlew grade
```

Windows：

```text
gradlew.bat test
gradlew.bat grade
```

不允许要求：

* Docker；
* Kubernetes；
* Redis；
* Kafka；
* PostgreSQL；
* MySQL；
* Cloudflare；
* AWS；
* 其他外部服务。

所有节点均运行于：

```text
localhost
```

---

# 3. 实际业务：Parcel Locker Ledger

假设系统管理：

```text
Locker 1
Locker 2
...
Locker N
```

每个 Locker 状态至少包含：

```text
lockerId
status
packageId?
reservedForClientId?
```

LockerStatus：

```text
AVAILABLE
RESERVED
OCCUPIED
OUT_OF_SERVICE
```

---

# 4. Client Commands

至少实现以下 command。

## ReserveLocker

```text
ReserveLocker(
    requestId,
    clientId,
    lockerId
)
```

要求：

```text
AVAILABLE -> RESERVED
```

否则 command 执行失败，但 command 本身仍然已经存在于 replicated log。

---

## CancelReservation

```text
CancelReservation(
    requestId,
    clientId,
    lockerId
)
```

只有该 client 拥有 reservation 时成功。

---

## StorePackage

```text
StorePackage(
    requestId,
    courierId,
    packageId,
    lockerId
)
```

合法情况：

```text
AVAILABLE -> OCCUPIED
```

或者：

```text
RESERVED -> OCCUPIED
```

前提是业务规则允许该 reservation 使用此 package。

---

## PickupPackage

```text
PickupPackage(
    requestId,
    clientId,
    packageId,
    lockerId
)
```

合法时：

```text
OCCUPIED -> AVAILABLE
```

---

## MarkOutOfService

```text
MarkOutOfService(
    requestId,
    operatorId,
    lockerId
)
```

只能按照模板定义的 deterministic rule 执行。

---

## RestoreLocker

```text
RestoreLocker(
    requestId,
    operatorId,
    lockerId
)
```

将符合条件的：

```text
OUT_OF_SERVICE
```

恢复为：

```text
AVAILABLE
```

---

# 5. 为什么这个场景需要 Consensus

考虑：

```text
Locker 17 = AVAILABLE
```

Client A 同时：

```text
ReserveLocker(A, Locker17)
```

Courier B 同时：

```text
StorePackage(P123, Locker17)
```

两个 Replica 可能同时认为：

```text
next available slot = 104
```

于是：

```text
Replica X:
slot 104 -> ReserveLocker

Replica Y:
slot 104 -> StorePackage
```

Multi-Paxos 必须最终让 slot 104 只对应其中一个 command。

另一个 command 不能丢失，而应该重新 propose 到后续 slot。

例如：

```text
slot 104 = ReserveLocker
slot 105 = StorePackage
```

此时第二个 command 的业务结果可能与反过来的顺序完全不同。

因此系统真正复制的是：

```text
ordered command log
```

而不是简单复制最终 Locker 状态。

---

# 6. Fault-Tolerance 参数 f

程序启动时接受：

```text
--fault-tolerance f
```

必须自动使用至少：

```text
2f + 1
```

个 Acceptors。

例如：

```text
f = 1
Acceptors = 3

f = 2
Acceptors = 5

f = 3
Acceptors = 7
```

quorum 定义为：

```text
floor(N / 2) + 1
```

系统必须在最多 `f` 个 Acceptors crash-stop 或暂时不可达时仍然能够取得进展。

如果活着的 Acceptors 少于 quorum：

```text
Safety 必须继续成立
Liveness 可以暂时停止
```

禁止为了保持 availability 而降低 quorum。

---

# 7. Ballot

禁止仅使用整数：

```text
21
```

必须使用全局唯一且具有 total ordering 的 ballot。

推荐：

```java
record BallotNumber(long round, String leaderId)
        implements Comparable<BallotNumber> {}
```

比较顺序：

```text
先比较 round
round 相同则比较 leaderId
```

因此：

```text
(21, B) < (21, C)
```

必须可以确定。

不同 Leader 的 ballot namespace 不能产生无法排序的冲突。

---

# 8. PValue

必须存在等价于：

```java
record PValue(
    BallotNumber ballot,
    long slot,
    Command command
) {}
```

逻辑含义：

```text
<ballot, slot, proposal>
```

---

# 9. Replica

Replica 是 application state machine。

每个 Replica 至少维护：

```text
state
slotIn / nextProposalSlot
slotOut / nextExecutionSlot
proposals
decisions
executedRequestIds
```

Replica 必须：

1. 接收 Client command；
2. 为 command 找候选 slot；
3. 向 Leader(s) 发送：

```text
PROPOSE(slot, command)
```

4. 接收：

```text
DECISION(slot, command)
```

5. 严格按照 slot 顺序执行；
6. 允许 decision 乱序到达；
7. 不允许跳过 hole 执行后面的 command；
8. proposal 输掉时重新 propose；
9. 相同 `requestId` 不能产生重复业务副作用；
10. 重启后能够恢复。

例如：

```text
decision[104] = A
decision[106] = C
```

但：

```text
decision[105]
```

不存在。

Replica：

```text
可以知道 slot 106
但不能执行 slot 106
```

必须等待或恢复 slot 105。

---

# 10. Replica Crash-Recovery

Replica 可以在任何时刻 crash。

例如：

```text
Replica X executed through slot 104
```

随后：

```text
X crash
```

其他 Replica 继续：

```text
105
106
107
```

X restart 后必须能够：

```text
recover local durable state
+
obtain missing decisions
+
replay 105..107
+
catch up
```

本项目不实现 snapshot。

因此允许 Replica：

```text
保存完整 decision log
```

或：

```text
从其他 Replica / Leader 获取缺失 decision
```

最终重新执行完整 log。

必须支持：

```text
decision catch-up
```

不允许要求整个集群重启。

---

# 11. Replica Persistence

至少必须 durable 保存足够信息，使 crash-recovery 不产生重复业务执行。

建议：

```text
decided log
lastExecutedSlot
requestId deduplication information
application state
```

或者：

```text
完整 decided log
```

并在 restart 后 deterministic replay。

TA 不规定具体磁盘格式。

允许：

```text
binary file
JSON lines
custom WAL
Java NIO file
```

但必须确保 crash-recovery 正确。

---

# 12. Leader

Leader 至少维护：

```text
ballotNumber
active
proposals
```

Leader 初始：

```text
active = false
```

必须通过 Scout 成功完成 Phase 1 后才能：

```text
active = true
```

Leader负责：

* 接收 Replica proposals；
* 对同一 `(ballot, slot)` 只推进一个 proposal；
* 创建 Scout；
* 收到 ADOPTED 后运行 `pmax`；
* 恢复旧 accepted history；
* 创建 Commander；
* 收到 PREEMPTED 后停止当前 ballot；
* 生成更高 ballot 或暂时退让；
* 支持 leader failover；
* 支持多个 Leader 同时竞争而不破坏 Safety。

---

# 13. Leader Failure Detection

Leader candidates 必须具有 failure detector。

允许使用：

```text
heartbeat + timeout
```

但必须理解：

```text
timeout ≠ 证明 Leader 已死亡
```

它只表示：

```text
suspect leader
```

因此可能出现：

```text
Leader A 网络延迟

B suspects A
C suspects A

B 和 C 同时竞争 leadership
```

系统必须安全处理。

---

# 14. Multiple Leaders

允许：

```text
A thinks A is leader
B thinks B is leader
C thinks C is leader
```

同时存在。

Safety 不能依赖“全系统任何时刻只有一个人认为自己是 Leader”。

真正的 authority 来自：

```text
ballot
+
Acceptor quorum
```

较低 ballot Leader最终会从 Scout 或 Commander 收到：

```text
PREEMPTED(higherBallot)
```

并使当前 ballot inactive。

---

# 15. Leader Liveness

为了避免：

```text
B -> ballot 21
C -> ballot 22
B -> ballot 23
C -> ballot 24
...
```

无限 dueling leaders，系统必须实现某种：

```text
backoff
leader heartbeat
preferred leader
randomized retry
```

机制。

具体算法由学生决定。

要求：

当：

```text
网络最终稳定
+
至少 quorum Acceptors 可用
+
至少一个 Leader candidate 可用
```

时，最终必须存在一个能够稳定推进 commands 的 Leader。

---

# 16. Acceptor

Acceptor 必须维护 durable：

```text
ballotNumber
accepted
```

其中：

```text
ballotNumber
```

代表 adopted/promised 的最高 ballot。

`accepted` 包含：

```text
PValue
```

Acceptor必须正确处理：

```text
P1A
P2A
```

并返回：

```text
P1B
P2B
```

---

# 17. Acceptor Persistence

以下状态必须 crash-safe：

```text
ballotNumber
accepted
```

测试会执行：

```text
acceptor accepts PValue
crash
restart
```

然后验证 Acceptors 没有忘记历史。

禁止：

```text
restart -> empty accepted
restart -> ballot reset
```

否则直接破坏 Safety。

---

# 18. Acceptor Invariants

实现必须保持 Cornell 对应的不变量。

至少验证：

### A1

Acceptor adopted ballot 单调增加。

### A2

Acceptor 只能接受当前 adopted ballot 对应的 pvalue。

### A3

在本项目没有 GC 的前提下：

```text
accepted
```

不能删除已经接受的记录。

### A4

对于给定：

```text
ballot b
slot s
```

不能存在不同 proposal：

```text
<b,s,p>
<b,s,p'>
```

其中：

```text
p != p'
```

### A5

如果：

```text
<b,s,p>
```

已经被 majority Acceptors 接受，那么任何：

```text
b' > b
```

下后来被接受的：

```text
<b',s,p'>
```

必须满足：

```text
p' = p
```

---

# 19. Scout

Scout 是一个 ephemeral Phase-1 worker。

Scout 创建时绑定：

```text
leader
ballot
acceptor set
```

Scout：

1. 向所有 Acceptors 发送：

```text
P1A(ballot)
```

2. 收集：

```text
P1B(
    acceptor,
    currentBallot,
    accepted
)
```

3. 如果得到当前 ballot 的 quorum：

```text
ADOPTED(ballot, pvalues)
```

发送给 Leader；

4. 如果发现 higher ballot：

```text
PREEMPTED(higherBallot)
```

5. 然后退出。

Scout本身不生成新的 ballot。

Scout本身不负责决定是否再次竞争 Leader。

---

# 20. Scout Failure

测试框架必须能够在任意阶段 kill Scout，例如：

```text
before any P1A
after one P1A
after quorum promises
before ADOPTED
```

Scout failure：

```text
不得破坏 Safety
```

Leader必须能够超时或检测 worker failure，并重新完成 Phase 1。

新 Scout 可以使用同一 ballot，或由 Leader根据当前状态决定重新竞争。

---

# 21. Commander

Commander 是一个 ephemeral Phase-2 worker。

每个 Commander绑定一个：

```text
<ballot, slot, command>
```

Commander：

1. 向 Acceptors 发送：

```text
P2A(pvalue)
```

2. 收集：

```text
P2B(acceptor,currentBallot)
```

3. 当前 ballot 获得 quorum：

```text
command chosen
```

4. 向 Replicas 广播：

```text
DECISION(slot,command)
```

5. 退出。

如果看到 higher ballot：

```text
PREEMPTED(higherBallot)
```

发送给 Leader并退出。

---

# 22. Commander Invariants

至少保持：

### C1

对于：

```text
(ballot, slot)
```

Leader最多生成一个不同 proposal 的 Commander。

### C2

如果一个较低 ballot 的某 proposal 已经被 majority accepted，那么以后更高 ballot 创建同 slot Commander 时必须使用相同 command。

---

# 23. Commander Failure

测试框架必须支持 kill Commander：

```text
before P2A
after one P2A
after minority accepts
after quorum accepts
before DECISION
during DECISION broadcast
```

特别测试：

```text
A1 accepted
A2 accepted
=> value already chosen

Commander crashes before Replicas learn decision
```

系统必须保证：

```text
chosen value cannot be replaced
```

后续 Leader/Scout/Commander 必须能够通过 Acceptor history 继续恢复该 value。

---

# 24. Partial Decision Delivery

允许出现：

```text
R1 receives DECISION(104,C)
R2 receives DECISION(104,C)
R3 does not
```

R3 最终必须 catch up。

不得要求：

```text
Commander waits for every Replica ACK
```

才能宣布 chosen。

Chosen 的定义仅取决于：

```text
Acceptor quorum
```

Replica learning 与 consensus 是不同阶段。

---

# 25. pmax

Scout 返回 accepted pvalues 后，Leader必须针对每个 slot 找到最高 ballot 对应的 proposal。

例如：

```text
<10,104,A>
<15,104,B>
<13,105,C>
```

则：

```text
pmax:

104 -> B
105 -> C
```

这些 proposal 必须覆盖 Leader 对对应 slot 的本地 proposal。

这是 Leader在新 ballot 中尊重历史的关键逻辑。

---

# 26. Proposal Collision

必须测试：

```text
Replica X:
slot104 -> command A

Replica Y:
slot104 -> command B
```

最终只能有：

```text
slot104 -> A
```

或：

```text
slot104 -> B
```

输掉的 command 必须重新 propose。

最终可能：

```text
104 -> A
105 -> B
```

不得丢失其中一个有效 Client command。

---

# 27. Slot Holes

允许 transient：

```text
104 decided
105 unknown
106 decided
```

Replica不能执行 106。

最终系统必须填补或决定 105，才能继续执行。

允许实现：

```text
NO_OP
```

作为合法 command 用于 gap filling。

---

# 28. Client Request Identity

每个 Client command 必须包含：

```text
requestId
```

要求 globally unique。

推荐：

```text
UUID
```

Client 可因 timeout 重试相同 request。

系统必须保证：

```text
same requestId
```

不会产生重复业务副作用。

---

# 29. Message Delivery Model

测试 transport 必须能够模拟：

```text
normal
drop
delay
duplicate
reorder
partition
heal partition
```

协议不能假设：

```text
exactly-once delivery
```

必须能够正确处理重复：

```text
P1A
P1B
P2A
P2B
PROPOSE
DECISION
heartbeat
```

---

# 30. 本地网络

Integration tests 必须使用真实 localhost 通信。

推荐：

```text
TCP
127.0.0.1
dynamic ports
```

端口不得硬编码。

Unit tests 可以使用：

```text
InMemoryTransport
```

以便确定性测试特殊 message ordering。

---

# 31. Process / Role Failure Injection

grader 必须能够：

```text
crashAcceptor(id)
restartAcceptor(id)

crashReplica(id)
restartReplica(id)

crashLeader(id)
restartLeader(id)

killScout(leaderId, ballot)

killCommander(
    leaderId,
    ballot,
    slot
)
```

还必须能够：

```text
partition(A,B)
heal(A,B)

dropNext(messageType,...)

delayNext(messageType,...)

duplicateNext(messageType,...)
```

---

# 32. Crash Semantics

区分：

## crash-stop

节点停止，不重新启动。

## crash-recovery

节点停止后重新启动。

对于：

```text
Acceptor
Replica
```

restart 必须恢复 durable state。

Leader的 ephemeral state 可以丢失。

Scout / Commander 不需要 durable state。

它们应该能够被重新创建。

---

# 33. Cold Start Requirement

除专门的 persistence/restart test 外，每一个独立 test：

```text
必须从空数据目录开始
```

流程：

```text
delete test data
allocate new ports
start cluster
execute test
assert
stop cluster
delete data
```

不同测试之间不得共享历史状态。

---

# 34. Deterministic Observability API

为 grader 提供只读 debug/status API。

至少可以查询：

Replica：

```text
lastExecutedSlot
decisions
applicationState
knownLeader
pendingProposals
```

Leader：

```text
ballot
active
proposals
runningScout
runningCommanders
```

Acceptor：

```text
ballot
accepted
```

这些 debug API 不得被协议本身用于作弊。

它们只用于测试和诊断。

---

# 35. Event Log

所有节点必须产生 structured event log。

事件至少包含：

```text
timestamp
nodeId
role
eventType
ballot?
slot?
requestId?
peer?
```

例如：

```text
LEADER_SCOUT_STARTED
ACCEPTOR_BALLOT_ADOPTED
P1A_SENT
P1B_RECEIVED
COMMANDER_STARTED
PVALUE_ACCEPTED
VALUE_CHOSEN
DECISION_LEARNED
COMMAND_EXECUTED
LEADER_PREEMPTED
NODE_CRASHED
NODE_RESTARTED
```

grader 在失败时应输出最后若干事件，便于学生调试。

---

# 36. Correctness Definition

## Safety

无论：

```text
crash
restart
delay
duplicate
reorder
partition
multiple leaders
worker failure
```

如何发生，都必须保证：

### S1

同一个 slot 不能决定两个不同 command。

### S2

所有正确 Replica 对同一个 slot 的最终 decision 相同。

### S3

Replica只能按 slot 顺序执行。

### S4

同一个 requestId 最多产生一次业务副作用。

### S5

Acceptor restart 不得遗忘已经影响 Safety 的 ballot / accepted state。

---

# 37. Liveness

当环境最终满足：

```text
至少 quorum Acceptors alive
至少一个 Replica alive
至少一个 Leader candidate alive
网络最终恢复可靠通信
故障停止继续发生
```

时：

```text
新的 Client request 最终必须得到 decision
```

且：

```text
正确 Replica 最终 catch up
```

测试允许合理 timeout，但不能无限等待。

---

# 38. Required Unit Tests

grader 至少包含以下 Unit Tests。

## Ballot tests

1. ballot ordering；
2. same round different Leader ID；
3. higher round；
4. equality；
5. serialization round-trip。

## Acceptor tests

6. first P1A adopted；
7. lower P1A rejected/not adopted；
8. higher P1A adopted；
9. equal ballot idempotent；
10. valid P2A accepted；
11. lower P2A rejected；
12. accepted persistence；
13. ballot persistence；
14. duplicate P2A idempotent；
15. A1 monotonicity；
16. A2；
17. A3。

## pmax tests

18. empty set；
19. one slot one pvalue；
20. same slot multiple ballots；
21. multiple slots；
22. ties impossible / rejected。

## Replica tests

23. basic propose；
24. same-slot proposal lost and re-proposed；
25. out-of-order decisions；
26. hole prevents execution；
27. duplicate decision；
28. duplicate client request；
29. deterministic command failure；
30. restart + replay。

## Leader tests

31. passive before adopted；
32. adopted activates；
33. pmax overrides local proposal；
34. active proposal spawns Commander；
35. C1；
36. preemption marks ballot inactive；
37. next ballot > observed ballot。

## Scout tests

38. quorum success；
39. insufficient responses；
40. higher ballot preemption；
41. duplicate P1B；
42. one Acceptor failure；
43. Scout killed and retried。

## Commander tests

44. quorum chosen；
45. minority not chosen；
46. higher ballot preemption；
47. duplicate P2B；
48. Commander killed before quorum；
49. Commander killed after quorum before all decisions delivered。

---

# 39. Basic Integration Tests

每项冷启动完整集群。

## I1 Single command

提交：

```text
ReserveLocker
```

所有 Replica最终状态一致。

## I2 Sequential commands

至少 100 条 command。

验证：

```text
same decisions
same state
same slot order
```

## I3 Concurrent clients

至少 20 Clients 同时发送 requests。

## I4 Same locker conflict

多个 Clients 同时操作同一个 Locker。

最终所有 Replica产生完全相同结果。

## I5 Same-slot competing proposals

人为强制两个 Replica 同时 propose slot 1 不同 command。

## I6 Out-of-order DECISION

故意使：

```text
slot3
```

先于：

```text
slot2
```

到某 Replica。

---

# 40. Leader Failure Tests

## L1

Leader crash before Scout completes。

## L2

Leader crash immediately after Scout adopted。

## L3

Leader crash while Commanders running。

## L4

Leader crash after value chosen but before all Replicas learn。

## L5

Old Leader returns after new Leader established。

旧 Leader不得破坏新 ballot。

## L6

Two Leaders suspect current Leader simultaneously。

## L7

Three competing Leaders。

## L8

Repeated preemption。

## L9

Eventually stable Leader。

---

# 41. Acceptor Failure Tests

对于配置：

```text
f = 1
N = 3
```

测试：

## A-F1

1 Acceptor down：

```text
must progress
```

## A-F2

2 Acceptors down：

```text
must not produce conflicting decision
liveness may stop
```

恢复一个后：

```text
must resume
```

对于：

```text
f = 2
N = 5
```

测试：

## A-F3

2 Acceptors down：

```text
must progress
```

## A-F4

3 down：

```text
must not progress incorrectly
```

恢复 quorum 后继续。

---

# 42. Acceptor Crash-Recovery Tests

## AR1

Accept PValue -> crash -> restart。

accepted 必须存在。

## AR2

Promise high ballot -> crash -> restart -> receive old ballot。

必须拒绝旧 ballot。

## AR3

Crash during Phase 1。

## AR4

Crash during Phase 2。

## AR5

Repeated crash/restart cycles。

至少：

```text
100 cycles
```

---

# 43. Replica Failure Tests

## R-F1

Replica crash before any decision。

## R-F2

Replica crash after slot 50。

集群推进至 100。

restart 后 catch up。

## R-F3

Replica misses decisions 51..80。

## R-F4

Replica receives 81..100 before missing range。

不得跳过执行。

## R-F5

Repeated replica restart。

## R-F6

Two replicas down while consensus continues。

恢复后都必须 catch up。

---

# 44. Scout Failure Tests

必须精确注入：

## S-F1

kill before P1A。

## S-F2

kill after first P1A。

## S-F3

kill after minority P1B。

## S-F4

kill immediately after quorum reached。

## S-F5

kill before ADOPTED reaches Leader。

## S-F6

retry Phase 1 后继续 progress。

---

# 45. Commander Failure Tests

## C-F1

kill before P2A。

## C-F2

kill after one P2A。

## C-F3

kill after minority accepted。

## C-F4

kill after quorum accepted but before DECISION。

这是核心测试。

确认：

```text
chosen value survives
```

## C-F5

DECISION only reaches one Replica then kill。

## C-F6

DECISION reaches subset of Replicas then kill。

## C-F7

Leader同时 crash。

新 Leader必须通过 Scout恢复 value。

---

# 46. Network Fault Tests

## N1 Packet Delay

随机 0–500ms。

## N2 Reorder

强制：

```text
later message arrives first
```

## N3 Duplicate

随机复制：

```text
P1A/P1B/P2A/P2B/DECISION
```

## N4 Drop

随机丢消息。

最终关闭 drop 后系统必须恢复 progress。

## N5 Partition Old Leader

```text
Leader A
```

与 quorum 隔离。

B/C 能产生新 Leader。

恢复 A 后 A 不得产生冲突。

## N6 Split Leaders

不同 Leader看到不同网络视图。

## N7 Replica-only partition

Replica落后但 consensus继续。

恢复后 catch up。

## N8 Acceptor minority partition

quorum一侧继续。

minority一侧不得产生 conflicting chosen value。

---

# 47. Combined Failure Tests

单一故障远远不够。

至少：

## CF1

Leader crash + one Acceptor down。

## CF2

Commander crash + Replica partition。

## CF3

Scout crash + delayed old Leader messages。

## CF4

Leader failover + old Commander stale P2A。

## CF5

Acceptor restart + duplicate old P1A。

## CF6

Replica restart + concurrent new commands。

## CF7

Two Leaders + one Acceptor crash。

## CF8

Network partition + leader recovery + heal。

---

# 48. Safety Property Test

grader 必须有一个通用 invariant checker。

运行期间持续检查：

```text
slot -> at most one chosen command
```

以及：

```text
Replica decision agreement
```

如果任何时刻观察到：

```text
slot S -> C1
slot S -> C2

C1 != C2
```

立即 fail。

Safety violation 不允许通过 retry 自愈。

---

# 49. Randomized Chaos Test

固定 seed，可重复。

例如：

```text
seed = 123456
```

持续执行：

```text
submit command
crash node
restart node
delay message
drop message
duplicate message
partition
heal
kill scout
kill commander
```

每轮运行至少：

```text
30 seconds
```

恢复正常网络后：

```text
wait for convergence
```

检查：

```text
all surviving replicas agree
all accepted requests represented
no duplicate effects
```

至少运行多个不同 deterministic seeds。

---

# 50. Stress Test

Stress test 不需要冷启动每一个内部阶段，但整个 stress run 从空状态启动。

推荐最低规模：

```text
f = 2
5 Acceptors
3+ Replicas
3 Leaders
50 concurrent Clients
10,000 commands
```

测试至少包含：

```text
random leader failures
random acceptor crash/restart
random replica crash/restart
random Scout failure
random Commander failure
message delay
message duplication
temporary packet loss
```

最终进入：

```text
stabilization phase
```

即：

```text
停止注入故障
恢复全部网络
恢复 quorum
```

然后要求：

```text
系统最终处理完所有可重试 requests
Replica states converge
decision logs agree
no duplicate request side effect
```

---

# 51. Higher-f Stress Test

额外测试：

```text
f = 3
Acceptors = 7
```

随机最多同时 crash：

```text
3 Acceptors
```

仍应取得 progress。

同时 crash：

```text
4 Acceptors
```

必须停止新 consensus，而不是违反 Safety。

---

# 52. Long-Run Test

持续创建至少：

```text
50,000 slots
```

不做 snapshot / GC。

目的：

```text
测试 accepted history
decision log
pmax
restart
memory correctness
```

不要求高性能，但不能出现明显的无限循环或指数级行为。

---

# 53. Performance Metrics

不作为主要 correctness 分数，但 grader 收集：

```text
throughput
p50 latency
p95 latency
p99 latency
leader failover latency
replica catch-up latency
message count
```

测试输出报告。

---

# 54. Timeout Philosophy

测试不得使用极端脆弱的：

```text
sleep(100)
assert(...)
```

优先使用：

```text
eventually(condition, timeout)
```

并轮询 observable state。

Integration test 的 timeout 必须足以适应普通开发机。

---

# 55. Grading

总分：

```text
100
```

建议：

```text
Build / API / Template Compatibility       5

Core Data Structures                       5

Replica Correctness                       10

Acceptor Correctness                      15

Leader / pmax                             10

Scout                                     10

Commander                                 10

Basic Multi-Paxos Integration             10

Crash-Recovery / Leader Failover          10

Network / Adversarial Safety              10

Stress / Chaos / Liveness                  5
```

任何可复现的：

```text
two different decisions for same slot
```

视为严重 Safety violation。

建议对 Safety 类别设置 cap。

例如发生确定性 Safety violation 时：

```text
总成绩最高不得超过 60/100
```

---

# 56. Starter Template 原则

TA 提供：

```text
interfaces
records
message types
transport abstraction
process lifecycle abstraction
persistence abstraction
failure injection hooks
CLI
test harness
application state machine skeleton
Gradle setup
README
```

但不得提供 Paxos 核心答案。

学生必须自己完成至少：

```text
Replica TODO
Acceptor TODO
Leader TODO
Scout TODO
Commander TODO
pmax TODO
leader failover TODO
recovery TODO
```

---

# 57. 推荐项目结构

```text
paxos-parcel-locker/
│
├── settings.gradle.kts
├── build.gradle.kts
├── gradlew
├── gradlew.bat
│
├── README.md
├── ASSIGNMENT.md
│
├── starter/
│   └── src/
│       ├── main/java/...
│       └── test/java/...
│
├── grader/
│   └── src/
│       └── test/java/...
│
├── testkit/
│   └── src/main/java/...
│
└── scripts/
    └── ...
```

Gradle：

```text
./gradlew test
```

运行学生/public tests。

```text
./gradlew grade
```

运行全部 grader tests 并生成：

```text
build/reports/grading/
```

---

# 58. Grader Isolation

Grader 不得：

* 通过 reflection 直接修改学生内部 Paxos 状态；
* 使用 debug API 帮学生做 protocol decision；
* 替学生补发 consensus messages；
* 替学生选 Leader；
* 替学生计算 pmax。

FailureInjector 可以影响环境，但不能替算法完成工作。

---

# 59. Reproducibility

所有 randomized test：

```text
必须打印 seed
```

失败时输出：

```text
seed
cluster configuration
last events
current ballots
accepted sets
leader status
replica slot positions
```

学生必须能够：

```text
./gradlew reproduceFailure -Pseed=...
```

或等价方式复现。

---

# 60. 最终验收标准

项目成功的标准不是：

```text
“正常情况下跑通。”
```

而是：

> 在最多 `f` 个 Acceptor failure、Leader crash/re-election、Replica crash/recovery、Scout/Commander failure、消息重复、乱序、延迟、临时丢失和多个 Leader 并存的情况下，系统始终不产生冲突 decision；当网络和 quorum 最终恢复稳定后，新的 Client commands 最终能够被 chosen，并且所有正确 Replica 最终按照同一 slot 顺序执行同一 command log。

这就是本项目要验证的 Multi-Paxos Safety 与 Liveness。
