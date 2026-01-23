package com.redgreenrefactor.tool;

/**
 * Represents the result of a tool execution.
 */
public record ToolResult(
        boolean success,
        String output,
        String error
) {
    /**
     * Creates a successful result with the given output.
     */
    public static ToolResult success(String output) {
        return new ToolResult(true, output, null);
    }

    /**
     * Creates a failed result with the given error message.
     */
    public static ToolResult failure(String error) {
        return new ToolResult(false, null, error);
    }

    /**
     * Returns the content to send back to the agent.
     * Returns the output if successful, otherwise the error.
     */
    public String getContent() {
        return success ? output : error;
    }
}
