package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class F2LostQuorumIT{@Test void scenario(){IntegrationScenarioSupport.acceptorsDown(2,5,3,false);}}
