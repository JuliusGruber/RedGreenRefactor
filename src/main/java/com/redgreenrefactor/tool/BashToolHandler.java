package com.redgreenrefactor.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handler for the Bash tool.
 * Executes shell commands with configurable timeout.
 */
public class BashToolHandler implements ToolExecutor {

    private final Path workingDirectory;
    private final long timeoutSeconds;

    /**
     * Creates a BashToolHandler with the specified working directory and default timeout (120s).
     */
    public BashToolHandler(Path workingDirectory) {
        this(workingDirectory, 120);
    }

    /**
     * Creates a BashToolHandler with the specified working directory and timeout.
     */
    public BashToolHandler(Path workingDirectory, long timeoutSeconds) {
        this.workingDirectory = workingDirectory;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String getToolName() {
        return "Bash";
    }

    @Override
    public ToolResult execute(Map<String, Object> inputs) throws ToolExecutionException {
        String command = (String) inputs.get("command");

        if (command == null || command.isBlank()) {
            return ToolResult.failure("command is required");
        }

        try {
            ProcessBuilder processBuilder = createProcessBuilder(command);
            processBuilder.directory(workingDirectory.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            CompletableFuture<String> outputFuture = readProcessOutputAsync(process);

            // Wait for process with timeout
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                outputFuture.cancel(true);
                return ToolResult.failure("Command timed out after " + timeoutSeconds + " seconds");
            }

            String outputStr = getOutputWithTimeout(outputFuture);

            int exitCode = process.exitValue();

            if (exitCode == 0) {
                return ToolResult.success(outputStr.isEmpty() ? "(no output)" : outputStr);
            } else {
                return ToolResult.failure("Exit code " + exitCode + "\n" + outputStr);
            }
        } catch (IOException | InterruptedException e) {
            throw new ToolExecutionException("Failed to execute command: " + command, e);
        }
    }

    private String getOutputWithTimeout(CompletableFuture<String> outputFuture) {
        try {
            return outputFuture.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return "(output collection timed out)";
        } catch (ExecutionException | InterruptedException e) {
            return "(error collecting output)";
        }
    }

    private CompletableFuture<String> readProcessOutputAsync(Process process) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (IOException e) {
                output.append("Error reading output: ").append(e.getMessage());
            }
            return output.toString().trim();
        });
    }

    private ProcessBuilder createProcessBuilder(String command) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return new ProcessBuilder("cmd.exe", "/c", command);
        }
        return new ProcessBuilder("sh", "-c", command);
    }
}
