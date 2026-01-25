package com.redgreenrefactor.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Detects the test framework used in a project.
 * <p>
 * Detection priority (first match wins):
 * <ol>
 *   <li>Maven: pom.xml with JUnit → mvn test</li>
 *   <li>Gradle: build.gradle or build.gradle.kts → ./gradlew test (or gradlew.bat on Windows)</li>
 *   <li>npm: package.json with test script → npm test</li>
 *   <li>pytest: pytest.ini, pyproject.toml, or setup.py → pytest</li>
 * </ol>
 */
public class TestFrameworkDetector {
    private static final Logger LOG = LoggerFactory.getLogger(TestFrameworkDetector.class);

    private final Path projectRoot;

    /**
     * Creates a detector for the specified project root.
     *
     * @param projectRoot the root directory of the project
     */
    public TestFrameworkDetector(Path projectRoot) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot must not be null");
    }

    /**
     * Detects the test command for the project.
     *
     * @return the detected test command, or empty if no framework detected
     */
    public Optional<String> detectTestCommand() {
        LOG.debug("Detecting test framework in {}", projectRoot);

        // Priority 1: Maven
        if (isMavenProject()) {
            LOG.info("Detected Maven project");
            return Optional.of("mvn test");
        }

        // Priority 2: Gradle
        if (isGradleProject()) {
            LOG.info("Detected Gradle project");
            String gradleWrapper = isWindows() ? "gradlew.bat test" : "./gradlew test";
            if (hasGradleWrapper()) {
                return Optional.of(gradleWrapper);
            }
            return Optional.of("gradle test");
        }

        // Priority 3: npm/Node.js
        if (isNodeProject()) {
            LOG.info("Detected Node.js project with test script");
            return Optional.of("npm test");
        }

        // Priority 4: pytest
        if (isPytestProject()) {
            LOG.info("Detected pytest project");
            return Optional.of("pytest");
        }

        LOG.warn("No test framework detected in {}", projectRoot);
        return Optional.empty();
    }

    /**
     * Gets the detected framework type.
     *
     * @return the framework type, or empty if not detected
     */
    public Optional<TestFramework> detectFramework() {
        if (isMavenProject()) {
            return Optional.of(TestFramework.MAVEN);
        }
        if (isGradleProject()) {
            return Optional.of(TestFramework.GRADLE);
        }
        if (isNodeProject()) {
            return Optional.of(TestFramework.NPM);
        }
        if (isPytestProject()) {
            return Optional.of(TestFramework.PYTEST);
        }
        return Optional.empty();
    }

    private boolean isMavenProject() {
        Path pomXml = projectRoot.resolve("pom.xml");
        if (!Files.exists(pomXml)) {
            return false;
        }
        // Check if pom.xml contains JUnit (basic check)
        try {
            String content = Files.readString(pomXml);
            return content.contains("junit");
        } catch (IOException e) {
            LOG.debug("Failed to read pom.xml: {}", e.getMessage());
            // If we can't read it, assume it's a valid Maven project
            return true;
        }
    }

    private boolean isGradleProject() {
        return Files.exists(projectRoot.resolve("build.gradle"))
                || Files.exists(projectRoot.resolve("build.gradle.kts"));
    }

    private boolean hasGradleWrapper() {
        if (isWindows()) {
            return Files.exists(projectRoot.resolve("gradlew.bat"));
        }
        return Files.exists(projectRoot.resolve("gradlew"));
    }

    private boolean isNodeProject() {
        Path packageJson = projectRoot.resolve("package.json");
        if (!Files.exists(packageJson)) {
            return false;
        }
        // Check if package.json has a test script
        try {
            String content = Files.readString(packageJson);
            return content.contains("\"test\"");
        } catch (IOException e) {
            LOG.debug("Failed to read package.json: {}", e.getMessage());
            return false;
        }
    }

    private boolean isPytestProject() {
        return Files.exists(projectRoot.resolve("pytest.ini"))
                || hasPytestInPyproject()
                || Files.exists(projectRoot.resolve("setup.py"));
    }

    private boolean hasPytestInPyproject() {
        Path pyproject = projectRoot.resolve("pyproject.toml");
        if (!Files.exists(pyproject)) {
            return false;
        }
        try {
            String content = Files.readString(pyproject);
            return content.contains("[tool.pytest") || content.contains("pytest");
        } catch (IOException e) {
            LOG.debug("Failed to read pyproject.toml: {}", e.getMessage());
            return false;
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    /**
     * Supported test frameworks.
     */
    public enum TestFramework {
        MAVEN("Maven", "mvn test"),
        GRADLE("Gradle", "./gradlew test"),
        NPM("npm", "npm test"),
        PYTEST("pytest", "pytest");

        private final String displayName;
        private final String defaultCommand;

        TestFramework(String displayName, String defaultCommand) {
            this.displayName = displayName;
            this.defaultCommand = defaultCommand;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDefaultCommand() {
            return defaultCommand;
        }
    }
}
