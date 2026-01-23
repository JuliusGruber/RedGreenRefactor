package com.redgreenrefactor.orchestrator;

/**
 * Exception thrown when orchestration fails.
 * This includes errors during phase execution, state transitions, or workflow management.
 */
public class OrchestratorException extends RuntimeException {

    /**
     * Creates a new OrchestratorException with the specified message.
     *
     * @param message the error message
     */
    public OrchestratorException(String message) {
        super(message);
    }

    /**
     * Creates a new OrchestratorException with the specified message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public OrchestratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
