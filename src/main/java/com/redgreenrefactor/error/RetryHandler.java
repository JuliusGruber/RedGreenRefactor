package com.redgreenrefactor.error;

import com.redgreenrefactor.model.ErrorDetails;
import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Handles retry logic for failed phase executions.
 * <p>
 * Implements exponential backoff with configurable maximum retries.
 * Each retry includes error context in the updated state for
 * the agent to understand what went wrong.
 */
public class RetryHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RetryHandler.class);

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long[] DEFAULT_BACKOFF_MS = {1000, 2000, 4000};

    private final int maxRetries;
    private final long[] backoffMs;
    private final TDDErrorHandler errorHandler;

    /**
     * Creates a RetryHandler with default settings (3 retries, 1s/2s/4s backoff).
     */
    public RetryHandler() {
        this(DEFAULT_MAX_RETRIES, DEFAULT_BACKOFF_MS, new TDDErrorHandler());
    }

    /**
     * Creates a RetryHandler with a custom error handler.
     *
     * @param errorHandler the error handler to use for classification
     */
    public RetryHandler(TDDErrorHandler errorHandler) {
        this(DEFAULT_MAX_RETRIES, DEFAULT_BACKOFF_MS, errorHandler);
    }

    /**
     * Creates a RetryHandler with custom settings.
     *
     * @param maxRetries   maximum number of retry attempts
     * @param backoffMs    array of backoff durations in milliseconds (one per retry)
     * @param errorHandler the error handler to use for classification
     */
    public RetryHandler(int maxRetries, long[] backoffMs, TDDErrorHandler errorHandler) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative");
        }
        this.maxRetries = maxRetries;
        this.backoffMs = backoffMs != null ? backoffMs.clone() : DEFAULT_BACKOFF_MS;
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler cannot be null");
    }

    /**
     * Returns the maximum number of retries.
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Returns whether a retry should be attempted based on the current retry count.
     *
     * @param currentRetryCount the number of retries already attempted
     * @return true if another retry is allowed
     */
    public boolean shouldRetry(int currentRetryCount) {
        return currentRetryCount < maxRetries;
    }

    /**
     * Returns whether a retry should be attempted for the given error type.
     *
     * @param errorType         the type of error that occurred
     * @param currentRetryCount the number of retries already attempted
     * @return true if a retry should be attempted
     */
    public boolean shouldRetry(ErrorType errorType, int currentRetryCount) {
        if (!shouldRetry(currentRetryCount)) {
            return false;
        }
        return errorType.isRetriable();
    }

    /**
     * Waits for the appropriate backoff duration before a retry.
     *
     * @param retryAttempt the retry attempt number (1-based)
     * @throws InterruptedException if the thread is interrupted during sleep
     */
    public void waitBeforeRetry(int retryAttempt) throws InterruptedException {
        if (retryAttempt <= 0) {
            return;
        }

        int index = Math.min(retryAttempt - 1, backoffMs.length - 1);
        long waitTime = backoffMs[index];

        LOG.info("Waiting {}ms before retry attempt {}", waitTime, retryAttempt);
        Thread.sleep(waitTime);
    }

    /**
     * Gets the backoff duration for a specific retry attempt.
     *
     * @param retryAttempt the retry attempt number (1-based)
     * @return the backoff duration in milliseconds
     */
    public long getBackoffMs(int retryAttempt) {
        if (retryAttempt <= 0) {
            return 0;
        }
        int index = Math.min(retryAttempt - 1, backoffMs.length - 1);
        return backoffMs[index];
    }

    /**
     * Updates the handoff state with error information for retry.
     *
     * @param state       the current state
     * @param errorType   the type of error that occurred
     * @param errorOutput the error output/message
     * @return updated state with error context and incremented retry count
     */
    public HandoffState prepareForRetry(HandoffState state, ErrorType errorType, String errorOutput) {
        Objects.requireNonNull(state, "state cannot be null");
        Objects.requireNonNull(errorType, "errorType cannot be null");

        String errorMessage = errorHandler.extractErrorMessage(errorOutput, 500);

        return state.toBuilder()
                .error(errorMessage)
                .errorDetails(new ErrorDetails(errorType.name(), errorMessage))
                .retryCount(state.retryCount() + 1)
                .build();
    }

    /**
     * Builds an error context string to include in retry prompts.
     * This helps the agent understand what went wrong in the previous attempt.
     *
     * @param state     the current state with error information
     * @param phase     the phase that failed
     * @param errorType the type of error
     * @return a formatted error context string
     */
    public String buildRetryContext(HandoffState state, Phase phase, ErrorType errorType) {
        StringBuilder context = new StringBuilder();
        context.append("## Previous Attempt Failed\n\n");
        context.append("This is retry attempt ").append(state.retryCount()).append(" of ").append(maxRetries).append(".\n\n");
        context.append("**Error Type:** ").append(errorType.getDescription()).append("\n\n");

        if (state.error() != null && !state.error().isBlank()) {
            context.append("**Error Details:**\n```\n");
            context.append(state.error());
            context.append("\n```\n\n");
        }

        // Add phase-specific guidance
        context.append(getPhaseSpecificGuidance(phase, errorType));

        return context.toString();
    }

    private String getPhaseSpecificGuidance(Phase phase, ErrorType errorType) {
        return switch (errorType) {
            case COMPILATION -> "Please fix the compilation errors before proceeding. " +
                    "Check for missing imports, typos, and type mismatches.";

            case TEST_FAILURE -> switch (phase) {
                case RED -> "The test is failing as expected for RED phase. This is correct behavior.";
                case GREEN -> "Tests are failing. Please fix the implementation to make all tests pass.";
                case REFACTOR -> "Refactoring broke tests. Please undo the problematic changes " +
                        "and try a different refactoring approach.";
                default -> "Please investigate the test failures and fix the underlying issues.";
            };

            case UNEXPECTED_PASS -> "The test passed but should have failed in RED phase. " +
                    "Please add a proper assertion that will fail until the feature is implemented. " +
                    "The test should assert the expected behavior that doesn't exist yet.";

            case TIMEOUT -> "The previous command timed out. " +
                    "Consider breaking the operation into smaller steps or optimizing the approach.";

            case RATE_LIMIT -> "API rate limit was hit. The system has waited before retrying.";

            case NETWORK -> "A network error occurred. The system is retrying the operation.";

            case UNKNOWN -> "An unexpected error occurred. Please review the error details " +
                    "and try a different approach.";
        };
    }

    /**
     * Executes an operation with retry logic.
     *
     * @param operation   the operation to execute
     * @param errorType   the type of error (for logging)
     * @param maxAttempts maximum number of attempts (including initial)
     * @param <T>         the return type of the operation
     * @return the result of the operation
     * @throws RetryExhaustedException if all retry attempts fail
     */
    public <T> T executeWithRetry(Supplier<T> operation, ErrorType errorType, int maxAttempts)
            throws RetryExhaustedException, InterruptedException {

        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                LOG.warn("Attempt {} failed with {}: {}", attempt, errorType, e.getMessage());

                if (attempt < maxAttempts) {
                    waitBeforeRetry(attempt);
                }
            }
        }

        throw new RetryExhaustedException(
                "Operation failed after " + maxAttempts + " attempts",
                lastException,
                maxAttempts
        );
    }
}
