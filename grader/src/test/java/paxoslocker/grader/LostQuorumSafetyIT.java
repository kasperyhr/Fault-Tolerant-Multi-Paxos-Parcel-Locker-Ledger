package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class LostQuorumSafetyIT{@Test void scenario(){IntegrationScenarioSupport.acceptorsDown(1,3,2,false);}}
