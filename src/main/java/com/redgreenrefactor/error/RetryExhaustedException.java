package com.redgreenrefactor.error;

/**
 * Exception thrown when all retry attempts have been exhausted.
 */
public class RetryExhaustedException extends Exception {

    private final int attemptsMade;

    /**
     * Creates a new RetryExhaustedException.
     *
     * @param message      the detail message
     * @param cause        the underlying cause
     * @param attemptsMade the number of attempts that were made
     */
    public RetryExhaustedException(String message, Throwable cause, int attemptsMade) {
        super(message, cause);
        this.attemptsMade = attemptsMade;
    }

    /**
     * Creates a new RetryExhaustedException without a cause.
     *
     * @param message      the detail message
     * @param attemptsMade the number of attempts that were made
     */
    public RetryExhaustedException(String message, int attemptsMade) {
        super(message);
        this.attemptsMade = attemptsMade;
    }

    /**
     * Returns the number of attempts that were made before giving up.
     */
    public int getAttemptsMade() {
        return attemptsMade;
    }
}
