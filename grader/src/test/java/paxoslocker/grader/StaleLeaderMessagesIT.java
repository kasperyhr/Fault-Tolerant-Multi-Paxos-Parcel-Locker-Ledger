package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class StaleLeaderMessagesIT{@Test void scenario(){IntegrationScenarioSupport.network("staleLeader");}}
