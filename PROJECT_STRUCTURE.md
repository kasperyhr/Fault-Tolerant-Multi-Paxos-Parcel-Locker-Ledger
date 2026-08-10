# 项目结构与学生实现指南

本文解释仓库中各目录和主要文件的用途，并明确区分：

- **已提供**：TA 已经完成，学生通常只需要调用，不应重写其协议语义；
- **学生实现**：Multi-Paxos 作业的核心评分内容；
- **测试代码**：用于说明行为、注入故障和检查实现，不属于学生协议实现。

完整功能要求与正确性定义以 [README.md](README.md) 和
[ASSIGNMENT.md](ASSIGNMENT.md) 为准。本文主要回答“代码放在哪里”和“哪些地方要写”。

## 1. 模块依赖关系

```text
starter  <-  testkit  <-  grader
```

- `starter` 是学生实现所在模块，不能依赖 `testkit` 或 `grader`。
- `testkit` 依赖 `starter` 的公开类型，用于启动节点、注入故障和观察结果。
- `grader` 同时依赖 `starter` 与 `testkit`，通过公开 API 测试学生代码。
- 协议实现不得读取 grader 的内部状态，也不得让 testkit 帮助决定 Paxos value。

所有 Java package 都直接位于 `paxoslocker` 下，例如：

```text
paxoslocker.acceptor
paxoslocker.app
paxoslocker.leader
paxoslocker.replica
paxoslocker.worker
```

## 2. 根目录

| 文件 | 含义 | 学生是否需要修改 |
|---|---|---|
| `README.md` | 完整 handout：系统目标、角色、Safety/Liveness、故障模型和测试要求。 | 否，先完整阅读。 |
| `ASSIGNMENT.md` | 精简的提交要求和 correctness contract。 | 否。 |
| `PROJECT_STRUCTURE.md` | 本文，代码导航和 TODO 指南。 | 否。 |
| `GRADING.md` | 100 分评分规则及 Safety violation 的分数上限。 | 否。 |
| `settings.gradle.kts` | 声明 `starter`、`testkit`、`grader` 三个 Gradle 子项目。 | 否。 |
| `build.gradle.kts` | 根项目任务入口：`grade`、`gradeFull`、`integrationTest`、`chaosTest`、`stressTest`。 | 通常否。 |
| `gradle.properties` | Gradle JVM、UTF-8、并行构建和缓存配置。 | 否。 |
| `gradlew` / `gradlew.bat` | Unix-like / Windows Gradle Wrapper 启动脚本。 | 否。 |
| `gradle/wrapper/gradle-wrapper.jar` | 固定版本的 Gradle Wrapper 程序。 | 否。 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 9.2.1 distribution 配置。 | 否。 |
| `.gitignore` | 排除 Gradle build、IDE、本地数据和日志。 | 通常否。 |

`.idea/` 是 IntelliJ IDEA 的本地项目配置，不参与协议行为或评分。
`.gradle/` 与各模块的 `build/` 是自动生成目录，不应在其中实现代码。

## 3. `starter`：学生提交与实现模块

### 3.1 `starter/build.gradle.kts`

配置 Java 25 toolchain、JUnit、public tests 和 CLI main class：

```text
paxoslocker.app.ParcelLockerMain
```

学生通常不需要修改此文件。

### 3.2 `paxoslocker.model`：领域和协议基础类型

路径：`starter/src/main/java/paxoslocker/model/`

这些类型已经提供，主要作为协议实现的稳定数据模型。

| 文件 | 含义 |
|---|---|
| `NodeId.java` | 节点 ID，支持比较、序列化和字符串表示。 |
| `Role.java` | CLIENT、REPLICA、LEADER、ACCEPTOR、SCOUT、COMMANDER 等角色枚举。 |
| `BallotNumber.java` | `(round, leaderId)` ballot，已实现 total ordering 和 `after`。 |
| `PValue.java` | Paxos 三元组 `<ballot, slot, command>`。 |
| `Command.java` | 所有业务命令的 sealed interface；每个命令必须有 `requestId`。 |
| `CommandResult.java` | 状态机执行结果，包括 requestId、成功标记和消息。 |
| `LockerStatus.java` | AVAILABLE、RESERVED、OCCUPIED、OUT_OF_SERVICE。 |
| `Locker.java` | 单个 locker 的不可变状态。 |
| `ReserveLocker.java` | 预约空柜。 |
| `CancelReservation.java` | 取消属于该 client 的预约。 |
| `StorePackage.java` | 将 package 放入合法 locker。 |
| `PickupPackage.java` | 取出匹配 package。 |
| `MarkOutOfService.java` | 将合法 locker 标记为不可用。 |
| `RestoreLocker.java` | 将 OUT_OF_SERVICE locker 恢复为 AVAILABLE。 |
| `NoOp.java` | 可用于填补 slot hole 的确定性空操作。 |
| `ParcelLockerStateMachine.java` | 已完成的确定性业务状态机和 transition validation。 |

