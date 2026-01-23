package com.redgreenrefactor.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Handler for the Edit tool.
 * Edits a file by replacing text. The old_string must be unique in the file.
 */
public class EditToolHandler implements ToolExecutor {

    @Override
    public String getToolName() {
        return "Edit";
    }

    @Override
    public ToolResult execute(Map<String, Object> inputs) throws ToolExecutionException {
        String filePath = (String) inputs.get("file_path");
        String oldString = (String) inputs.get("old_string");
        String newString = (String) inputs.get("new_string");

        if (filePath == null || filePath.isBlank()) {
            return ToolResult.failure("file_path is required");
        }
        if (oldString == null) {
            return ToolResult.failure("old_string is required");
        }
        if (newString == null) {
            return ToolResult.failure("new_string is required");
        }

        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            return ToolResult.failure("File not found: " + filePath);
        }

        try {
            String content = Files.readString(path);

            // Count occurrences of old_string
            int occurrences = countOccurrences(content, oldString);

            if (occurrences == 0) {
                return ToolResult.failure("old_string not found in file: " + filePath);
            }
            if (occurrences > 1) {
                return ToolResult.failure(
                        "old_string found " + occurrences + " times in file. It must be unique. " +
                        "Provide more context to make it unique.");
            }

            String newContent = content.replace(oldString, newString);
            Files.writeString(path, newContent);

            return ToolResult.success("Successfully edited " + filePath);
        } catch (IOException e) {
            throw new ToolExecutionException("Failed to edit file: " + filePath, e);
        }
    }

    private int countOccurrences(String content, String search) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(search, index)) != -1) {
            count++;
            index += search.length();
        }
        return count;
    }
}
