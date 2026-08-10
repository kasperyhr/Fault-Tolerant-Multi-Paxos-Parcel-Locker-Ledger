package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class ConcurrentClientsIT{@Test void scenario(){IntegrationScenarioSupport.concurrent(20);}}
