package com.redgreenrefactor.orchestrator;

import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.Phase;
import org.eclipse.jgit.lib.ObjectId;

import java.util.Objects;

/**
 * Captures the result of executing a single phase.
 *
 * @param executedPhase the phase that was executed
 * @param updatedState  the handoff state after phase execution
 * @param commitId      the commit ID created by the agent (nullable if no commit was made)
 * @param agentResponse the final text response from the agent
 * @param success       whether the phase completed successfully
 * @param error         error message if the phase failed (nullable)
 */
public record PhaseResult(
        Phase executedPhase,
        HandoffState updatedState,
        ObjectId commitId,
        String agentResponse,
        boolean success,
        String error
) {
    public PhaseResult {
        Objects.requireNonNull(executedPhase, "executedPhase cannot be null");
        Objects.requireNonNull(updatedState, "updatedState cannot be null");
        Objects.requireNonNull(agentResponse, "agentResponse cannot be null");
    }

    /**
     * Creates a successful phase result.
     *
     * @param phase         the executed phase
     * @param state         the updated state
     * @param commitId      the commit ID (may be null)
     * @param agentResponse the agent's response
     * @return a successful PhaseResult
     */
    public static PhaseResult success(Phase phase, HandoffState state, ObjectId commitId, String agentResponse) {
        return new PhaseResult(phase, state, commitId, agentResponse, true, null);
    }

    /**
     * Creates a failed phase result.
     *
     * @param phase         the executed phase
     * @param state         the state at time of failure
     * @param agentResponse the agent's response (may contain error details)
     * @param error         the error message
     * @return a failed PhaseResult
     */
    public static PhaseResult failure(Phase phase, HandoffState state, String agentResponse, String error) {
        return new PhaseResult(phase, state, null, agentResponse, false, error);
    }
}
