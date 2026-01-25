package com.redgreenrefactor;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.redgreenrefactor.agent.AgentInvoker;
import com.redgreenrefactor.cli.ConfigurationException;
import com.redgreenrefactor.cli.TddConfiguration;
import com.redgreenrefactor.cli.TestFrameworkDetector;
import com.redgreenrefactor.git.GitNotesException;
import com.redgreenrefactor.git.GitNotesManager;
import com.redgreenrefactor.git.GitNotesManager.HandoffEntry;
import com.redgreenrefactor.git.GitOperations;
import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.Phase;
import com.redgreenrefactor.orchestrator.PhaseExecutor;
import com.redgreenrefactor.orchestrator.TddOrchestrator;
import com.redgreenrefactor.orchestrator.WorkflowResult;
import com.redgreenrefactor.tool.ToolDispatcher;
import org.eclipse.jgit.lib.ObjectId;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Main CLI entry point for the TDD Orchestrator.
 * <p>
 * Usage:
 * <pre>
 *   tdd-orchestrator run "Add user authentication"
 *   tdd-orchestrator resume
 *   tdd-orchestrator status
 *   tdd-orchestrator history
 *   tdd-orchestrator rollback abc1234
 * </pre>
 */
@Command(
        name = "tdd-orchestrator",
        description = "Multi-agent TDD orchestrator using Claude AI",
        version = "1.0.0",
        mixinStandardHelpOptions = true,
        subcommands = {
                TddOrchestratorCLI.RunCommand.class,
                TddOrchestratorCLI.ResumeCommand.class,
                TddOrchestratorCLI.StatusCommand.class,
                TddOrchestratorCLI.HistoryCommand.class,
                TddOrchestratorCLI.RollbackCommand.class
        }
)
public class TddOrchestratorCLI implements Callable<Integer> {

    @Option(names = {"-p", "--project"}, description = "Project root directory", defaultValue = ".")
    Path projectRoot;