**学生职责**：不要重新设计业务状态机。Replica 应按照已经决定的 slot 顺序调用
`ParcelLockerStateMachine.apply(command)`，并负责 request deduplication。

### 3.3 `paxoslocker.protocol`：消息模型

路径：`starter/src/main/java/paxoslocker/protocol/`

| 文件 | 含义 |
|---|---|
| `ProtocolMessage.java` | 所有网络协议消息的 sealed interface。 |
| `ProposeMessage.java` | Replica → Leader：`PROPOSE(slot, command)`。 |
| `DecisionMessage.java` | Commander/Learner → Replica：`DECISION(slot, command)`。 |
| `P1aMessage.java` | Scout → Acceptor：Phase 1 请求。 |
| `P1bMessage.java` | Acceptor → Scout：当前 ballot 和 accepted PValues。 |
| `P2aMessage.java` | Commander → Acceptor：请求接受一个 PValue。 |
| `P2bMessage.java` | Acceptor → Commander：返回当前 ballot 和 slot。 |
| `AdoptedMessage.java` | Scout → Leader：Phase 1 已获得 quorum。 |
| `PreemptedMessage.java` | Scout/Commander → Leader：观察到更高 ballot。 |
| `HeartbeatMessage.java` | Leader heartbeat/failure suspicion 所需消息。 |

**学生职责**：正确发送、接收和幂等处理这些消息。不要依赖 exactly-once delivery，
也不要通过改消息语义绕过 Phase 1 或 Phase 2。

### 3.4 `paxoslocker.acceptor`

路径：`starter/src/main/java/paxoslocker/acceptor/Acceptor.java`

这是主要学生实现文件之一。

学生必须完成：

- Acceptor 当前 promised/adopted ballot 和 accepted PValues 状态；
- `onP1a`：处理 promise、低/equal/高 ballot、返回 P1B；
- `onP2a`：校验 ballot、接受合法 PValue、返回 P2B；
- ballot 与 accepted PValues 的 durable persistence；
- `start` 时从 `PersistentStore` 恢复；
- duplicate P1A/P2A 的幂等处理；
- `status` 的不可变只读快照；
- A1、A2、A3，并配合整体实现保持 A4、A5。

不要在 restart 时清空 ballot 或 accepted history。

### 3.5 `paxoslocker.replica`

路径：`starter/src/main/java/paxoslocker/replica/Replica.java`

这是主要学生实现文件之一。

学生必须完成：

- `slotIn` / next proposal slot；
- `slotOut` / next execution slot；
- proposals、decisions、executed request IDs；
- `submit`：接收 client command、去重、分配候选 slot、发送 PROPOSE；
- `onDecision`：学习 decision、处理重复或乱序 decision；
- proposal 输掉时将原 command 重新 propose 到后续 slot；
- hole 存在时阻止后续 slot 提前执行；
- 严格按 slot 顺序调用业务状态机；
- durable decision/execution/dedup state；
- restart replay 与 missing decision catch-up；
- `status` 的不可变只读快照。

### 3.6 `paxoslocker.leader`

路径：`starter/src/main/java/paxoslocker/leader/Leader.java`

这是主要学生实现文件之一。

学生必须完成：

- ballot、active 标记和 proposal map；
- 启动 Scout，并在 Phase 1 成功前保持 passive；
- `onPropose`：记录每个 slot 的 proposal，active 时启动 Commander；
- `onAdopted`：调用 pmax、覆盖冲突的本地 proposal、激活 leader；
- `pmax`：每个 slot 选择最高 ballot 的 accepted command；
- `onPreempted`：停止旧 ballot、生成严格更高 ballot；
- leader heartbeat、failure suspicion、retry/backoff；
- multiple leaders 和 stale worker 的安全处理；
- C1，并配合 Phase 1/pmax 保持 C2；
- `status` 的不可变只读快照。

### 3.7 `paxoslocker.worker`

路径：`starter/src/main/java/paxoslocker/worker/`

#### `Scout.java`

学生必须完成 Phase 1 worker：

- 向全部 Acceptors 发送 P1A；
- 只计算不同 acceptor 的响应，duplicate P1B 不得重复计数；
- 当前 ballot 获得 quorum 后汇总 accepted PValues 并发送 ADOPTED；
- 发现 higher ballot 后发送 PREEMPTED；
- 支持在任意 hook 阶段被 kill；
- 正确维护 `ScoutStatus` 并退出。

#### `Commander.java`

学生必须完成 Phase 2 worker：

