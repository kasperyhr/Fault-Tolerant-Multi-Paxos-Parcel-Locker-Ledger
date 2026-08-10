package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class F2TwoAcceptorFailureIT{@Test void scenario(){IntegrationScenarioSupport.acceptorsDown(2,5,2,true);}}
