package com.redgreenrefactor.agent;

import com.redgreenrefactor.model.AgentConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAgentTest {

    @Test
    void createConfig_returnsValidConfiguration() {
        AgentConfig config = TestAgent.createConfig();

        assertThat(config.name()).isEqualTo(TestAgent.NAME);
        assertThat(config.description()).isEqualTo(TestAgent.DESCRIPTION);
        assertThat(config.systemPrompt()).isNotBlank();
        assertThat(config.tools()).hasSize(6);
        assertThat(config.model()).isEqualTo(AgentConfig.DEFAULT_MODEL);
    }

    @Test
    void systemPrompt_containsKeyInstructions() {
        AgentConfig config = TestAgent.createConfig();
        String prompt = config.systemPrompt();

        // Verify key RED phase instructions
        assertThat(prompt).contains("RED");
        assertThat(prompt).contains("failing");
        assertThat(prompt).contains("FAIL");
        assertThat(prompt).contains("test:");
        assertThat(prompt).contains("git");
        assertThat(prompt).contains("mvn test").as("Should mention Maven test command");
    }

    @Test
    void systemPrompt_emphasizesTestMustFail() {
        AgentConfig config = TestAgent.createConfig();
        String prompt = config.systemPrompt();

        // The test MUST fail - this is critical for TDD
        assertThat(prompt.toLowerCase()).contains("must fail");
    }

    @Test
    void config_hasAllRequiredTools() {
        AgentConfig config = TestAgent.createConfig();

        var toolNames = config.tools().stream()
                .map(tool -> tool.name())
                .toList();

        assertThat(toolNames).containsExactlyInAnyOrder(
                "Read", "Write", "Edit", "Bash", "Glob", "Grep"
        );
    }
}
