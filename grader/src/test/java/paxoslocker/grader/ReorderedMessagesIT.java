package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class ReorderedMessagesIT{@Test void scenario(){IntegrationScenarioSupport.network("reorder");}}
