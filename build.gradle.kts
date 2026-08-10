plugins {
    base
}

allprojects {
    group = "paxoslocker"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

tasks.register("grade") {
    group = "verification"
    description = "Runs the grading suite and writes text and JSON summaries."
    dependsOn(":grader:grade")
}

tasks.register("gradeFull") {
    group = "verification"
    description = "Runs grading plus the long-running stress suites."
    dependsOn(":grader:gradeFull")
}

tasks.register("integrationTest") { dependsOn(":grader:integrationTest") }
tasks.register("chaosTest") { dependsOn(":grader:chaosTest") }
tasks.register("stressTest") { dependsOn(":grader:stressTest") }
tasks.register("reproduceFailure") { dependsOn(":grader:chaosTest") }
