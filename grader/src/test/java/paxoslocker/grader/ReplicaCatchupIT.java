package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class ReplicaCatchupIT{@Test void scenario(){IntegrationScenarioSupport.replicaRecovery(false,false);}}
