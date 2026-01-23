package com.redgreenrefactor.tool;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Dispatches tool calls to the appropriate handler.
 */
public class ToolDispatcher {

    private final Map<String, ToolExecutor> handlers;

    /**
     * Creates a ToolDispatcher with handlers for all standard tools.
     *
     * @param workingDirectory the working directory for file and bash operations
     * @param bashTimeoutSeconds timeout for bash commands in seconds
     */
    public ToolDispatcher(Path workingDirectory, long bashTimeoutSeconds) {
        this.handlers = new HashMap<>();

        // Register all handlers
        registerHandler(new ReadToolHandler());
        registerHandler(new WriteToolHandler());
        registerHandler(new EditToolHandler());
        registerHandler(new BashToolHandler(workingDirectory, bashTimeoutSeconds));
        registerHandler(new GlobToolHandler(workingDirectory));
        registerHandler(new GrepToolHandler(workingDirectory));
    }

    /**
     * Creates a ToolDispatcher with default timeout (120 seconds).
     */
    public ToolDispatcher(Path workingDirectory) {
        this(workingDirectory, 120);
    }

    /**
     * Registers a tool handler.
     */
    public void registerHandler(ToolExecutor handler) {
        handlers.put(handler.getToolName(), handler);
    }

    /**
     * Dispatches a tool call to the appropriate handler.
     *
     * @param toolName the name of the tool to execute
     * @param inputs the input parameters for the tool
     * @return the result of the tool execution
     * @throws ToolExecutionException if execution fails or tool is unknown
     */
    public ToolResult dispatch(String toolName, Map<String, Object> inputs) throws ToolExecutionException {
        ToolExecutor handler = handlers.get(toolName);
        if (handler == null) {
            throw new ToolExecutionException("Unknown tool: " + toolName);
        }

        return handler.execute(inputs);
    }

    /**
     * Checks if a tool is registered.
     */
    public boolean hasHandler(String toolName) {
        return handlers.containsKey(toolName);
    }
}
