package com.redgreenrefactor;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.*;

class TddOrchestratorCLITest {

    @Test
    void cli_showsUsageWithNoArgs() {
        // When no subcommand is provided, the CLI shows usage via System.out
        // We just verify it doesn't fail and returns 0
        CommandLine cmd = new CommandLine(new TddOrchestratorCLI());
        int exitCode = cmd.execute();
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void cli_hasRunSubcommand() {
        CommandLine cmd = new CommandLine(new TddOrchestratorCLI());
        assertThat(cmd.getSubcommands()).containsKey("run");
    }

    @Test
    void cli_hasResumeSubcommand() {
        CommandLine cmd = new CommandLine(new TddOrchestratorCLI());
        assertThat(cmd.getSubcommands()).containsKey("resume");
    }

    @Test
    void cli_hasStatusSubcommand() {
        CommandLine cmd = new CommandLine(new TddOrchestratorCLI());
        assertThat(cmd.getSubcommands()).containsKey("status");
    }

    @Test
    void cli_hasHistorySubcommand() {
        CommandLine cmd = new CommandLine(new TddOrchestratorCLI());
        assertThat(cmd.getSubcommands()).containsKey("history");
    }

    @Test
    void cli_hasRollbackSubcommand() {
        CommandLine cmd = new CommandLine(new TddOrchestratorCLI());
        assertThat(cmd.getSubcommands()).containsKey("rollback");
    }

    @Test
    void cli_showsVersion() {
        StringWriter sw = new StringWriter();
        CommandLine cmd = new CommandLine(new TddOrchestratorCLI());
        cmd.setOut(new PrintWriter(sw));

        int exitCode = cmd.execute("--version");

        assertThat(exitCode).isEqualTo(0);
        assertThat(sw.toString()).contains("1.0.0");
    }

    @Test
    void cli_showsHelp() {
        StringWriter sw = new StringWriter();
        CommandLine cmd = new CommandLine(new TddOrchestratorCLI());
        cmd.setOut(new PrintWriter(sw));

        int exitCode = cmd.execute("--help");

        assertThat(exitCode).isEqualTo(0);
        String output = sw.toString();
        assertThat(output).contains("Multi-agent TDD orchestrator");
        assertThat(output).contains("run");
        assertThat(output).contains("resume");
        assertThat(output).contains("status");
        assertThat(output).contains("history");
        assertThat(output).contains("rollback");
    }

    @Test
    void runCommand_requiresFeatureRequest() {
        StringWriter sw = new StringWriter();
        StringWriter errSw = new StringWriter();
        CommandLine cmd = new CommandLine(new TddOrchestratorCLI());
        cmd.setOut(new PrintWriter(sw));
        cmd.setErr(new PrintWriter(errSw));

        int exitCode = cmd.execute("run");

        assertThat(exitCode).isNotEqualTo(0);
        assertThat(errSw.toString()).contains("Missing required parameter");
    }

    @Test
    void rollbackCommand_requiresCommitId() {
        StringWriter sw = new StringWriter();
        StringWriter errSw = new StringWriter();
        CommandLine cmd = new CommandLine(new TddOrchestratorCLI());
        cmd.setOut(new PrintWriter(sw));
        cmd.setErr(new PrintWriter(errSw));

        int exitCode = cmd.execute("rollback");

        assertThat(exitCode).isNotEqualTo(0);
        assertThat(errSw.toString()).contains("Missing required parameter");
    }

    @Test
    void historyCommand_hasLimitOption() {
        CommandLine cmd = new CommandLine(new TddOrchestratorCLI());
        CommandLine historyCmd = cmd.getSubcommands().get("history");

        // Check that history command has --limit option
        assertThat(historyCmd.getCommandSpec().options().stream()
                .anyMatch(opt -> opt.names().length > 0 &&
                        (opt.names()[0].equals("-n") || opt.names()[0].equals("--limit")))).isTrue();
    }

    @Test
    void runCommand_hasProjectOption() {
        CommandLine cmd = new CommandLine(new TddOrchestratorCLI());
        CommandLine runCmd = cmd.getSubcommands().get("run");

        // Check that run command has --project option
        assertThat(runCmd.getCommandSpec().options().stream()
                .anyMatch(opt -> opt.names().length > 0 &&
                        (opt.names()[0].equals("-p") || opt.names()[0].equals("--project")))).isTrue();
    }

    @Test
    void rollbackCommand_hasForceOption() {
        CommandLine cmd = new CommandLine(new TddOrchestratorCLI());
        CommandLine rollbackCmd = cmd.getSubcommands().get("rollback");

        // Check that rollback command has --force option
        assertThat(rollbackCmd.getCommandSpec().options().stream()
                .anyMatch(opt -> opt.names().length > 0 &&
                        opt.names()[0].equals("--force"))).isTrue();
    }
}
