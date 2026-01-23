package com.redgreenrefactor.orchestrator;

import com.redgreenrefactor.model.HandoffState;

import java.util.List;
import java.util.Objects;

/**
 * Captures the result of running the complete TDD workflow.
 *
 * @param success         whether the workflow completed successfully
 * @param finalState      the final handoff state
 * @param completedCycles the number of TDD cycles completed
 * @param phaseResults    the results of all executed phases
 * @param error           error message if the workflow failed (nullable)
 */
public record WorkflowResult(
        boolean success,
        HandoffState finalState,
        int completedCycles,
        List<PhaseResult> phaseResults,
        String error
) {
    public WorkflowResult {
        Objects.requireNonNull(finalState, "finalState cannot be null");
        Objects.requireNonNull(phaseResults, "phaseResults cannot be null");
        phaseResults = List.copyOf(phaseResults);
    }

    /**
     * Creates a successful workflow result.
     *
     * @param finalState      the final state
     * @param completedCycles the number of cycles completed
     * @param phaseResults    all phase results
     * @return a successful WorkflowResult
     */
    public static WorkflowResult success(HandoffState finalState, int completedCycles, List<PhaseResult> phaseResults) {
        return new WorkflowResult(true, finalState, completedCycles, phaseResults, null);
    }

    /**
     * Creates a failed workflow result.
     *
     * @param finalState      the state at time of failure
     * @param completedCycles the number of cycles completed before failure
     * @param phaseResults    all phase results up to failure
     * @param error           the error message
     * @return a failed WorkflowResult
     */
    public static WorkflowResult failure(HandoffState finalState, int completedCycles,
                                          List<PhaseResult> phaseResults, String error) {
        return new WorkflowResult(false, finalState, completedCycles, phaseResults, error);
    }
}
