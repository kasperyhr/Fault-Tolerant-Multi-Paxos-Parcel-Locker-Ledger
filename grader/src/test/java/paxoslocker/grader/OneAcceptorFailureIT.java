package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class OneAcceptorFailureIT{@Test void scenario(){IntegrationScenarioSupport.acceptorsDown(1,3,1,true);}}
