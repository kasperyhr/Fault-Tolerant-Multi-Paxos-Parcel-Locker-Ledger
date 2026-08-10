package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class LeaderCrashAfterChosenIT{@Test void scenario(){IntegrationScenarioSupport.leaderScenario("afterChosen");}}
