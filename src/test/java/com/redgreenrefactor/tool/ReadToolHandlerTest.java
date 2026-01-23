package com.redgreenrefactor.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReadToolHandlerTest {

    private ReadToolHandler handler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        handler = new ReadToolHandler();
    }

    @Test
    void getToolName_returnsRead() {
        assertThat(handler.getToolName()).isEqualTo("Read");
    }

    @Test
    void execute_readsFileContents() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello, World!");

        ToolResult result = handler.execute(Map.of("file_path", file.toString()));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("Hello, World!");
    }

    @Test
    void execute_returnsFailureForMissingFile() throws Exception {
        ToolResult result = handler.execute(Map.of("file_path", tempDir.resolve("nonexistent.txt").toString()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("not found");
    }

    @Test
    void execute_returnsFailureForMissingFilePath() throws Exception {
        ToolResult result = handler.execute(Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("file_path is required");
    }

    @Test
    void execute_returnsFailureForDirectory() throws Exception {
        ToolResult result = handler.execute(Map.of("file_path", tempDir.toString()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Not a regular file");
    }

    @Test
    void execute_readsMultilineFile() throws Exception {
        Path file = tempDir.resolve("multiline.txt");
        Files.writeString(file, "Line 1\nLine 2\nLine 3");

        ToolResult result = handler.execute(Map.of("file_path", file.toString()));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("Line 1\nLine 2\nLine 3");
    }
}
