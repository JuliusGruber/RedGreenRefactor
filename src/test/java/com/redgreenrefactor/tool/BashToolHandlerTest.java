package com.redgreenrefactor.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BashToolHandlerTest {

    private BashToolHandler handler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        handler = new BashToolHandler(tempDir);
    }

    @Test
    void getToolName_returnsBash() {
        assertThat(handler.getToolName()).isEqualTo("Bash");
    }

    @Test
    void execute_runsSimpleCommand() throws Exception {
        ToolResult result = handler.execute(Map.of("command", "echo Hello"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Hello");
    }

    @Test
    void execute_capturesStdout() throws Exception {
        ToolResult result = handler.execute(Map.of("command", "echo Line1 && echo Line2"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Line1");
        assertThat(result.output()).contains("Line2");
    }

    @Test
    void execute_returnsFailureForFailingCommand() throws Exception {
        // Use a command that will fail on any OS
        ToolResult result = handler.execute(Map.of("command", "exit 1"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Exit code 1");
    }

    @Test
    void execute_returnsFailureForMissingCommand() throws Exception {
        ToolResult result = handler.execute(Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("command is required");
    }

    @Test
    void execute_usesWorkingDirectory() throws Exception {
        // Use a cross-platform way to verify working directory
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win") ? "cd" : "pwd";

        ToolResult result = handler.execute(Map.of("command", command));

        assertThat(result.success()).isTrue();
        // The output should contain the temp directory path (normalize paths for comparison)
        String normalizedOutput = result.output().toLowerCase().replace("\\", "/");
        String normalizedTempDir = tempDir.toString().toLowerCase().replace("\\", "/");
        assertThat(normalizedOutput).contains(normalizedTempDir);
    }

    @Test
    void execute_handlesCommandTimeout() throws Exception {
        // This test verifies the timeout logic works by using a short timeout
        // with a long-running command.
        // Use a separate temp dir to avoid cleanup issues when process is forcibly killed.
        Path timeoutTestDir = java.nio.file.Files.createTempDirectory("bash-timeout-test");
        try {
            BashToolHandler shortTimeoutHandler = new BashToolHandler(timeoutTestDir, 2);

            // Use cross-platform commands: ping on Windows, sleep on Unix
            String os = System.getProperty("os.name").toLowerCase();
            String command = os.contains("win")
                    ? "ping -n 30 127.0.0.1"  // ping 30 times (~30 seconds)
                    : "sleep 30";

            ToolResult result = shortTimeoutHandler.execute(Map.of("command", command));

            assertThat(result.success()).isFalse();
            assertThat(result.error()).contains("timed out");
        } finally {
            // On Windows, give a brief moment for process handles to be released
            Thread.sleep(100);
            deleteDirectory(timeoutTestDir);
        }
    }

    private void deleteDirectory(Path dir) {
        try {
            java.nio.file.Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            java.nio.file.Files.deleteIfExists(path);
                        } catch (java.io.IOException ignored) {
                            // Best effort cleanup
                        }
                    });
        } catch (java.io.IOException ignored) {
            // Best effort cleanup
        }
    }

    @Test
    void execute_capturesStderr() throws Exception {
        // Use a command that writes to stderr
        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win")
                ? "echo error message 1>&2"
                : "echo 'error message' >&2";

        ToolResult result = handler.execute(Map.of("command", command));

        // Output should contain stderr (merged with stdout)
        assertThat(result.output()).contains("error message");
    }
}
