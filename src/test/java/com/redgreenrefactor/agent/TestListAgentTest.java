package com.redgreenrefactor.agent;

import com.redgreenrefactor.model.AgentConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestListAgentTest {

    @Test
    void createConfig_returnsValidConfiguration() {
        AgentConfig config = TestListAgent.createConfig();

        assertThat(config.name()).isEqualTo(TestListAgent.NAME);
        assertThat(config.description()).isEqualTo(TestListAgent.DESCRIPTION);
        assertThat(config.systemPrompt()).isNotBlank();
        assertThat(config.tools()).hasSize(6);
        assertThat(config.model()).isEqualTo(AgentConfig.DEFAULT_MODEL);
    }

    @Test
    void systemPrompt_containsKeyInstructions() {
        AgentConfig config = TestListAgent.createConfig();
        String prompt = config.systemPrompt();

        // Verify key instructions are present
        assertThat(prompt).contains("test-list.md");
        assertThat(prompt).contains("currentTest");
        assertThat(prompt).contains("testFile");
        assertThat(prompt).contains("implFile");
        assertThat(prompt).contains("plan:");
        assertThat(prompt).contains("git");
    }

    @Test
    void config_hasAllRequiredTools() {
        AgentConfig config = TestListAgent.createConfig();

        var toolNames = config.tools().stream()
                .map(tool -> tool.name())
                .toList();

        assertThat(toolNames).containsExactlyInAnyOrder(
                "Read", "Write", "Edit", "Bash", "Glob", "Grep"
        );
    }
}
