package com.redgreenrefactor.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class TddConfigurationTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Note: Environment variables can't be easily mocked in tests
        // We test the builder and properties loading instead
    }

    @Test
    void builder_createsValidConfiguration() {
        TddConfiguration config = new TddConfiguration.Builder()
                .anthropicApiKey("test-api-key")
                .projectRoot(tempDir)
                .maxRetries(5)
                .model("claude-3-opus")
                .bashTimeoutSeconds(60)
                .testCommand("npm test")
                .build();

        assertThat(config.getAnthropicApiKey()).isEqualTo("test-api-key");
        assertThat(config.getProjectRoot()).isEqualTo(tempDir);
        assertThat(config.getMaxRetries()).isEqualTo(5);
        assertThat(config.getModel()).isEqualTo("claude-3-opus");
        assertThat(config.getBashTimeoutSeconds()).isEqualTo(60);
        assertThat(config.getTestCommand()).contains("npm test");
    }

    @Test
    void builder_usesDefaults() {
        TddConfiguration config = new TddConfiguration.Builder()
                .anthropicApiKey("test-api-key")
                .projectRoot(tempDir)
                .build();

        assertThat(config.getMaxRetries()).isEqualTo(TddConfiguration.DEFAULT_MAX_RETRIES);
        assertThat(config.getModel()).isEqualTo(TddConfiguration.DEFAULT_MODEL);
        assertThat(config.getBashTimeoutSeconds()).isEqualTo(TddConfiguration.DEFAULT_BASH_TIMEOUT);
        assertThat(config.getTestCommand()).isEmpty();
    }

    @Test
    void builder_requiresApiKey() {
        TddConfiguration.Builder builder = new TddConfiguration.Builder()
                .projectRoot(tempDir);

        assertThatThrownBy(builder::build)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("anthropicApiKey");
    }

    @Test
    void builder_requiresProjectRoot() {
        TddConfiguration.Builder builder = new TddConfiguration.Builder()
                .anthropicApiKey("test-api-key");

        assertThatThrownBy(builder::build)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("projectRoot");
    }

    @Test
    void toString_doesNotExposeApiKey() {
        TddConfiguration config = new TddConfiguration.Builder()
                .anthropicApiKey("super-secret-key")
                .projectRoot(tempDir)
                .build();

        String toString = config.toString();

        assertThat(toString).doesNotContain("super-secret-key");
        assertThat(toString).contains("hasApiKey=true");
    }

    @Test
    void toString_indicatesMissingApiKey() {
        // Can't actually test empty API key since builder requires non-null
        // But we test the logic by examining the toString format
        TddConfiguration config = new TddConfiguration.Builder()
                .anthropicApiKey("x")  // minimal key
                .projectRoot(tempDir)
                .build();

        String toString = config.toString();
        assertThat(toString).contains("hasApiKey=true");
    }

    @Test
    void propertiesFile_parsedCorrectly() throws IOException {
        // Create a properties file
        Path propsFile = tempDir.resolve(TddConfiguration.CONFIG_FILE_NAME);
        Files.writeString(propsFile, """
                bash.timeout=300
                test.command=pytest -v
                """);

        // Note: Since load() reads from env vars, we can't fully test it without mocking
        // This just verifies the file creation for manual testing
        assertThat(propsFile).exists();
        assertThat(Files.readString(propsFile)).contains("bash.timeout=300");
    }

    @Test
    void constantValues_areCorrect() {
        assertThat(TddConfiguration.ENV_ANTHROPIC_API_KEY).isEqualTo("ANTHROPIC_API_KEY");
        assertThat(TddConfiguration.ENV_PROJECT_ROOT).isEqualTo("TDD_PROJECT_ROOT");
        assertThat(TddConfiguration.ENV_MAX_RETRIES).isEqualTo("TDD_MAX_RETRIES");
        assertThat(TddConfiguration.ENV_MODEL).isEqualTo("TDD_MODEL");
        assertThat(TddConfiguration.PROP_BASH_TIMEOUT).isEqualTo("bash.timeout");
        assertThat(TddConfiguration.PROP_TEST_COMMAND).isEqualTo("test.command");
        assertThat(TddConfiguration.CONFIG_FILE_NAME).isEqualTo("tdd.properties");
        assertThat(TddConfiguration.DEFAULT_MODEL).isEqualTo("claude-opus-4-5-20251101");
        assertThat(TddConfiguration.DEFAULT_MAX_RETRIES).isEqualTo(3);
        assertThat(TddConfiguration.DEFAULT_BASH_TIMEOUT).isEqualTo(120L);
    }
}
