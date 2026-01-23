package com.redgreenrefactor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents the complete state passed between agents via Git Notes.
 * This is the core data structure for handoff coordination.
 *
 * @param phase          The current phase of the TDD workflow
 * @param nextPhase      The next phase to execute (set by orchestrator)
 * @param cycleNumber    The current TDD cycle number (increments after each REFACTOR)
 * @param currentTest    The test case being worked on (null when complete)
 * @param completedTests List of completed test descriptions
 * @param pendingTests   List of pending test descriptions
 * @param testResult     Result of the last test run (nullable)
 * @param error          Error message if an error occurred (nullable)
 * @param errorDetails   Detailed error information (nullable)
 * @param retryCount     Number of retry attempts for the current phase
 */
public record HandoffState(
        @JsonProperty("phase") Phase phase,
        @JsonProperty("nextPhase") Phase nextPhase,
        @JsonProperty("cycleNumber") int cycleNumber,
        @JsonProperty("currentTest") TestCase currentTest,
        @JsonProperty("completedTests") List<String> completedTests,
        @JsonProperty("pendingTests") List<String> pendingTests,
        @JsonProperty("testResult") TestResult testResult,
        @JsonProperty("error") String error,
        @JsonProperty("errorDetails") ErrorDetails errorDetails,
        @JsonProperty("retryCount") int retryCount
) {
    /**
     * Creates a new HandoffState with defensive copies of lists.
     */
    public HandoffState {
        completedTests = completedTests == null ? List.of() : List.copyOf(completedTests);
        pendingTests = pendingTests == null ? List.of() : List.copyOf(pendingTests);
    }

    /**
     * Creates a builder initialized with the values from this state.
     *
     * @return A new builder for creating modified copies
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Creates a new builder for constructing a HandoffState.
     *
     * @return A new empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an initial handoff state for starting a new workflow.
     *
     * @return A new HandoffState in PLAN phase
     */
    public static HandoffState initial() {
        return builder()
                .phase(Phase.PLAN)
                .nextPhase(Phase.RED)
                .cycleNumber(1)
                .build();
    }

    /**
     * Builder for creating HandoffState instances.
     */
    public static final class Builder {
        private Phase phase;
        private Phase nextPhase;
        private int cycleNumber;
        private TestCase currentTest;
        private List<String> completedTests;
        private List<String> pendingTests;
        private TestResult testResult;
        private String error;
        private ErrorDetails errorDetails;
        private int retryCount;

        public Builder() {
            this.completedTests = List.of();
            this.pendingTests = List.of();
        }

        public Builder(HandoffState state) {
            this.phase = state.phase();
            this.nextPhase = state.nextPhase();
            this.cycleNumber = state.cycleNumber();
            this.currentTest = state.currentTest();
            this.completedTests = state.completedTests();
            this.pendingTests = state.pendingTests();
            this.testResult = state.testResult();
            this.error = state.error();
            this.errorDetails = state.errorDetails();
            this.retryCount = state.retryCount();
        }

        public Builder phase(Phase phase) {
            this.phase = phase;
            return this;
        }

        public Builder nextPhase(Phase nextPhase) {
            this.nextPhase = nextPhase;
            return this;
        }

        public Builder cycleNumber(int cycleNumber) {
            this.cycleNumber = cycleNumber;
            return this;
        }

        public Builder currentTest(TestCase currentTest) {
            this.currentTest = currentTest;
            return this;
        }

        public Builder completedTests(List<String> completedTests) {
            this.completedTests = completedTests == null ? List.of() : List.copyOf(completedTests);
            return this;
        }

        public Builder pendingTests(List<String> pendingTests) {
            this.pendingTests = pendingTests == null ? List.of() : List.copyOf(pendingTests);
            return this;
        }

        public Builder testResult(TestResult testResult) {
            this.testResult = testResult;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder errorDetails(ErrorDetails errorDetails) {
            this.errorDetails = errorDetails;
            return this;
        }

        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public HandoffState build() {
            return new HandoffState(
                    phase,
                    nextPhase,
                    cycleNumber,
                    currentTest,
                    completedTests,
                    pendingTests,
                    testResult,
                    error,
                    errorDetails,
                    retryCount
            );
        }
    }
}
