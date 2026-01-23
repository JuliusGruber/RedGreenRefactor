package com.redgreenrefactor.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WriteToolHandlerTest {

    private WriteToolHandler handler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        handler = new WriteToolHandler();
    }

    @Test
    void getToolName_returnsWrite() {
        assertThat(handler.getToolName()).isEqualTo("Write");
    }

    @Test
    void execute_createsNewFile() throws Exception {
        Path file = tempDir.resolve("new.txt");

        ToolResult result = handler.execute(Map.of(
                "file_path", file.toString(),
                "content", "New content"
        ));

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(file)).isEqualTo("New content");
    }

    @Test
    void execute_overwritesExistingFile() throws Exception {
        Path file = tempDir.resolve("existing.txt");
        Files.writeString(file, "Old content");

        ToolResult result = handler.execute(Map.of(
                "file_path", file.toString(),
                "content", "New content"
        ));

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(file)).isEqualTo("New content");
    }

    @Test
    void execute_createsParentDirectories() throws Exception {
        Path file = tempDir.resolve("nested/dir/file.txt");

        ToolResult result = handler.execute(Map.of(
                "file_path", file.toString(),
                "content", "Content"
        ));

        assertThat(result.success()).isTrue();
        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.readString(file)).isEqualTo("Content");
    }

    @Test
    void execute_returnsFailureForMissingFilePath() throws Exception {
        ToolResult result = handler.execute(Map.of("content", "test"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("file_path is required");
    }

    @Test
    void execute_returnsFailureForMissingContent() throws Exception {
        ToolResult result = handler.execute(Map.of("file_path", tempDir.resolve("test.txt").toString()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("content is required");
    }

    @Test
    void execute_writesEmptyContent() throws Exception {
        Path file = tempDir.resolve("empty.txt");

        ToolResult result = handler.execute(Map.of(
                "file_path", file.toString(),
                "content", ""
        ));

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(file)).isEmpty();
    }
}
