package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class LeaderCrashBeforeScoutIT{@Test void scenario(){IntegrationScenarioSupport.leaderScenario("beforeScout");}}
