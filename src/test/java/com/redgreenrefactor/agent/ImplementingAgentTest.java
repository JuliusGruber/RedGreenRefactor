package com.redgreenrefactor.agent;

import com.redgreenrefactor.model.AgentConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImplementingAgentTest {

    @Test
    void createConfig_returnsValidConfiguration() {
        AgentConfig config = ImplementingAgent.createConfig();

        assertThat(config.name()).isEqualTo(ImplementingAgent.NAME);
        assertThat(config.description()).isEqualTo(ImplementingAgent.DESCRIPTION);
        assertThat(config.systemPrompt()).isNotBlank();
        assertThat(config.tools()).hasSize(6);
        assertThat(config.model()).isEqualTo(AgentConfig.DEFAULT_MODEL);
    }

    @Test
    void systemPrompt_containsKeyInstructions() {
        AgentConfig config = ImplementingAgent.createConfig();
        String prompt = config.systemPrompt();

        // Verify key GREEN phase instructions
        assertThat(prompt).contains("GREEN");
        assertThat(prompt).contains("MINIMUM");
        assertThat(prompt).contains("pass");
        assertThat(prompt).contains("feat:");
        assertThat(prompt).contains("git");
    }

    @Test
    void systemPrompt_emphasizesMinimumCode() {
        AgentConfig config = ImplementingAgent.createConfig();
        String prompt = config.systemPrompt();

        // Should emphasize minimal implementation
        assertThat(prompt.toLowerCase()).contains("minimum");
        assertThat(prompt.toLowerCase()).contains("simplest");
    }

    @Test
    void systemPrompt_mentionsAllTestsMustPass() {
        AgentConfig config = ImplementingAgent.createConfig();
        String prompt = config.systemPrompt();

        // All tests must pass
        assertThat(prompt.toLowerCase()).contains("all tests");
    }

    @Test
    void config_hasAllRequiredTools() {
        AgentConfig config = ImplementingAgent.createConfig();

        var toolNames = config.tools().stream()
                .map(tool -> tool.name())
                .toList();

        assertThat(toolNames).containsExactlyInAnyOrder(
                "Read", "Write", "Edit", "Bash", "Glob", "Grep"
        );
    }
}
