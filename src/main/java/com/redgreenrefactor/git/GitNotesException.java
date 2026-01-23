package com.redgreenrefactor.git;

/**
 * Exception thrown when Git Notes operations fail.
 * Provides context for recovery guidance.
 */
public class GitNotesException extends Exception {

    /**
     * Creates a new GitNotesException with the specified message.
     *
     * @param message The error message
     */
    public GitNotesException(String message) {
        super(message);
    }

    /**
     * Creates a new GitNotesException with the specified message and cause.
     *
     * @param message The error message
     * @param cause   The underlying cause
     */
    public GitNotesException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns a recovery hint for the user.
     *
     * @return Recovery guidance message
     */
    public String getRecoveryHint() {
        return "Run 'tdd-orchestrator repair-notes' to recover, or manually inspect with 'git notes --ref=tdd-handoffs list'";
    }
}
