package com.redgreenrefactor.agent;

import com.redgreenrefactor.model.AgentConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefactorAgentTest {

    @Test
    void createConfig_returnsValidConfiguration() {
        AgentConfig config = RefactorAgent.createConfig();

        assertThat(config.name()).isEqualTo(RefactorAgent.NAME);
        assertThat(config.description()).isEqualTo(RefactorAgent.DESCRIPTION);
        assertThat(config.systemPrompt()).isNotBlank();
        assertThat(config.tools()).hasSize(6);
        assertThat(config.model()).isEqualTo(AgentConfig.DEFAULT_MODEL);
    }

    @Test
    void systemPrompt_containsKeyInstructions() {
        AgentConfig config = RefactorAgent.createConfig();
        String prompt = config.systemPrompt();

        // Verify key REFACTOR phase instructions
        assertThat(prompt).contains("REFACTOR");
        assertThat(prompt).contains("refactor:");
        assertThat(prompt).contains("git");
        assertThat(prompt).contains("--allow-empty");
    }

    @Test
    void systemPrompt_mentionsCodeQualityImprovements() {
        AgentConfig config = RefactorAgent.createConfig();
        String prompt = config.systemPrompt();

        // Should mention various refactoring concerns
        assertThat(prompt.toLowerCase()).contains("duplication");
        assertThat(prompt.toLowerCase()).contains("naming");
        assertThat(prompt.toLowerCase()).contains("readability");
    }

    @Test
    void systemPrompt_emphasizesTestsMustStillPass() {
        AgentConfig config = RefactorAgent.createConfig();
        String prompt = config.systemPrompt();

        // All tests must still pass
        assertThat(prompt.toLowerCase()).contains("tests");
        assertThat(prompt.toLowerCase()).contains("pass");
    }

    @Test
    void systemPrompt_mentionsTestListUpdate() {
        AgentConfig config = RefactorAgent.createConfig();
        String prompt = config.systemPrompt();

        // Should mark test as complete in test-list.md
        assertThat(prompt).contains("test-list.md");
        assertThat(prompt).contains("[x]");
    }

    @Test
    void config_hasAllRequiredTools() {
        AgentConfig config = RefactorAgent.createConfig();

        var toolNames = config.tools().stream()
                .map(tool -> tool.name())
                .toList();

        assertThat(toolNames).containsExactlyInAnyOrder(
                "Read", "Write", "Edit", "Bash", "Glob", "Grep"
        );
    }
}
