import groovy.json.JsonOutput

plugins { java }

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    testImplementation(project(":starter"))
    testImplementation(project(":testkit"))
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val gradingResults = mutableListOf<Map<String, Any>>()

fun Test.configureGrader(includeTag: String? = null) {
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
    shouldRunAfter(tasks.test)
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs localhost/in-memory integration tests."
    configureGrader("integration")
    ignoreFailures = true
    shouldRunAfter(tasks.test)
}

val chaosTest by tasks.registering(Test::class) {
    description = "Runs deterministic seeded chaos tests."
    configureGrader("chaos")
    ignoreFailures = true
    shouldRunAfter(integrationTest)
}

val stressTest by tasks.registering(Test::class) {
    description = "Runs long stress tests; excluded from the default grade task."
    configureGrader("stress")
    ignoreFailures = true
    maxHeapSize = "2g"
}

val writeGradingSummary by tasks.registering {
    val outputDir = rootProject.layout.buildDirectory.dir("reports/grading")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.apply { mkdirs() }
        val failures = gradingResults.filter { it["status"] == "FAILURE" }
        val seed = providers.gradleProperty("seed").orElse("123456").get()
        val rubric = linkedMapOf(
            "Build/API" to 5, "Core Types" to 5, "Replica" to 10,
            "Acceptor" to 15, "Leader/pmax" to 10, "Scout" to 10,
            "Commander" to 10, "Basic Integration" to 10,
            "Crash Recovery / Failover" to 10,
            "Network / Adversarial Safety" to 10, "Stress / Chaos" to 5
        )
        val sectionPoints = rubric.mapValues { (section, maximum) ->
            mapOf("awarded" to 0, "maximum" to maximum,
                "status" to "Scored by the complete course grader ($section)")
        }
        val payload = linkedMapOf(
            "totalPoints" to 0,
            "maximumPoints" to 100,
            "sectionPoints" to sectionPoints,
            "failedTests" to failures,
            "seed" to seed,
            "diagnostics" to "See grader/build/reports/tests and the event trace printed by failing tests."
        )
        dir.resolve("summary.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(payload)))
        dir.resolve("summary.txt").writeText(buildString {
            appendLine("Paxos Parcel Locker grading summary")
            appendLine("Seed: $seed")
            appendLine("Tests observed: ${gradingResults.size}")
            appendLine("Failures: ${failures.size}")
            failures.forEach { appendLine("- ${it["test"]}") }
            appendLine("Section maxima: ${rubric.entries.joinToString { "${it.key}=${it.value}" }}")
            appendLine("Note: section scoring is finalized by the course grading environment.")
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
