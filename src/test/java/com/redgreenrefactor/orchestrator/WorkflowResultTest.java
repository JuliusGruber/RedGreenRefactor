package com.redgreenrefactor.orchestrator;

import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.Phase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowResultTest {

    @Test
    void success_createsSuccessfulResult() {
        HandoffState state = HandoffState.builder()
                .phase(Phase.COMPLETE)
                .cycleNumber(5)
                .completedTests(List.of("test1", "test2"))
                .build();
        List<PhaseResult> results = List.of(
                PhaseResult.success(Phase.PLAN, state, null, "planned"),
                PhaseResult.success(Phase.RED, state, null, "wrote test")
        );

        WorkflowResult result = WorkflowResult.success(state, 5, results);

        assertThat(result.success()).isTrue();
        assertThat(result.finalState()).isEqualTo(state);
        assertThat(result.completedCycles()).isEqualTo(5);
        assertThat(result.phaseResults()).hasSize(2);
        assertThat(result.error()).isNull();
    }

    @Test
    void failure_createsFailedResult() {
        HandoffState state = HandoffState.builder()
                .phase(Phase.RED)
                .cycleNumber(3)
                .build();
        List<PhaseResult> results = List.of(
                PhaseResult.failure(Phase.RED, state, "output", "test error")
        );

        WorkflowResult result = WorkflowResult.failure(state, 3, results, "Workflow failed");

        assertThat(result.success()).isFalse();
        assertThat(result.finalState()).isEqualTo(state);
        assertThat(result.completedCycles()).isEqualTo(3);
        assertThat(result.phaseResults()).hasSize(1);
        assertThat(result.error()).isEqualTo("Workflow failed");
    }

    @Test
    void constructor_throwsOnNullFinalState() {
        List<PhaseResult> results = List.of();

        assertThatThrownBy(() -> new WorkflowResult(true, null, 0, results, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("finalState");
    }

    @Test
    void constructor_throwsOnNullPhaseResults() {
        HandoffState state = HandoffState.initial();

        assertThatThrownBy(() -> new WorkflowResult(true, state, 0, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("phaseResults");
    }

    @Test
    void phaseResults_isImmutableCopy() {
        HandoffState state = HandoffState.initial();
        List<PhaseResult> mutableList = new ArrayList<>();
        mutableList.add(PhaseResult.success(Phase.PLAN, state, null, "response"));

        WorkflowResult result = WorkflowResult.success(state, 1, mutableList);

        mutableList.add(PhaseResult.success(Phase.RED, state, null, "another"));

        assertThat(result.phaseResults()).hasSize(1);
    }

    @Test
    void record_providesEqualsAndHashCode() {
        HandoffState state = HandoffState.initial();
        List<PhaseResult> results = List.of();

        WorkflowResult result1 = WorkflowResult.success(state, 3, results);
        WorkflowResult result2 = WorkflowResult.success(state, 3, results);

        assertThat(result1).isEqualTo(result2);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    void record_hasReadableToString() {
        HandoffState state = HandoffState.initial();
        List<PhaseResult> results = List.of();

        WorkflowResult result = WorkflowResult.success(state, 2, results);

        String toString = result.toString();
        assertThat(toString).contains("WorkflowResult");
        assertThat(toString).contains("success=true");
        assertThat(toString).contains("completedCycles=2");
    }
}
