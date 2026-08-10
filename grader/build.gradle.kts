import groovy.json.JsonOutput
import java.util.concurrent.ConcurrentLinkedQueue

plugins { java }

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    testImplementation(project(":starter"))
    testImplementation(project(":testkit"))
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val gradingResults = ConcurrentLinkedQueue<Map<String, Any>>()

fun Test.configureGrader(includeTag: String? = null) {
    outputs.upToDateWhen { false }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        if (includeTag != null) includeTags(includeTag)
    }
    maxParallelForks = 1
    systemProperty("paxos.seed", providers.gradleProperty("seed").orElse("123456").get())
    systemProperty("file.encoding", "UTF-8")
    testLogging { events("failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    afterTest(KotlinClosure2<TestDescriptor, TestResult, Unit>({ descriptor, result ->
        gradingResults += mapOf(
            "test" to "${descriptor.className}.${descriptor.name}",
            "status" to result.resultType.name,
            "failures" to result.exceptions.map { it.message.orEmpty() }
        )
    }))
}

tasks.test { configureGrader("framework") }

val studentUnitTest by tasks.registering(Test::class) {
    description = "Runs unit contracts for student protocol TODOs."
    configureGrader("student")
    ignoreFailures = true
    mustRunAfter(tasks.test)
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs localhost/in-memory integration tests."
    configureGrader("integration")
    ignoreFailures = true
    mustRunAfter(studentUnitTest)
}

val chaosTest by tasks.registering(Test::class) {
    description = "Runs deterministic seeded chaos tests."
    configureGrader("chaos")
    ignoreFailures = true
    mustRunAfter(integrationTest)
}

val stressTest by tasks.registering(Test::class) {
    description = "Runs long stress tests; excluded from the default grade task."
    configureGrader("stress")
    ignoreFailures = true
    maxHeapSize = "2g"
    mustRunAfter(chaosTest)
}

val writeGradingSummary by tasks.registering {
    val outputDir = rootProject.layout.buildDirectory.dir("reports/grading")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.apply { mkdirs() }
        val results = gradingResults.toList()
        val failures = results.filter { it["status"] == "FAILURE" }
        val passes = results.filter { it["status"] == "SUCCESS" }
        val seed = providers.gradleProperty("seed").orElse("123456").get()
        val rubric = linkedMapOf(
            "Build/API" to 5, "Core Types" to 5, "Replica" to 10,
            "Acceptor" to 15, "Leader/pmax" to 10, "Scout" to 10,
            "Commander" to 10, "Basic Integration" to 10,
            "Crash Recovery / Failover" to 10,
            "Network / Adversarial Safety" to 10, "Stress / Chaos" to 5
        )
        fun sectionFor(test: String): String = when {
            "Framework" in test -> "Build/API"
            "CoreTypes" in test -> "Core Types"
            "AcceptorUnit" in test -> "Acceptor"
            "ReplicaUnit" in test -> "Replica"
            "LeaderUnit" in test || "PmaxUnit" in test -> "Leader/pmax"
            "ScoutUnit" in test -> "Scout"
            "CommanderUnit" in test -> "Commander"
            listOf("SingleCommand", "SequentialCommands", "ConcurrentClients", "SameLockerConflict", "CompetingReplicaProposal", "OutOfOrderDecision", "LocalTcpSingleCommand").any { it in test } -> "Basic Integration"
            listOf("DelayMessages", "DuplicateMessages", "ReorderedMessages", "TemporaryDrop", "Partition", "Stale", "MinorityAcceptorPartition").any { it in test } -> "Network / Adversarial Safety"
            "Chaos" in test || "Stress" in test || "LongLog" in test -> "Stress / Chaos"
            else -> "Crash Recovery / Failover"
        }
        val sectionPoints = rubric.mapValues { (section, maximum) ->
            val sectionResults = results.filter { sectionFor(it["test"].toString()) == section }
            val passed = sectionResults.count { it["status"] == "SUCCESS" }
            val awarded = if (sectionResults.isEmpty()) 0.0 else maximum * passed.toDouble() / sectionResults.size
            mapOf("awarded" to Math.round(awarded * 100.0) / 100.0, "maximum" to maximum,
                "passed" to passed, "total" to sectionResults.size)
        }
        val rawTotal = sectionPoints.values.sumOf { (it["awarded"] as Number).toDouble() }
        val safetyViolation = failures.any { failure -> (failure["failures"] as List<*>).any { "two chosen values" in it.toString() } }
        val cappedTotal = if (safetyViolation) minOf(rawTotal, 60.0) else rawTotal
        val payload = linkedMapOf(
            "totalPoints" to Math.round(cappedTotal * 100.0) / 100.0,
            "maximumPoints" to 100,
            "sectionPoints" to sectionPoints,
            "failedTests" to failures,
            "passedTests" to passes.map { it["test"] },
            "seed" to seed,
            "safetyViolation" to safetyViolation,
            "scoreCapApplied" to (safetyViolation && rawTotal > 60.0),
            "diagnostics" to "See grader/build/reports/tests and the event trace printed by failing tests."
        )
        dir.resolve("summary.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(payload)))
        dir.resolve("summary.txt").writeText(buildString {
            appendLine("Paxos Parcel Locker grading summary")
            appendLine("Seed: $seed")
            appendLine("Score: ${Math.round(cappedTotal * 100.0) / 100.0}/100")
            appendLine("Tests observed: ${results.size}")
            appendLine("Passed: ${passes.size}")
            appendLine("Failures: ${failures.size}")
            failures.forEach { appendLine("- ${it["test"]}") }
            sectionPoints.forEach { (name, points) -> appendLine("$name: ${points["awarded"]}/${points["maximum"]} (${points["passed"]}/${points["total"]} tests)") }
            appendLine("Safety violation: $safetyViolation")
            appendLine("60-point cap applied: ${safetyViolation && rawTotal > 60.0}")
        })
    }
}

val grade by tasks.registering {
    group = "verification"
    dependsOn(tasks.test, studentUnitTest, integrationTest, chaosTest)
    finalizedBy(writeGradingSummary)
}

val gradeFull by tasks.registering {
    group = "verification"
    dependsOn(grade, stressTest)
    finalizedBy(writeGradingSummary)
}
