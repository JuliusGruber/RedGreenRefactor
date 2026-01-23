package com.redgreenrefactor.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Handler for the Write tool.
 * Writes content to a file, creating parent directories if needed.
 */
public class WriteToolHandler implements ToolExecutor {

    @Override
    public String getToolName() {
        return "Write";
    }

    @Override
    public ToolResult execute(Map<String, Object> inputs) throws ToolExecutionException {
        String filePath = (String) inputs.get("file_path");
        String content = (String) inputs.get("content");

        if (filePath == null || filePath.isBlank()) {
            return ToolResult.failure("file_path is required");
        }
        if (content == null) {
            return ToolResult.failure("content is required");
        }

        Path path = Path.of(filePath);

        try {
            // Create parent directories if they don't exist
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.writeString(path, content);
            return ToolResult.success("Successfully wrote " + content.length() + " characters to " + filePath);
        } catch (IOException e) {
            throw new ToolExecutionException("Failed to write file: " + filePath, e);
        }
    }
}
