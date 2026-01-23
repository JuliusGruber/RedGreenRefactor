package com.redgreenrefactor.agent;

/**
 * Exception thrown when an agent invocation fails.
 */
public class AgentInvocationException extends RuntimeException {

    /**
     * Creates a new AgentInvocationException with the specified message.
     *
     * @param message the error message
     */
    public AgentInvocationException(String message) {
        super(message);
    }

    /**
     * Creates a new AgentInvocationException with the specified message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public AgentInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
