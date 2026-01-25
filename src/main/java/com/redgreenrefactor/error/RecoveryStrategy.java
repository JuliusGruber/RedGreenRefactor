package com.redgreenrefactor.error;

import com.redgreenrefactor.git.GitOperationException;
import com.redgreenrefactor.git.GitOperations;
import com.redgreenrefactor.model.ErrorDetails;
import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.Phase;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Implements recovery strategies for different types of errors.
 * <p>
 * Recovery strategies vary by error type and phase:
 * <ul>
 *   <li>COMPILATION: Re-run agent with error context</li>
 *   <li>TEST_FAILURE in GREEN/REFACTOR: Rollback and retry</li>
 *   <li>UNEXPECTED_PASS in RED: Re-run with assertion guidance</li>
 *   <li>TIMEOUT/NETWORK: Exponential backoff</li>
 * </ul>
 */
public class RecoveryStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(RecoveryStrategy.class);

    private final GitOperations gitOperations;
    private final RetryHandler retryHandler;
    private final TDDErrorHandler errorHandler;

    /**
     * Creates a RecoveryStrategy with the given dependencies.
     *
     * @param gitOperations the Git operations handler for rollbacks
     * @param retryHandler  the retry handler for backoff logic
     */
    public RecoveryStrategy(GitOperations gitOperations, RetryHandler retryHandler) {
        this.gitOperations = Objects.requireNonNull(gitOperations, "gitOperations cannot be null");
        this.retryHandler = Objects.requireNonNull(retryHandler, "retryHandler cannot be null");
        this.errorHandler = new TDDErrorHandler();
    }

    /**
     * Creates a RecoveryStrategy with a custom error handler.
     *
     * @param gitOperations the Git operations handler for rollbacks
     * @param retryHandler  the retry handler for backoff logic
     * @param errorHandler  the error handler for error classification
     */
    public RecoveryStrategy(GitOperations gitOperations, RetryHandler retryHandler, TDDErrorHandler errorHandler) {
        this.gitOperations = Objects.requireNonNull(gitOperations, "gitOperations cannot be null");
        this.retryHandler = Objects.requireNonNull(retryHandler, "retryHandler cannot be null");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler cannot be null");
    }

    /**
     * Determines the appropriate recovery action for an error.
     *
     * @param errorType the type of error
     * @param phase     the phase where the error occurred
     * @param state     the current handoff state
     * @return the recovery action to take
     */
    public RecoveryAction determineRecoveryAction(ErrorType errorType, Phase phase, HandoffState state) {
        // Check if retries are exhausted
        if (!retryHandler.shouldRetry(state.retryCount())) {
            LOG.warn("Retry limit reached ({} attempts) - aborting", state.retryCount());
            return RecoveryAction.ABORT;
        }

        return switch (errorType) {
            case COMPILATION -> RecoveryAction.RETRY_WITH_CONTEXT;

            case TEST_FAILURE -> switch (phase) {
                case RED -> RecoveryAction.CONTINUE;  // Expected in RED phase
                case GREEN, REFACTOR -> RecoveryAction.ROLLBACK_AND_RETRY;
                default -> RecoveryAction.RETRY_WITH_CONTEXT;
            };

            case UNEXPECTED_PASS -> RecoveryAction.RETRY_WITH_CONTEXT;

            case TIMEOUT, NETWORK, RATE_LIMIT -> RecoveryAction.WAIT_AND_RETRY;

            case UNKNOWN -> RecoveryAction.RETRY_WITH_CONTEXT;
        };
    }

    /**
     * Executes the recovery action and returns the updated state.
     *
     * @param action          the recovery action to execute
     * @param state           the current state
     * @param phase           the phase where the error occurred
     * @param errorType       the type of error
     * @param errorOutput     the error output/message
     * @param rollbackCommit  the commit to rollback to (if applicable)
     * @return the recovery result
     * @throws InterruptedException if interrupted during wait
     */
    public RecoveryResult executeRecovery(
            RecoveryAction action,
            HandoffState state,
            Phase phase,
            ErrorType errorType,
            String errorOutput,
            ObjectId rollbackCommit) throws InterruptedException {

        LOG.info("Executing recovery action: {} for {} in {} phase", action, errorType, phase);

        return switch (action) {
            case CONTINUE -> {
                // No action needed, continue with current state
                yield new RecoveryResult(true, state, "Continuing - error was expected");
            }

            case RETRY_WITH_CONTEXT -> {
                HandoffState updatedState = retryHandler.prepareForRetry(state, errorType, errorOutput);
                yield new RecoveryResult(true, updatedState, "Prepared for retry with error context");
            }

            case ROLLBACK_AND_RETRY -> {
                HandoffState updatedState = rollbackAndPrepareRetry(state, errorType, errorOutput, rollbackCommit);
                yield new RecoveryResult(true, updatedState, "Rolled back and prepared for retry");
            }

            case WAIT_AND_RETRY -> {
                retryHandler.waitBeforeRetry(state.retryCount() + 1);
                HandoffState updatedState = retryHandler.prepareForRetry(state, errorType, errorOutput);
                yield new RecoveryResult(true, updatedState, "Waited and prepared for retry");
            }

            case ABORT -> {
                HandoffState abortedState = recordAbort(state, errorType, errorOutput);
                yield new RecoveryResult(false, abortedState,
                        "Workflow aborted after " + state.retryCount() + " retries: " + errorType.getDescription());
            }
        };
    }

    /**
     * Rolls back to a previous commit and prepares state for retry.
     *
     * @param state          the current state
     * @param errorType      the type of error
     * @param errorOutput    the error output
     * @param rollbackCommit the commit to rollback to (null to skip rollback)
     * @return updated state ready for retry
     */
    public HandoffState rollbackAndPrepareRetry(
            HandoffState state,
            ErrorType errorType,
            String errorOutput,
            ObjectId rollbackCommit) {

        if (rollbackCommit != null) {
            try {
                gitOperations.rollbackToCommit(rollbackCommit);
                LOG.info("Rolled back to commit {}", rollbackCommit.abbreviate(7).name());
            } catch (GitOperationException e) {
                LOG.error("Failed to rollback to commit {}: {}", rollbackCommit.abbreviate(7).name(), e.getMessage());
                // Continue with retry anyway - the agent will see the error state
            }
        }

        return retryHandler.prepareForRetry(state, errorType, errorOutput);
    }

    /**
     * Records an abort in the state when all retries are exhausted.
     *
     * @param state       the current state
     * @param errorType   the final error type
     * @param errorOutput the final error output
     * @return state with abort information recorded
     */
    public HandoffState recordAbort(HandoffState state, ErrorType errorType, String errorOutput) {
        String abortMessage = String.format(
                "Workflow aborted after %d retries. Error type: %s",
                state.retryCount(),
                errorType.getDescription()
        );

        String detailedMessage = errorHandler.extractErrorMessage(errorOutput, 1000);

        return state.toBuilder()
                .error(abortMessage)
                .errorDetails(new ErrorDetails(
                        "ABORT_" + errorType.name(),
                        detailedMessage
                ))
                .build();
    }

    /**
     * Finds the commit to rollback to for a given phase.
     * Typically this is the commit before the current phase started.
     *
     * @param phase the current phase
     * @return the commit ID to rollback to, if available
     */
    public Optional<ObjectId> findRollbackCommit(Phase phase) {
        try {
            // For now, just use the latest commit as the rollback point
            // A more sophisticated implementation would track commits per phase
            return gitOperations.getLatestCommit();
        } catch (GitOperationException e) {
            LOG.warn("Could not find rollback commit: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Represents the possible recovery actions.
     */
    public enum RecoveryAction {
        /**
         * Continue without action (error was expected, e.g., test failure in RED phase).
         */
        CONTINUE,

        /**
         * Retry the phase with error context included in the prompt.
         */
        RETRY_WITH_CONTEXT,

        /**
         * Rollback to a previous commit before retrying.
         */
        ROLLBACK_AND_RETRY,

        /**
         * Wait (exponential backoff) before retrying.
         */
        WAIT_AND_RETRY,

        /**
         * Abort the workflow - retries exhausted or unrecoverable error.
         */
        ABORT
    }

    /**
     * Result of a recovery operation.
     *
     * @param shouldContinue whether the workflow should continue
     * @param updatedState   the state after recovery actions
     * @param message        a descriptive message about the recovery
     */
    public record RecoveryResult(
            boolean shouldContinue,
            HandoffState updatedState,
            String message
    ) {
    }
}
