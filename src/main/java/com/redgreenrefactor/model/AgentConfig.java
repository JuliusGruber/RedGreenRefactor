package com.redgreenrefactor.model;

import com.anthropic.models.messages.Tool;

import java.util.List;

/**
 * Configuration for a TDD agent.
 *
 * @param name         The agent's identifier (e.g., "TestListAgent", "TestAgent")
 * @param description  A brief description of the agent's role
 * @param systemPrompt The system prompt that defines the agent's behavior
 * @param tools        The tools available to this agent
 * @param model        The Claude model to use for this agent
 */
public record AgentConfig(
        String name,
        String description,
        String systemPrompt,
        List<Tool> tools,
        String model
) {
    /**
     * Default model to use for agents.
     */
    public static final String DEFAULT_MODEL = "claude-opus-4-5-20251101";

    public AgentConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be null or blank");
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt cannot be null or blank");
        }
        tools = tools == null ? List.of() : List.copyOf(tools);
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }
    }

    /**
     * Creates a builder for constructing an AgentConfig.
     *
     * @return A new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating AgentConfig instances.
     */
    public static final class Builder {
        private String name;
        private String description;
        private String systemPrompt;
        private List<Tool> tools = List.of();
        private String model = DEFAULT_MODEL;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder tools(List<Tool> tools) {
            this.tools = tools == null ? List.of() : List.copyOf(tools);
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public AgentConfig build() {
            return new AgentConfig(name, description, systemPrompt, tools, model);
        }
    }
}
