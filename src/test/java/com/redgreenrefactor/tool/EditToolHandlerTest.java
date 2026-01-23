package com.redgreenrefactor.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EditToolHandlerTest {

    private EditToolHandler handler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        handler = new EditToolHandler();
    }

    @Test
    void getToolName_returnsEdit() {
        assertThat(handler.getToolName()).isEqualTo("Edit");
    }

    @Test
    void execute_replacesUniqueText() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello, World!");

        ToolResult result = handler.execute(Map.of(
                "file_path", file.toString(),
                "old_string", "World",
                "new_string", "Java"
        ));

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(file)).isEqualTo("Hello, Java!");
    }

    @Test
    void execute_returnsFailureForNonUniqueText() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello Hello Hello");

        ToolResult result = handler.execute(Map.of(
                "file_path", file.toString(),
                "old_string", "Hello",
                "new_string", "Hi"
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("found 3 times");
    }

    @Test
    void execute_returnsFailureForTextNotFound() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello, World!");

        ToolResult result = handler.execute(Map.of(
                "file_path", file.toString(),
                "old_string", "Goodbye",
                "new_string", "Hi"
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("not found");
    }

    @Test
    void execute_returnsFailureForMissingFile() throws Exception {
        ToolResult result = handler.execute(Map.of(
                "file_path", tempDir.resolve("nonexistent.txt").toString(),
                "old_string", "test",
                "new_string", "new"
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("not found");
    }

    @Test
    void execute_returnsFailureForMissingParameters() throws Exception {
        ToolResult result1 = handler.execute(Map.of());
        assertThat(result1.success()).isFalse();
        assertThat(result1.error()).contains("file_path is required");

        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "test");

        ToolResult result2 = handler.execute(Map.of("file_path", file.toString()));
        assertThat(result2.success()).isFalse();
        assertThat(result2.error()).contains("old_string is required");

        ToolResult result3 = handler.execute(Map.of(
                "file_path", file.toString(),
                "old_string", "test"
        ));
        assertThat(result3.success()).isFalse();
        assertThat(result3.error()).contains("new_string is required");
    }

    @Test
    void execute_handlesMultilineReplacement() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Line 1\nLine 2\nLine 3");

        ToolResult result = handler.execute(Map.of(
                "file_path", file.toString(),
                "old_string", "Line 2",
                "new_string", "New Line 2\nExtra Line"
        ));

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(file)).isEqualTo("Line 1\nNew Line 2\nExtra Line\nLine 3");
    }

    @Test
    void execute_canDeleteText() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello, World!");

        ToolResult result = handler.execute(Map.of(
                "file_path", file.toString(),
                "old_string", ", World",
                "new_string", ""
        ));

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(file)).isEqualTo("Hello!");
    }
}
