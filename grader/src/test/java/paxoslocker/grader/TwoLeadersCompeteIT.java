package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class TwoLeadersCompeteIT{@Test void scenario(){IntegrationScenarioSupport.leadersCompete(2);}}
