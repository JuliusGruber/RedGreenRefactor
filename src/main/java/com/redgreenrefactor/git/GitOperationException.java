package com.redgreenrefactor.git;

/**
 * Exception thrown when Git operations fail.
 */
public class GitOperationException extends Exception {

    /**
     * Creates a new GitOperationException with the specified message.
     *
     * @param message The error message
     */
    public GitOperationException(String message) {
        super(message);
    }

    /**
     * Creates a new GitOperationException with the specified message and cause.
     *
     * @param message The error message
     * @param cause   The underlying cause
     */
    public GitOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