- 向全部 Acceptors 发送 P2A；
- 只计算不同 acceptor 的响应，duplicate P2B 不得重复计数；
- 当前 ballot 获得 quorum 后认定 value chosen；
- 向全部 Replicas 广播 DECISION；
- 发现 higher ballot 后发送 PREEMPTED；
- 正确处理 quorum 已形成但 decision 尚未完整传播时的 worker failure；
- 正确维护 `CommanderStatus` 并退出。

Scout 和 Commander 都是 ephemeral worker，不需要持久化自身状态。

### 3.8 `paxoslocker.transport`

路径：`starter/src/main/java/paxoslocker/transport/`

| 文件 | 含义 |
|---|---|
| `Transport.java` | 注册 receiver、发送 envelope、注销和关闭的抽象。 |
| `MessageEnvelope.java` | messageId、source、destination、payload 和创建时间。 |
| `LocalTcpTransport.java` | 已提供的 `127.0.0.1` 动态端口 TCP transport。 |

Transport 已由 TA 提供。学生应通过 `Transport.send` 完成协议通信，不应把正确 Paxos
逻辑塞入 transport，也不能假设网络可靠或消息只到达一次。

### 3.9 `paxoslocker.persistence`

路径：`starter/src/main/java/paxoslocker/persistence/`

| 文件 | 含义 |
|---|---|
| `PersistentStore.java` | 按 key 保存、读取和删除可序列化对象的抽象。 |
| `FileStore.java` | 已实现的 filesystem-backed atomic file utility。 |

**学生职责**：决定何时以及以什么 key 保存 Acceptor/Replica 的安全关键状态，并在回复
相关协议消息或对外暴露执行结果前保证必要状态已经 durable。

### 3.10 `paxoslocker.diagnostics`

路径：`starter/src/main/java/paxoslocker/diagnostics/`

| 文件 | 含义 |
|---|---|
| `ReplicaStatus.java` | last executed slot、decisions、应用状态、known leader、pending proposals。 |
| `LeaderStatus.java` | ballot、active、proposals、Scout/Commander 运行情况。 |
| `AcceptorStatus.java` | ballot 和 accepted PValues。 |
| `ScoutStatus.java` | ballot、已响应 acceptors 和 worker 状态。 |
| `CommanderStatus.java` | PValue、已响应 acceptors、chosen/运行状态。 |
| `WorkerEventType.java` | Scout/Commander 精确故障注入事件点。 |
| `WorkerHook.java` | worker 将观察事件报告给测试框架的 callback。 |

**学生职责**：在角色的 `status()` 中返回真实、不可变的当前快照，并在 Scout/Commander
规定的位置调用 hook。Status 和 hook 只能用于观察与故障注入，协议不能读取它们来决定 value。

### 3.11 `paxoslocker.app`

路径：`starter/src/main/java/paxoslocker/app/`

| 文件 | 含义 |
|---|---|
| `NodeLifecycle.java` | start、stop、restart、isRunning 生命周期接口。 |
| `ClusterOptions.java` | 校验 `acceptorCount >= 2f + 1` 并计算 quorum。 |
| `ParcelLockerMain.java` | CLI 参数入口和模板配置示例。 |

学生需要让角色生命周期与自己的线程、timer、transport registration 和恢复逻辑正确配合，
但不应更改 `2f+1` 与 majority quorum 的定义。

### 3.12 Public tests

路径：`starter/src/test/java/paxoslocker/`

| 文件 | 测试内容 |
|---|---|
| `model/BallotNumberTest.java` | ballot ordering、equality、serialization。 |
| `model/ParcelLockerStateMachineTest.java` | 确定性业务 transition。 |
| `persistence/FileStoreTest.java` | 保存/读取/删除和路径安全。 |

这些测试在未实现 Paxos 时也应该通过。学生可以添加自己的测试，但不能通过删除或弱化已有
测试来改变评分行为。

## 4. `testkit`：测试与故障注入基础设施

### 4.1 `testkit/build.gradle.kts`

声明对 `starter` 的 API 依赖和 framework self-tests。学生通常不修改。

### 4.2 `paxoslocker.testkit`

路径：`testkit/src/main/java/paxoslocker/testkit/`

| 文件 | 含义 |
|---|---|
| `ClusterConfig.java` | f、acceptor/replica/leader 数量、timeout、seed 和 quorum。 |
| `ClusterNodeFactory.java` | 由 harness 创建学生 Acceptor、Replica、Leader 的工厂。 |
| `ClusterHarness.java` | 独立 temp data directory、节点生命周期、submit、await、inspect、crash/restart、partition 与 cleanup。 |
| `InMemoryTransport.java` | 确定性测试 transport，支持 drop、delay、duplicate、partition 和 pause。 |
| `MessagePredicate.java` | 按 envelope/message type 选择故障注入目标。 |
| `FailureController.java` | 节点 crash/restart、worker kill、消息故障、partition/heal、pause/resume。 |
| `Event.java` | 一条结构化 trace record。 |
| `EventType.java` | transport、node、chosen、decision、execution 等 trace 类型。 |
| `EventRecorder.java` | 有界、按 sequence 排序的事件记录器。 |
| `SafetyInvariantChecker.java` | 独立检查 chosen uniqueness、Replica agreement、连续执行、request 去重、ballot 单调。 |
| `SafetyViolationException.java` | Safety checker 发现不可恢复违规时立即抛出的错误。 |
| `Await.java` | 基于条件和 timeout 的 eventually helper，避免脆弱的固定 sleep。 |
| `PortAllocator.java` | 获取 localhost 动态可用端口。 |

