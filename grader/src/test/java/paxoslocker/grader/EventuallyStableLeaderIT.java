package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class EventuallyStableLeaderIT{@Test void scenario(){IntegrationScenarioSupport.leaderScenario("stable");}}
