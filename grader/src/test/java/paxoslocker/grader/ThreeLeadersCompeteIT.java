package paxoslocker.grader; import org.junit.jupiter.api.*; @Tag("integration") class ThreeLeadersCompeteIT{@Test void scenario(){IntegrationScenarioSupport.leadersCompete(3);}}
