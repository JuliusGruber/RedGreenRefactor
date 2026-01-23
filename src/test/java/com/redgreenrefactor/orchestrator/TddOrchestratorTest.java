package com.redgreenrefactor.orchestrator;

import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.Phase;
import com.redgreenrefactor.model.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TddOrchestratorTest {

    private PhaseExecutor mockPhaseExecutor;
    private TestListOutputParser mockOutputParser;
    private TddOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        mockPhaseExecutor = mock(PhaseExecutor.class);
        mockOutputParser = mock(TestListOutputParser.class);
        orchestrator = new TddOrchestrator(mockPhaseExecutor, mockOutputParser);
    }

    @Test
    void constructor_throwsOnNullPhaseExecutor() {
        assertThatThrownBy(() -> new TddOrchestrator(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("phaseExecutor");
    }

    @Test
    void runWorkflow_throwsOnNullFeatureRequest() {
        assertThatThrownBy(() -> orchestrator.runWorkflow(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("featureRequest");
    }

    @Test
    void runWorkflow_throwsOnBlankFeatureRequest() {
        assertThatThrownBy(() -> orchestrator.runWorkflow("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void runWorkflow_completesImmediatelyWhenNoTests() {
        // PLAN phase returns success with null currentTest
        HandoffState planState = HandoffState.initial();
        PhaseResult planResult = PhaseResult.success(Phase.PLAN, planState, null,
                """
                {"currentTest": null}
                """);

        when(mockPhaseExecutor.runPhase(eq(Phase.PLAN), any(HandoffState.class), anyString()))
                .thenReturn(planResult);
        when(mockOutputParser.parseTestSelection(anyString())).thenReturn(Optional.empty());

        WorkflowResult result = orchestrator.runWorkflow("Add feature");

        assertThat(result.success()).isTrue();
        assertThat(result.completedCycles()).isZero();
        assertThat(result.finalState().phase()).isEqualTo(Phase.COMPLETE);
        verify(mockPhaseExecutor, times(1)).runPhase(any(), any(), anyString());
    }

    @Test
    void runWorkflow_executesOneCycle() {
        TestCase testCase = new TestCase("test login", "LoginTest.java", "Login.java");

        // Initial PLAN returns a test
        HandoffState planState = HandoffState.builder()
                .phase(Phase.PLAN)
                .cycleNumber(1)
                .build();
        PhaseResult initialPlanResult = PhaseResult.success(Phase.PLAN, planState, null, "plan response");

        // RED phase
        HandoffState redState = planState.toBuilder().phase(Phase.RED).nextPhase(Phase.GREEN).build();
        PhaseResult redResult = PhaseResult.success(Phase.RED, redState, null, "red response");

        // GREEN phase
        HandoffState greenState = redState.toBuilder().phase(Phase.GREEN).nextPhase(Phase.REFACTOR).build();
        PhaseResult greenResult = PhaseResult.success(Phase.GREEN, greenState, null, "green response");

        // REFACTOR phase
        HandoffState refactorState = greenState.toBuilder().phase(Phase.REFACTOR).nextPhase(Phase.PLAN).build();
        PhaseResult refactorResult = PhaseResult.success(Phase.REFACTOR, refactorState, null, "refactor response");

        // Final PLAN returns no more tests
        HandoffState finalPlanState = refactorState.toBuilder().phase(Phase.PLAN).cycleNumber(2).build();
        PhaseResult finalPlanResult = PhaseResult.success(Phase.PLAN, finalPlanState, null, "final plan");

        when(mockPhaseExecutor.runPhase(eq(Phase.PLAN), any(HandoffState.class), anyString()))
                .thenReturn(initialPlanResult)
                .thenReturn(finalPlanResult);
        when(mockPhaseExecutor.runPhase(eq(Phase.RED), any(HandoffState.class), anyString()))
                .thenReturn(redResult);
        when(mockPhaseExecutor.runPhase(eq(Phase.GREEN), any(HandoffState.class), anyString()))
                .thenReturn(greenResult);
        when(mockPhaseExecutor.runPhase(eq(Phase.REFACTOR), any(HandoffState.class), anyString()))
                .thenReturn(refactorResult);

        when(mockOutputParser.parseTestSelection("plan response")).thenReturn(Optional.of(testCase));
        when(mockOutputParser.parseTestSelection("final plan")).thenReturn(Optional.empty());

        WorkflowResult result = orchestrator.runWorkflow("Add user login");

        assertThat(result.success()).isTrue();
        assertThat(result.completedCycles()).isEqualTo(1);
        assertThat(result.finalState().phase()).isEqualTo(Phase.COMPLETE);
    }

    @Test
    void runWorkflow_failsOnPlanPhaseFailure() {
        HandoffState state = HandoffState.initial();
        PhaseResult failedPlan = PhaseResult.failure(Phase.PLAN, state, "", "Plan failed");

        when(mockPhaseExecutor.runPhase(eq(Phase.PLAN), any(HandoffState.class), anyString()))
                .thenReturn(failedPlan);

        WorkflowResult result = orchestrator.runWorkflow("Feature");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("PLAN phase failed");
        assertThat(result.completedCycles()).isZero();
    }

    @Test
    void runWorkflow_failsOnRedPhaseFailure() {
        TestCase testCase = new TestCase("test", "Test.java", "Impl.java");

        HandoffState planState = HandoffState.initial();
        PhaseResult planResult = PhaseResult.success(Phase.PLAN, planState, null, "plan");

        HandoffState redState = planState.toBuilder().currentTest(testCase).build();
        PhaseResult failedRed = PhaseResult.failure(Phase.RED, redState, "", "Test compilation failed");

        when(mockPhaseExecutor.runPhase(eq(Phase.PLAN), any(HandoffState.class), anyString()))
                .thenReturn(planResult);
        when(mockPhaseExecutor.runPhase(eq(Phase.RED), any(HandoffState.class), anyString()))
                .thenReturn(failedRed);
        when(mockOutputParser.parseTestSelection("plan")).thenReturn(Optional.of(testCase));

        WorkflowResult result = orchestrator.runWorkflow("Feature");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("RED phase failed");
    }

    @Test
    void runWorkflow_failsOnGreenPhaseFailure() {
        TestCase testCase = new TestCase("test", "Test.java", "Impl.java");

        HandoffState planState = HandoffState.initial();
        PhaseResult planResult = PhaseResult.success(Phase.PLAN, planState, null, "plan");

        HandoffState redState = planState.toBuilder().phase(Phase.RED).build();
        PhaseResult redResult = PhaseResult.success(Phase.RED, redState, null, "red");

        HandoffState greenState = redState.toBuilder().phase(Phase.GREEN).build();
        PhaseResult failedGreen = PhaseResult.failure(Phase.GREEN, greenState, "", "Implementation failed");

        when(mockPhaseExecutor.runPhase(eq(Phase.PLAN), any(HandoffState.class), anyString()))
                .thenReturn(planResult);
        when(mockPhaseExecutor.runPhase(eq(Phase.RED), any(HandoffState.class), anyString()))
                .thenReturn(redResult);
        when(mockPhaseExecutor.runPhase(eq(Phase.GREEN), any(HandoffState.class), anyString()))
                .thenReturn(failedGreen);
        when(mockOutputParser.parseTestSelection("plan")).thenReturn(Optional.of(testCase));

        WorkflowResult result = orchestrator.runWorkflow("Feature");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("GREEN phase failed");
    }

    @Test
    void runWorkflow_failsOnRefactorPhaseFailure() {
        TestCase testCase = new TestCase("test", "Test.java", "Impl.java");

        HandoffState planState = HandoffState.initial();
        PhaseResult planResult = PhaseResult.success(Phase.PLAN, planState, null, "plan");

        HandoffState redState = planState.toBuilder().phase(Phase.RED).build();
        PhaseResult redResult = PhaseResult.success(Phase.RED, redState, null, "red");

        HandoffState greenState = redState.toBuilder().phase(Phase.GREEN).build();
        PhaseResult greenResult = PhaseResult.success(Phase.GREEN, greenState, null, "green");

        HandoffState refactorState = greenState.toBuilder().phase(Phase.REFACTOR).build();
        PhaseResult failedRefactor = PhaseResult.failure(Phase.REFACTOR, refactorState, "", "Refactor broke tests");

        when(mockPhaseExecutor.runPhase(eq(Phase.PLAN), any(HandoffState.class), anyString()))
                .thenReturn(planResult);
        when(mockPhaseExecutor.runPhase(eq(Phase.RED), any(HandoffState.class), anyString()))
                .thenReturn(redResult);
        when(mockPhaseExecutor.runPhase(eq(Phase.GREEN), any(HandoffState.class), anyString()))
                .thenReturn(greenResult);
        when(mockPhaseExecutor.runPhase(eq(Phase.REFACTOR), any(HandoffState.class), anyString()))
                .thenReturn(failedRefactor);
        when(mockOutputParser.parseTestSelection("plan")).thenReturn(Optional.of(testCase));

        WorkflowResult result = orchestrator.runWorkflow("Feature");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("REFACTOR phase failed");
    }

    @Test
    void runWorkflow_retriesFailedPhase() {
        TestCase testCase = new TestCase("test", "Test.java", "Impl.java");

        HandoffState planState = HandoffState.initial();
        PhaseResult planResult = PhaseResult.success(Phase.PLAN, planState, null, "plan");

        HandoffState redState = planState.toBuilder().phase(Phase.RED).build();
        // First attempt fails, second succeeds
        PhaseResult failedRed = PhaseResult.failure(Phase.RED, redState, "", "Temporary error");
        PhaseResult successRed = PhaseResult.success(Phase.RED, redState, null, "red success");

        HandoffState greenState = redState.toBuilder().phase(Phase.GREEN).build();
        PhaseResult greenResult = PhaseResult.success(Phase.GREEN, greenState, null, "green");

        HandoffState refactorState = greenState.toBuilder().phase(Phase.REFACTOR).build();
        PhaseResult refactorResult = PhaseResult.success(Phase.REFACTOR, refactorState, null, "refactor");

        HandoffState finalPlanState = refactorState.toBuilder().phase(Phase.PLAN).cycleNumber(2).build();
        PhaseResult finalPlanResult = PhaseResult.success(Phase.PLAN, finalPlanState, null, "final");

        when(mockPhaseExecutor.runPhase(eq(Phase.PLAN), any(HandoffState.class), anyString()))
                .thenReturn(planResult)
                .thenReturn(finalPlanResult);
        when(mockPhaseExecutor.runPhase(eq(Phase.RED), any(HandoffState.class), anyString()))
                .thenReturn(failedRed)
                .thenReturn(successRed);
        when(mockPhaseExecutor.runPhase(eq(Phase.GREEN), any(HandoffState.class), anyString()))
                .thenReturn(greenResult);
        when(mockPhaseExecutor.runPhase(eq(Phase.REFACTOR), any(HandoffState.class), anyString()))
                .thenReturn(refactorResult);
        when(mockOutputParser.parseTestSelection("plan")).thenReturn(Optional.of(testCase));
        when(mockOutputParser.parseTestSelection("final")).thenReturn(Optional.empty());

        WorkflowResult result = orchestrator.runWorkflow("Feature");

        assertThat(result.success()).isTrue();
        // RED phase should have been called twice (1 failure + 1 success)
        verify(mockPhaseExecutor, times(2)).runPhase(eq(Phase.RED), any(HandoffState.class), anyString());
    }

    @Test
    void runWorkflow_tracksAllPhaseResults() {
        TestCase testCase = new TestCase("test", "Test.java", "Impl.java");

        HandoffState state = HandoffState.initial();
        PhaseResult planResult = PhaseResult.success(Phase.PLAN, state, null, "plan");
        PhaseResult redResult = PhaseResult.success(Phase.RED, state, null, "red");
        PhaseResult greenResult = PhaseResult.success(Phase.GREEN, state, null, "green");
        PhaseResult refactorResult = PhaseResult.success(Phase.REFACTOR, state, null, "refactor");
        PhaseResult finalPlanResult = PhaseResult.success(Phase.PLAN, state, null, "final");

        when(mockPhaseExecutor.runPhase(eq(Phase.PLAN), any(HandoffState.class), anyString()))
                .thenReturn(planResult)
                .thenReturn(finalPlanResult);
        when(mockPhaseExecutor.runPhase(eq(Phase.RED), any(HandoffState.class), anyString()))
                .thenReturn(redResult);
        when(mockPhaseExecutor.runPhase(eq(Phase.GREEN), any(HandoffState.class), anyString()))
                .thenReturn(greenResult);
        when(mockPhaseExecutor.runPhase(eq(Phase.REFACTOR), any(HandoffState.class), anyString()))
                .thenReturn(refactorResult);
        when(mockOutputParser.parseTestSelection("plan")).thenReturn(Optional.of(testCase));
        when(mockOutputParser.parseTestSelection("final")).thenReturn(Optional.empty());

        WorkflowResult result = orchestrator.runWorkflow("Feature");

        assertThat(result.phaseResults()).hasSize(5); // PLAN, RED, GREEN, REFACTOR, PLAN
    }
}
