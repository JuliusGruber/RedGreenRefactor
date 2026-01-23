package com.redgreenrefactor.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobToolHandlerTest {

    private GlobToolHandler handler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        handler = new GlobToolHandler(tempDir);
    }

    @Test
    void getToolName_returnsGlob() {
        assertThat(handler.getToolName()).isEqualTo("Glob");
    }

    @Test
    void execute_findsMatchingFiles() throws Exception {
        Files.writeString(tempDir.resolve("file1.txt"), "content");
        Files.writeString(tempDir.resolve("file2.txt"), "content");
        Files.writeString(tempDir.resolve("file.java"), "content");

        ToolResult result = handler.execute(Map.of("pattern", "*.txt"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("file1.txt");
        assertThat(result.output()).contains("file2.txt");
        assertThat(result.output()).doesNotContain("file.java");
    }

    @Test
    void execute_findsFilesInSubdirectories() throws Exception {
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("nested.txt"), "content");
        Files.writeString(tempDir.resolve("root.txt"), "content");

        // Pattern **/*.txt only matches files in subdirectories
        ToolResult result = handler.execute(Map.of("pattern", "**/*.txt"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("nested.txt");
        // Note: **/*.txt doesn't match root level files - use *.txt or **/* for those
    }

    @Test
    void execute_findsFilesAtRootAndSubdirectories() throws Exception {
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("nested.txt"), "content");
        Files.writeString(tempDir.resolve("root.txt"), "content");

        // Use *.txt to match root level files
        ToolResult rootResult = handler.execute(Map.of("pattern", "*.txt"));

        assertThat(rootResult.success()).isTrue();
        assertThat(rootResult.output()).contains("root.txt");
    }

    @Test
    void execute_returnsNoFilesMessage() throws Exception {
        ToolResult result = handler.execute(Map.of("pattern", "*.nonexistent"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("No files found");
    }

    @Test
    void execute_returnsFailureForMissingPattern() throws Exception {
        ToolResult result = handler.execute(Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("pattern is required");
    }

    @Test
    void execute_usesCustomPath() throws Exception {
        Path subDir = tempDir.resolve("custom");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("file.txt"), "content");
        Files.writeString(tempDir.resolve("other.txt"), "content");

        ToolResult result = handler.execute(Map.of(
                "pattern", "*.txt",
                "path", subDir.toString()
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("file.txt");
        assertThat(result.output()).doesNotContain("other.txt");
    }

    @Test
    void execute_returnsFailureForNonexistentPath() throws Exception {
        ToolResult result = handler.execute(Map.of(
                "pattern", "*.txt",
                "path", tempDir.resolve("nonexistent").toString()
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("not found");
    }

    @Test
    void execute_findsMultipleExtensions() throws Exception {
        Files.writeString(tempDir.resolve("file.java"), "content");
        Files.writeString(tempDir.resolve("file.class"), "content");
        Files.writeString(tempDir.resolve("file.txt"), "content");

        ToolResult result = handler.execute(Map.of("pattern", "*.{java,txt}"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("file.java");
        assertThat(result.output()).contains("file.txt");
        assertThat(result.output()).doesNotContain("file.class");
    }
}