    @Override
    public Integer call() {
        // If no subcommand is provided, print help
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TddOrchestratorCLI()).execute(args);
        System.exit(exitCode);
    }

    // ========== RUN COMMAND ==========

    @Command(name = "run", description = "Run full TDD workflow for a feature request",
            mixinStandardHelpOptions = true)
    static class RunCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "The feature request to implement")
        String featureRequest;

        @Option(names = {"-p", "--project"}, description = "Project root directory", defaultValue = ".")
        Path projectRoot;

        @Override
        public Integer call() {
            PrintWriter out = new PrintWriter(System.out, true);
            PrintWriter err = new PrintWriter(System.err, true);

            try {
                out.println("🚀 Starting TDD Orchestrator");
                out.println("Feature: " + featureRequest);
                out.println();

                // Load configuration
                TddConfiguration config = TddConfiguration.load(projectRoot);
                out.println("Project root: " + config.getProjectRoot());
                out.println("Model: " + config.getModel());

                // Detect test framework
                TestFrameworkDetector detector = new TestFrameworkDetector(config.getProjectRoot());
                String testCommand = config.getTestCommand()
                        .or(detector::detectTestCommand)
                        .orElseThrow(() -> new ConfigurationException(
                                "No test framework detected. Set 'test.command' in tdd.properties"));
                out.println("Test command: " + testCommand);
                out.println();

                // Initialize components
                try (GitNotesManager notesManager = new GitNotesManager(config.getProjectRoot());
                     GitOperations gitOps = new GitOperations(config.getProjectRoot())) {

                    AnthropicClient client = AnthropicOkHttpClient.builder()
                            .apiKey(config.getAnthropicApiKey())
                            .build();

                    ToolDispatcher toolDispatcher = new ToolDispatcher(
                            config.getProjectRoot(),
                            config.getBashTimeoutSeconds());

                    AgentInvoker agentInvoker = new AgentInvoker(client, toolDispatcher);
                    PhaseExecutor phaseExecutor = new PhaseExecutor(agentInvoker, gitOps, notesManager);
                    TddOrchestrator orchestrator = new TddOrchestrator(phaseExecutor);

                    // Run workflow
                    out.println("Starting TDD cycle...");
                    WorkflowResult result = orchestrator.runWorkflow(featureRequest);

                    // Print result
                    out.println();
                    if (result.success()) {
                        out.println("✅ Workflow completed successfully!");
                        out.println("Cycles completed: " + result.completedCycles());
                    } else {
                        err.println("❌ Workflow failed: " + (result.error() != null ? result.error() : "Unknown error"));
                        return 1;
                    }
                }

                return 0;

            } catch (ConfigurationException e) {
                err.println("Configuration error: " + e.getMessage());
                return 2;
            } catch (IOException e) {
                err.println("IO error: " + e.getMessage());
                return 3;
            } catch (Exception e) {
                err.println("Unexpected error: " + e.getMessage());
                e.printStackTrace(err);
                return 1;
            }
        }
    }

    // ========== RESUME COMMAND ==========

    @Command(name = "resume", description = "Resume workflow from last handoff state",
            mixinStandardHelpOptions = true)
    static class ResumeCommand implements Callable<Integer> {

        @Option(names = {"-p", "--project"}, description = "Project root directory", defaultValue = ".")
        Path projectRoot;

        @Override
        public Integer call() {
            PrintWriter out = new PrintWriter(System.out, true);
            PrintWriter err = new PrintWriter(System.err, true);

            try {
                TddConfiguration config = TddConfiguration.load(projectRoot);

                try (GitNotesManager notesManager = new GitNotesManager(config.getProjectRoot());
                     GitOperations gitOps = new GitOperations(config.getProjectRoot())) {

                    // Find latest handoff
                    Optional<HandoffEntry> latestHandoff = notesManager.findLatestHandoff();
                    if (latestHandoff.isEmpty()) {
                        err.println("No handoff state found. Use 'run' to start a new workflow.");
                        return 1;
                    }

                    HandoffEntry entry = latestHandoff.get();
                    HandoffState state = entry.state();

                    out.println("🔄 Resuming from handoff");
                    out.println("Commit: " + entry.commitId().abbreviate(7).name());
                    out.println("Phase: " + state.phase());
                    out.println("Next phase: " + state.nextPhase());
                    out.println("Cycle: " + state.cycleNumber());

                    if (state.currentTest() != null) {
                        out.println("Current test: " + state.currentTest().description());
                    }

                    if (state.phase() == Phase.COMPLETE) {
                        out.println();
                        out.println("✅ Workflow already complete. Use 'run' to start a new workflow.");
                        return 0;
                    }

                    out.println();

                    // Initialize components
                    AnthropicClient client = AnthropicOkHttpClient.builder()
                            .apiKey(config.getAnthropicApiKey())
                            .build();

                    ToolDispatcher toolDispatcher = new ToolDispatcher(
                            config.getProjectRoot(),
                            config.getBashTimeoutSeconds());

                    AgentInvoker agentInvoker = new AgentInvoker(client, toolDispatcher);
                    PhaseExecutor phaseExecutor = new PhaseExecutor(agentInvoker, gitOps, notesManager);
                    TddOrchestrator orchestrator = new TddOrchestrator(phaseExecutor);

                    // Continue from current state
                    out.println("Continuing TDD cycle...");
                    WorkflowResult result = orchestrator.runWorkflow("Continue from previous state");

                    // Print result
                    out.println();
                    if (result.success()) {
                        out.println("✅ Workflow completed successfully!");
                        out.println("Cycles completed: " + result.completedCycles());
                    } else {
                        err.println("❌ Workflow failed: " + (result.error() != null ? result.error() : "Unknown error"));
                        return 1;
                    }
                }

                return 0;

            } catch (ConfigurationException e) {
                err.println("Configuration error: " + e.getMessage());
                return 2;
            } catch (IOException e) {
                err.println("IO error: " + e.getMessage());
                return 3;
            } catch (Exception e) {
                err.println("Unexpected error: " + e.getMessage());
                e.printStackTrace(err);
                return 1;
            }
        }
    }

    // ========== STATUS COMMAND ==========

    @Command(name = "status", description = "Show current workflow state",
            mixinStandardHelpOptions = true)
    static class StatusCommand implements Callable<Integer> {

        @Option(names = {"-p", "--project"}, description = "Project root directory", defaultValue = ".")
        Path projectRoot;

        @Override
        public Integer call() {
            PrintWriter out = new PrintWriter(System.out, true);
            PrintWriter err = new PrintWriter(System.err, true);

            try {
                TddConfiguration config = TddConfiguration.load(projectRoot);

                try (GitNotesManager notesManager = new GitNotesManager(config.getProjectRoot())) {

                    Optional<HandoffEntry> latestHandoff = notesManager.findLatestHandoff();
                    if (latestHandoff.isEmpty()) {
                        out.println("No active workflow.");
                        out.println("Use 'tdd-orchestrator run <feature>' to start a new workflow.");
                        return 0;
                    }

                    HandoffEntry entry = latestHandoff.get();
                    HandoffState state = entry.state();

                    out.println("📊 Current Workflow Status");
                    out.println("==========================");
                    out.println();
                    out.println("Commit:     " + entry.commitId().abbreviate(7).name());
                    out.println("Phase:      " + formatPhase(state.phase()));
                    out.println("Next Phase: " + formatPhase(state.nextPhase()));
                    out.println("Cycle:      " + state.cycleNumber());
                    out.println();

                    if (state.currentTest() != null) {
                        out.println("Current Test:");
                        out.println("  Description: " + state.currentTest().description());
                        if (state.currentTest().testFile() != null) {
                            out.println("  Test file:   " + state.currentTest().testFile());
                        }
                        if (state.currentTest().implFile() != null) {
                            out.println("  Impl file:   " + state.currentTest().implFile());
                        }
                        out.println();
                    }

                    if (state.testResult() != null) {
                        out.println("Test Result: " + state.testResult());
                    }

                    if (!state.completedTests().isEmpty()) {
                        out.println("Completed Tests: " + state.completedTests().size());
                        for (String test : state.completedTests()) {
                            out.println("  ✓ " + test);
                        }
                        out.println();
                    }

                    if (!state.pendingTests().isEmpty()) {
                        out.println("Pending Tests: " + state.pendingTests().size());
                        for (String test : state.pendingTests()) {
                            out.println("  ○ " + test);
                        }
                        out.println();
                    }

                    if (state.error() != null) {
                        out.println("⚠️  Error: " + state.error());
                        if (state.errorDetails() != null) {
                            out.println("   Type: " + state.errorDetails().type());
                            out.println("   Message: " + state.errorDetails().message());
                        }
                        out.println("   Retry count: " + state.retryCount());
                    }
                }

                return 0;

            } catch (ConfigurationException e) {
                err.println("Configuration error: " + e.getMessage());
                return 2;
            } catch (IOException e) {
                err.println("IO error: " + e.getMessage());
                return 3;
            } catch (GitNotesException e) {
                err.println("Git notes error: " + e.getMessage());
                return 3;
            }
        }

        private String formatPhase(Phase phase) {
            return switch (phase) {
                case PLAN -> "📋 PLAN (selecting next test)";
                case RED -> "🔴 RED (writing failing test)";
                case GREEN -> "🟢 GREEN (implementing code)";
                case REFACTOR -> "🔧 REFACTOR (cleaning up)";
                case COMPLETE -> "✅ COMPLETE";
            };
        }
    }

    // ========== HISTORY COMMAND ==========

    @Command(name = "history", description = "Show handoff history",
            mixinStandardHelpOptions = true)
    static class HistoryCommand implements Callable<Integer> {

        @Option(names = {"-p", "--project"}, description = "Project root directory", defaultValue = ".")
        Path projectRoot;

        @Option(names = {"-n", "--limit"}, description = "Limit number of entries", defaultValue = "20")
        int limit;

        @Override
        public Integer call() {
            PrintWriter out = new PrintWriter(System.out, true);
            PrintWriter err = new PrintWriter(System.err, true);

            try {
                TddConfiguration config = TddConfiguration.load(projectRoot);

                try (GitNotesManager notesManager = new GitNotesManager(config.getProjectRoot())) {

                    List<HandoffEntry> handoffs = notesManager.listAllHandoffs();
                    if (handoffs.isEmpty()) {
                        out.println("No handoff history found.");
                        return 0;
                    }

                    out.println("📜 Handoff History");
                    out.println("==================");
                    out.println();

                    int count = 0;
                    for (HandoffEntry entry : handoffs) {
                        if (count >= limit) {
                            out.println("... and " + (handoffs.size() - limit) + " more entries");
                            break;
                        }

                        HandoffState state = entry.state();
                        String commitId = entry.commitId().abbreviate(7).name();

                        String phaseSymbol = getPhaseSymbol(state.phase());
                        String testInfo = state.currentTest() != null
                                ? " - " + truncate(state.currentTest().description(), 40)
                                : "";

                        out.printf("%s [%s] Cycle %d: %s%s%n",
                                phaseSymbol,
                                commitId,
                                state.cycleNumber(),
                                state.phase(),
                                testInfo);

                        if (state.error() != null) {
                            out.println("   ⚠️  Error: " + truncate(state.error(), 60));
                        }

                        count++;
                    }

                    out.println();
                    out.println("Total: " + handoffs.size() + " handoffs");
                }

                return 0;

            } catch (ConfigurationException e) {
                err.println("Configuration error: " + e.getMessage());
                return 2;
            } catch (IOException e) {
                err.println("IO error: " + e.getMessage());
                return 3;
            } catch (GitNotesException e) {
                err.println("Git notes error: " + e.getMessage());
                return 3;
            }
        }

        private String getPhaseSymbol(Phase phase) {
            return switch (phase) {
                case PLAN -> "📋";
                case RED -> "🔴";
                case GREEN -> "🟢";
                case REFACTOR -> "🔧";
                case COMPLETE -> "✅";
            };
        }

        private String truncate(String text, int maxLength) {
            if (text == null) return "";
            if (text.length() <= maxLength) return text;
            return text.substring(0, maxLength - 3) + "...";
        }
    }

    // ========== ROLLBACK COMMAND ==========

    @Command(name = "rollback", description = "Rollback to a specific commit",
            mixinStandardHelpOptions = true)
    static class RollbackCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "The commit ID to rollback to")
        String commitId;

        @Option(names = {"-p", "--project"}, description = "Project root directory", defaultValue = ".")
        Path projectRoot;

        @Option(names = {"--force"}, description = "Force rollback without confirmation", defaultValue = "false")
        boolean force;

        @Override
        public Integer call() {
            PrintWriter out = new PrintWriter(System.out, true);
            PrintWriter err = new PrintWriter(System.err, true);

            try {
                TddConfiguration config = TddConfiguration.load(projectRoot);

                try (GitNotesManager notesManager = new GitNotesManager(config.getProjectRoot());
                     GitOperations gitOps = new GitOperations(config.getProjectRoot())) {

                    // Resolve commit ID
                    ObjectId targetCommit = gitOps.getRepository().resolve(commitId);
                    if (targetCommit == null) {
                        err.println("Unknown commit: " + commitId);
                        return 1;
                    }

                    // Check if commit has a handoff note
                    Optional<HandoffState> handoffState = notesManager.readHandoff(targetCommit);

                    out.println("🔙 Rollback to commit " + targetCommit.abbreviate(7).name());

                    if (handoffState.isPresent()) {
                        HandoffState state = handoffState.get();
                        out.println("Phase: " + state.phase());
                        out.println("Cycle: " + state.cycleNumber());
                        if (state.currentTest() != null) {
                            out.println("Test: " + state.currentTest().description());
                        }
                    } else {
                        out.println("⚠️  Warning: This commit has no handoff state.");
                    }

                    if (!force) {
                        out.println();
                        out.println("⚠️  This will discard all changes after this commit!");
                        out.println("Use --force to confirm rollback.");
                        return 1;
                    }

                    // Perform rollback
                    out.println();
                    out.println("Rolling back...");
                    gitOps.rollbackToCommit(targetCommit);

                    out.println("✅ Rollback complete.");
                    out.println("Use 'tdd-orchestrator resume' to continue from this state.");
                }

                return 0;

            } catch (ConfigurationException e) {
                err.println("Configuration error: " + e.getMessage());
                return 2;
            } catch (IOException e) {
                err.println("IO error: " + e.getMessage());
                return 3;
            } catch (Exception e) {
                err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }
}
