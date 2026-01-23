package com.redgreenrefactor.tool;

import java.util.Map;

/**
 * Interface for executing tools.
 * Each tool handler implements this interface to provide execution logic.
 */
public interface ToolExecutor {

    /**
     * Returns the name of the tool this executor handles.
     */
    String getToolName();

    /**
     * Executes the tool with the given input parameters.
     *
     * @param inputs the tool input parameters as a map
     * @return the result of the tool execution
     * @throws ToolExecutionException if execution fails
     */
    ToolResult execute(Map<String, Object> inputs) throws ToolExecutionException;
}
