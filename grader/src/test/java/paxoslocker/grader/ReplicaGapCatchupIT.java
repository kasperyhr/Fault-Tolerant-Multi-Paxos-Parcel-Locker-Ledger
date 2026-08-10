package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class ReplicaGapCatchupIT{@Test void scenario(){IntegrationScenarioSupport.replicaRecovery(true,false);}}
