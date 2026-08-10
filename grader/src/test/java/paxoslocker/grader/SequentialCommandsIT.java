package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class SequentialCommandsIT{@Test void scenario(){IntegrationScenarioSupport.sequential(100);}}
