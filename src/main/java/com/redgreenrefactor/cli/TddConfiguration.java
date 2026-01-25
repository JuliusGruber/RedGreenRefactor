package com.redgreenrefactor.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Configuration management for the TDD Orchestrator.
 * <p>
 * Loads configuration from environment variables and tdd.properties file.
 * Environment variables take precedence over properties file values.
 */
public class TddConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(TddConfiguration.class);

    // Environment variable names
    public static final String ENV_ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";
    public static final String ENV_PROJECT_ROOT = "TDD_PROJECT_ROOT";
    public static final String ENV_MAX_RETRIES = "TDD_MAX_RETRIES";
    public static final String ENV_MODEL = "TDD_MODEL";

    // Properties file keys
    public static final String PROP_BASH_TIMEOUT = "bash.timeout";
    public static final String PROP_TEST_COMMAND = "test.command";

    // Default values
    public static final String DEFAULT_MODEL = "claude-opus-4-5-20251101";
    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final long DEFAULT_BASH_TIMEOUT = 120L;
    public static final String CONFIG_FILE_NAME = "tdd.properties";

    private final String anthropicApiKey;
    private final Path projectRoot;
    private final int maxRetries;
    private final String model;
    private final long bashTimeoutSeconds;
    private final String testCommand;

    private TddConfiguration(Builder builder) {
        this.anthropicApiKey = builder.anthropicApiKey;
        this.projectRoot = builder.projectRoot;
        this.maxRetries = builder.maxRetries;
        this.model = builder.model;
        this.bashTimeoutSeconds = builder.bashTimeoutSeconds;
        this.testCommand = builder.testCommand;
    }

    /**
     * Loads configuration from environment variables and properties file.
     *
     * @param projectPath the project directory (or null to use current directory)
     * @return the loaded configuration
     * @throws ConfigurationException if required configuration is missing
     */
    public static TddConfiguration load(Path projectPath) throws ConfigurationException {
        Path projectRoot = resolveProjectRoot(projectPath);
        Properties properties = loadPropertiesFile(projectRoot);

        String apiKey = getEnv(ENV_ANTHROPIC_API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            throw new ConfigurationException(
                    "ANTHROPIC_API_KEY environment variable is required. " +
                    "Set it with: export ANTHROPIC_API_KEY=your-api-key");
        }

        return new Builder()
                .anthropicApiKey(apiKey)
                .projectRoot(projectRoot)
                .maxRetries(parseMaxRetries())
                .model(parseModel())
                .bashTimeoutSeconds(parseBashTimeout(properties))
                .testCommand(parseTestCommand(properties))
                .build();
    }

    private static Path resolveProjectRoot(Path projectPath) {
        if (projectPath != null) {
            return projectPath.toAbsolutePath().normalize();
        }
        String envRoot = getEnv(ENV_PROJECT_ROOT);
        if (envRoot != null && !envRoot.isBlank()) {
            return Path.of(envRoot).toAbsolutePath().normalize();
        }
        return Path.of("").toAbsolutePath();
    }

    private static Properties loadPropertiesFile(Path projectRoot) {
        Properties properties = new Properties();
        Path configFile = projectRoot.resolve(CONFIG_FILE_NAME);

        if (Files.exists(configFile)) {
            try (InputStream is = Files.newInputStream(configFile)) {
                properties.load(is);
                LOG.debug("Loaded configuration from {}", configFile);
            } catch (IOException e) {
                LOG.warn("Failed to load {}: {}", configFile, e.getMessage());
            }
        } else {
            LOG.debug("No {} file found in {}", CONFIG_FILE_NAME, projectRoot);
        }

        return properties;
    }

    private static int parseMaxRetries() {
        String value = getEnv(ENV_MAX_RETRIES);
        if (value != null && !value.isBlank()) {
            try {
                int retries = Integer.parseInt(value.trim());
                if (retries < 0) {
                    LOG.warn("Invalid TDD_MAX_RETRIES value '{}', using default: {}",
                            value, DEFAULT_MAX_RETRIES);
                    return DEFAULT_MAX_RETRIES;
                }
                return retries;
            } catch (NumberFormatException e) {
                LOG.warn("Invalid TDD_MAX_RETRIES value '{}', using default: {}",
                        value, DEFAULT_MAX_RETRIES);
            }
        }
        return DEFAULT_MAX_RETRIES;
    }

    private static String parseModel() {
        String value = getEnv(ENV_MODEL);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return DEFAULT_MODEL;
    }

    private static long parseBashTimeout(Properties properties) {
        String value = properties.getProperty(PROP_BASH_TIMEOUT);
        if (value != null && !value.isBlank()) {
            try {
                long timeout = Long.parseLong(value.trim());
                if (timeout <= 0) {
                    LOG.warn("Invalid bash.timeout value '{}', using default: {}",
                            value, DEFAULT_BASH_TIMEOUT);
                    return DEFAULT_BASH_TIMEOUT;
                }
                return timeout;
            } catch (NumberFormatException e) {
                LOG.warn("Invalid bash.timeout value '{}', using default: {}",
                        value, DEFAULT_BASH_TIMEOUT);
            }
        }
        return DEFAULT_BASH_TIMEOUT;
    }

    private static String parseTestCommand(Properties properties) {
        return properties.getProperty(PROP_TEST_COMMAND);
    }

    private static String getEnv(String name) {
        return System.getenv(name);
    }

    // Getters

    public String getAnthropicApiKey() {
        return anthropicApiKey;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public String getModel() {
        return model;
    }

    public long getBashTimeoutSeconds() {
        return bashTimeoutSeconds;
    }

    public Optional<String> getTestCommand() {
        return Optional.ofNullable(testCommand);
    }

    @Override
    public String toString() {
        return "TddConfiguration{" +
                "projectRoot=" + projectRoot +
                ", maxRetries=" + maxRetries +
                ", model='" + model + '\'' +
                ", bashTimeoutSeconds=" + bashTimeoutSeconds +
                ", testCommand='" + testCommand + '\'' +
                ", hasApiKey=" + (anthropicApiKey != null && !anthropicApiKey.isBlank()) +
                '}';
    }

    /**
     * Builder for TddConfiguration.
     */
    public static class Builder {
        private String anthropicApiKey;
        private Path projectRoot;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private String model = DEFAULT_MODEL;
        private long bashTimeoutSeconds = DEFAULT_BASH_TIMEOUT;
        private String testCommand;

        public Builder anthropicApiKey(String apiKey) {
            this.anthropicApiKey = apiKey;
            return this;
        }

        public Builder projectRoot(Path projectRoot) {
            this.projectRoot = projectRoot;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder bashTimeoutSeconds(long bashTimeoutSeconds) {
            this.bashTimeoutSeconds = bashTimeoutSeconds;
            return this;
        }

        public Builder testCommand(String testCommand) {
            this.testCommand = testCommand;
            return this;
        }

        public TddConfiguration build() {
            Objects.requireNonNull(anthropicApiKey, "anthropicApiKey is required");
            Objects.requireNonNull(projectRoot, "projectRoot is required");
            return new TddConfiguration(this);
        }
    }
}
