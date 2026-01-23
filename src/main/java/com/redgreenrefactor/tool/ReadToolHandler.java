package com.redgreenrefactor.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Handler for the Read tool.
 * Reads the contents of a file and returns it as text.
 */
public class ReadToolHandler implements ToolExecutor {

    @Override
    public String getToolName() {
        return "Read";
    }

    @Override
    public ToolResult execute(Map<String, Object> inputs) throws ToolExecutionException {
        String filePath = (String) inputs.get("file_path");
        if (filePath == null || filePath.isBlank()) {
            return ToolResult.failure("file_path is required");
        }

        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            return ToolResult.failure("File not found: " + filePath);
        }

        if (!Files.isRegularFile(path)) {
            return ToolResult.failure("Not a regular file: " + filePath);
        }

        try {
            String content = Files.readString(path);
            return ToolResult.success(content);
        } catch (IOException e) {
            throw new ToolExecutionException("Failed to read file: " + filePath, e);
        }
    }
}