testkit 是环境和观察工具，不会替学生选择 Leader、计算 pmax、发送缺失的 consensus message
或生成 decision。

### 4.3 `FrameworkSelfTest.java`

路径：`testkit/src/test/java/paxoslocker/testkit/FrameworkSelfTest.java`

验证动态端口、event trace、drop/delay/duplicate/partition、pause/resume、localhost TCP 和
Safety checker。即使学生尚未完成任何 Paxos TODO，此测试也必须通过。

## 5. `grader`：TA 评分测试

### 5.1 `grader/build.gradle.kts`

定义以下测试任务和 JUnit tags：

| Task | 作用 |
|---|---|
| `test` | framework grader contract。 |
| `studentUnitTest` | Acceptor、Replica、Leader/pmax、Scout、Commander unit contracts。 |
| `integrationTest` | 完整集群 integration tests。 |
| `chaosTest` | 固定 seed 的 deterministic chaos tests。 |
| `stressTest` | 长时间和高并发 stress tests。 |
| `grade` | 有界默认评分套件并生成 summary。 |
| `gradeFull` | `grade` 加完整 stress tests。 |

评分输出位于：

```text
build/reports/grading/summary.txt
build/reports/grading/summary.json
grader/build/reports/tests/
```

### 5.2 `grader/TEST-MATRIX.md`

列出完整 hidden-style 测试类别：基础共识、Leader failure、Acceptor/Replica recovery、
Scout/Commander failure、network adversarial、combined failures、chaos 与 stress。

### 5.3 当前 grader test files

路径：`grader/src/test/java/paxoslocker/grader/`

| 文件 | 含义 |
|---|---|
| `FrameworkContractTest.java` | seed 和冷启动 temp directory 等 grader 自检。 |
| `StudentProtocolUnitTest.java` | Acceptor promise/persistence 和 pmax 等学生实现 contract。 |
| `SingleCommandIT.java` | 单命令完整集群收敛测试。 |
| `ChaosIT.java` | 验证随机 seed 可确定性复现。 |
| `StressConfigurationTest.java` | 验证 f=2/f=3 quorum 和长测配置。 |

在空 starter 上，`StudentProtocolUnitTest` 和 `SingleCommandIT` 到达
`TODO(student)` 后失败是正常现象；学生实现完成后，这些测试应逐步变绿。

## 6. 学生必须完成的集中清单

| 优先级 | 文件 | 必须完成 |
|---|---|---|
| 1 | `acceptor/Acceptor.java` | P1A、P2A、durable ballot/accepted、restart、status。 |
| 2 | `leader/Leader.java` | Phase orchestration、pmax、active/passive、preemption、retry/failover、status。 |
| 3 | `worker/Scout.java` | Phase 1 quorum、dedup responses、ADOPTED/PREEMPTED、kill/hooks/status。 |
| 4 | `worker/Commander.java` | Phase 2 quorum、chosen、DECISION、preemption、kill/hooks/status。 |
| 5 | `replica/Replica.java` | propose、ordered learn/execute、re-propose、holes、dedup、persistence、catch-up、status。 |
| 6 | 角色之间的 wiring | transport registration、message dispatch、timers、heartbeat、recovery coordination。 |

不要实现 snapshot、checkpoint、garbage collection、dynamic membership 或 Byzantine
fault tolerance；这些不在本作业范围内。

## 7. 建议实现顺序

1. 先保证 `./gradlew test` 始终通过。
2. 完成 Acceptor 与 persistence，运行 `studentUnitTest`。
3. 完成纯函数 `Leader.pmax`。
4. 完成 Scout、Leader adoption/preemption。
5. 完成 Commander 和单 slot decision。
6. 完成 Replica 顺序执行、re-proposal 和 deduplication。
7. 完成 restart、catch-up、heartbeat 和 leader failover。
8. 依次运行 integration、network、chaos，最后运行 stress。

常用命令：

```bash
./gradlew clean classes
./gradlew test
./gradlew :grader:studentUnitTest
./gradlew integrationTest
./gradlew chaosTest -Pseed=123456
./gradlew grade
./gradlew gradeFull
```

Windows 使用对应的 `gradlew.bat`。
