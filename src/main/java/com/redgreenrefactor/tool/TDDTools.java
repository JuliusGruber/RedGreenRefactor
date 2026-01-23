package com.redgreenrefactor.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;

import java.util.List;
import java.util.Map;

/**
 * Defines the tools available to TDD agents.
 * Tool names use PascalCase to match Claude Code conventions.
 */
public final class TDDTools {

    private TDDTools() {
        // Utility class
    }

    /**
     * Returns all available tools for TDD agents.
     */
    public static List<Tool> getAllTools() {
        return List.of(
                createReadTool(),
                createWriteTool(),
                createEditTool(),
                createBashTool(),
                createGlobTool(),
                createGrepTool()
        );
    }

    /**
     * Creates the Read tool for reading file contents.
     */
    public static Tool createReadTool() {
        return Tool.builder()
                .name("Read")
                .description("Read the contents of a file. Returns the file content as text.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(JsonValue.from(Map.of(
                                "file_path", Map.of(
                                        "type", "string",
                                        "description", "The absolute path to the file to read"
                                )
                        )))
                        .putAdditionalProperty("required", JsonValue.from(List.of("file_path")))
                        .build())
                .build();
    }

    /**
     * Creates the Write tool for writing content to a file.
     */
    public static Tool createWriteTool() {
        return Tool.builder()
                .name("Write")
                .description("Write content to a file. Creates the file if it doesn't exist, overwrites if it does.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(JsonValue.from(Map.of(
                                "file_path", Map.of(
                                        "type", "string",
                                        "description", "The absolute path to the file to write"
                                ),
                                "content", Map.of(
                                        "type", "string",
                                        "description", "The content to write to the file"
                                )
                        )))
                        .putAdditionalProperty("required", JsonValue.from(List.of("file_path", "content")))
                        .build())
                .build();
    }

    /**
     * Creates the Edit tool for editing files by replacing text.
     * Parameter names match Claude's interface: file_path, old_string, new_string.
     */
    public static Tool createEditTool() {
        return Tool.builder()
                .name("Edit")
                .description("Edit a file by replacing text. The old_string must exist exactly once in the file.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(JsonValue.from(Map.of(
                                "file_path", Map.of(
                                        "type", "string",
                                        "description", "The absolute path to the file to edit"
                                ),
                                "old_string", Map.of(
                                        "type", "string",
                                        "description", "The exact text to replace (must be unique in file)"
                                ),
                                "new_string", Map.of(
                                        "type", "string",
                                        "description", "The replacement text"
                                )
                        )))
                        .putAdditionalProperty("required", JsonValue.from(List.of("file_path", "old_string", "new_string")))
                        .build())
                .build();
    }

    /**
     * Creates the Bash tool for executing shell commands.
     */
    public static Tool createBashTool() {
        return Tool.builder()
                .name("Bash")
                .description("Execute a bash/shell command and return its output. Use for running tests, git operations, etc.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(JsonValue.from(Map.of(
                                "command", Map.of(
                                        "type", "string",
                                        "description", "The shell command to execute"
                                )
                        )))
                        .putAdditionalProperty("required", JsonValue.from(List.of("command")))
                        .build())
                .build();
    }

    /**
     * Creates the Glob tool for finding files matching a pattern.
     */
    public static Tool createGlobTool() {
        return Tool.builder()
                .name("Glob")
                .description("Find files matching a glob pattern. Returns a list of matching file paths.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(JsonValue.from(Map.of(
                                "pattern", Map.of(
                                        "type", "string",
                                        "description", "The glob pattern to match (e.g., '**/*.java', 'src/**/*.ts')"
                                ),
                                "path", Map.of(
                                        "type", "string",
                                        "description", "The directory to search in (optional, defaults to current directory)"
                                )
                        )))
                        .putAdditionalProperty("required", JsonValue.from(List.of("pattern")))
                        .build())
                .build();
    }

    /**
     * Creates the Grep tool for searching text patterns in files.
     */
    public static Tool createGrepTool() {
        return Tool.builder()
                .name("Grep")
                .description("Search for text patterns in files using regular expressions. Returns matching lines with file and line numbers.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(JsonValue.from(Map.of(
                                "pattern", Map.of(
                                        "type", "string",
                                        "description", "The regex pattern to search for"
                                ),
                                "path", Map.of(
                                        "type", "string",
                                        "description", "The directory or file to search in (optional, defaults to current directory)"
                                ),
                                "glob", Map.of(
                                        "type", "string",
                                        "description", "Glob pattern to filter files (e.g., '*.java', '*.ts')"
                                )
                        )))
                        .putAdditionalProperty("required", JsonValue.from(List.of("pattern")))
                        .build())
                .build();
    }
}
