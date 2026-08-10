package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class MultipleReplicaRecoveryIT{@Test void scenario(){IntegrationScenarioSupport.replicaRecovery(false,true);}}
