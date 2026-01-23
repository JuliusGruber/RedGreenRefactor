package com.redgreenrefactor.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

/**
 * Represents the result of a complete TDD workflow execution.
 *
 * @param featureRequest   The original feature request that started the workflow
 * @param success          Whether the workflow completed successfully
 * @param cycles           Results from each TDD cycle
 * @param totalTests       Total number of tests created
 * @param startTime        When the workflow started
 * @param endTime          When the workflow ended
 * @param finalState       The final handoff state
 * @param error            Error message if the workflow failed (nullable)
 */
public record WorkflowResult(
        String featureRequest,
        boolean success,
        List<CycleResult> cycles,
        int totalTests,
        Instant startTime,
        Instant endTime,
        HandoffState finalState,
        String error
) {
    public WorkflowResult {
        cycles = cycles == null ? List.of() : List.copyOf(cycles);
    }

    /**
     * Calculates the total duration of the workflow.
     *
     * @return The duration between start and end time
     */
    public Duration duration() {
        if (startTime == null || endTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, endTime);
    }

    /**
     * Returns the number of successful cycles.
     *
     * @return Count of cycles where success is true
     */
    public long successfulCycleCount() {
        return cycles.stream().filter(CycleResult::success).count();
    }

    /**
     * Returns the number of failed cycles.
     *
     * @return Count of cycles where success is false
     */
    public long failedCycleCount() {
        return cycles.stream().filter(Predicate.not(CycleResult::success)).count();
    }

    /**
     * Creates a builder for constructing a WorkflowResult.
     *
     * @return A new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating WorkflowResult instances.
     */
    public static final class Builder {
        private String featureRequest;
        private boolean success;
        private List<CycleResult> cycles = List.of();
        private int totalTests;
        private Instant startTime;
        private Instant endTime;
        private HandoffState finalState;
        private String error;

        public Builder featureRequest(String featureRequest) {
            this.featureRequest = featureRequest;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder cycles(List<CycleResult> cycles) {
            this.cycles = cycles == null ? List.of() : List.copyOf(cycles);
            return this;
        }

        public Builder totalTests(int totalTests) {
            this.totalTests = totalTests;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder finalState(HandoffState finalState) {
            this.finalState = finalState;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public WorkflowResult build() {
            return new WorkflowResult(
                    featureRequest,
                    success,
                    cycles,
                    totalTests,
                    startTime,
                    endTime,
                    finalState,
                    error
            );
        }
    }
}
