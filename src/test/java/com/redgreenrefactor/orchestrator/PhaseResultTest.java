package com.redgreenrefactor.orchestrator;

import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.Phase;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhaseResultTest {

    @Test
    void success_createsSuccessfulResult() {
        HandoffState state = HandoffState.initial();
        ObjectId commitId = ObjectId.fromString("0123456789012345678901234567890123456789");

        PhaseResult result = PhaseResult.success(Phase.RED, state, commitId, "Test response");

        assertThat(result.success()).isTrue();
        assertThat(result.executedPhase()).isEqualTo(Phase.RED);
        assertThat(result.updatedState()).isEqualTo(state);
        assertThat(result.commitId()).isEqualTo(commitId);
        assertThat(result.agentResponse()).isEqualTo("Test response");
        assertThat(result.error()).isNull();
    }

    @Test
    void success_allowsNullCommitId() {
        HandoffState state = HandoffState.initial();

        PhaseResult result = PhaseResult.success(Phase.GREEN, state, null, "Response");

        assertThat(result.success()).isTrue();
        assertThat(result.commitId()).isNull();
    }

    @Test
    void failure_createsFailedResult() {
        HandoffState state = HandoffState.initial();

        PhaseResult result = PhaseResult.failure(Phase.REFACTOR, state, "Agent output", "Compilation failed");

        assertThat(result.success()).isFalse();
        assertThat(result.executedPhase()).isEqualTo(Phase.REFACTOR);
        assertThat(result.updatedState()).isEqualTo(state);
        assertThat(result.commitId()).isNull();
        assertThat(result.agentResponse()).isEqualTo("Agent output");
        assertThat(result.error()).isEqualTo("Compilation failed");
    }

    @Test
    void constructor_throwsOnNullPhase() {
        HandoffState state = HandoffState.initial();

        assertThatThrownBy(() -> new PhaseResult(null, state, null, "response", true, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("executedPhase");
    }

    @Test
    void constructor_throwsOnNullState() {
        assertThatThrownBy(() -> new PhaseResult(Phase.RED, null, null, "response", true, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("updatedState");
    }

    @Test
    void constructor_throwsOnNullResponse() {
        HandoffState state = HandoffState.initial();

        assertThatThrownBy(() -> new PhaseResult(Phase.RED, state, null, null, true, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("agentResponse");
    }

    @Test
    void record_providesEqualsAndHashCode() {
        HandoffState state = HandoffState.initial();
        ObjectId commitId = ObjectId.fromString("0123456789012345678901234567890123456789");

        PhaseResult result1 = PhaseResult.success(Phase.RED, state, commitId, "response");
        PhaseResult result2 = PhaseResult.success(Phase.RED, state, commitId, "response");

        assertThat(result1).isEqualTo(result2);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    void record_hasReadableToString() {
        HandoffState state = HandoffState.initial();

        PhaseResult result = PhaseResult.success(Phase.GREEN, state, null, "done");

        String toString = result.toString();
        assertThat(toString).contains("PhaseResult");
        assertThat(toString).contains("GREEN");
        assertThat(toString).contains("success=true");
    }
}
