package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class LeaderCrashAfterAdoptedIT{@Test void scenario(){IntegrationScenarioSupport.leaderScenario("afterAdopted");}}
