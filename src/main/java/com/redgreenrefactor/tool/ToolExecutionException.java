package com.redgreenrefactor.tool;

/**
 * Exception thrown when a tool execution fails.
 */
public class ToolExecutionException extends Exception {

    public ToolExecutionException(String message) {
        super(message);
    }

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
