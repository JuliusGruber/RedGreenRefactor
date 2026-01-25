package com.redgreenrefactor.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class TestFrameworkDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsMavenProject() throws IOException {
        // Create pom.xml with JUnit
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);

        TestFrameworkDetector detector = new TestFrameworkDetector(tempDir);
        Optional<String> testCommand = detector.detectTestCommand();

        assertThat(testCommand).contains("mvn test");
        assertThat(detector.detectFramework()).contains(TestFrameworkDetector.TestFramework.MAVEN);
    }

    @Test
    void detectsMavenProject_evenWithoutJunitInContent() throws IOException {
        // pom.xml exists but we can't read it (simulated by empty file)
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

        TestFrameworkDetector detector = new TestFrameworkDetector(tempDir);

        // Should NOT detect as Maven without junit reference
        assertThat(detector.detectTestCommand()).isEmpty();
    }

    @Test
    void detectsGradleProject_withWrapper() throws IOException {
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }");
        // Create wrapper for both platforms
        Files.writeString(tempDir.resolve("gradlew"), "#!/bin/bash\n./gradle \"$@\"");
        Files.writeString(tempDir.resolve("gradlew.bat"), "@echo off\ngradle %*");

        TestFrameworkDetector detector = new TestFrameworkDetector(tempDir);
        Optional<String> testCommand = detector.detectTestCommand();

        assertThat(testCommand).isPresent();
        // On Windows it uses gradlew.bat, on Unix it uses ./gradlew
        assertThat(testCommand.get()).containsIgnoringCase("gradlew");
        assertThat(testCommand.get()).containsIgnoringCase("test");
        assertThat(detector.detectFramework()).contains(TestFrameworkDetector.TestFramework.GRADLE);
    }

    @Test
    void detectsGradleKotlinProject() throws IOException {
        Files.writeString(tempDir.resolve("build.gradle.kts"), "plugins { kotlin(\"jvm\") }");

        TestFrameworkDetector detector = new TestFrameworkDetector(tempDir);
        Optional<String> testCommand = detector.detectTestCommand();

        assertThat(testCommand).isPresent();
        assertThat(testCommand.get()).contains("gradle test");
        assertThat(detector.detectFramework()).contains(TestFrameworkDetector.TestFramework.GRADLE);
    }

    @Test
    void detectsNodeProject_withTestScript() throws IOException {
        Files.writeString(tempDir.resolve("package.json"), """
                {
                    "name": "test-project",
                    "scripts": {
                        "test": "jest"
                    }
                }
                """);

        TestFrameworkDetector detector = new TestFrameworkDetector(tempDir);
        Optional<String> testCommand = detector.detectTestCommand();

        assertThat(testCommand).contains("npm test");
        assertThat(detector.detectFramework()).contains(TestFrameworkDetector.TestFramework.NPM);
    }

    @Test
    void doesNotDetectNodeProject_withoutTestScript() throws IOException {
        Files.writeString(tempDir.resolve("package.json"), """
                {
                    "name": "test-project",
                    "scripts": {
                        "start": "node index.js"
                    }
                }
                """);

        TestFrameworkDetector detector = new TestFrameworkDetector(tempDir);
        Optional<String> testCommand = detector.detectTestCommand();

        assertThat(testCommand).isEmpty();
    }

    @Test
    void detectsPytestProject_withPytestIni() throws IOException {
        Files.writeString(tempDir.resolve("pytest.ini"), "[pytest]\ntestpaths = tests");

        TestFrameworkDetector detector = new TestFrameworkDetector(tempDir);
        Optional<String> testCommand = detector.detectTestCommand();

        assertThat(testCommand).contains("pytest");
        assertThat(detector.detectFramework()).contains(TestFrameworkDetector.TestFramework.PYTEST);
    }

    @Test
    void detectsPytestProject_withPyproject() throws IOException {
        Files.writeString(tempDir.resolve("pyproject.toml"), """
                [tool.pytest.ini_options]
                testpaths = ["tests"]
                """);

        TestFrameworkDetector detector = new TestFrameworkDetector(tempDir);
        Optional<String> testCommand = detector.detectTestCommand();

        assertThat(testCommand).contains("pytest");
        assertThat(detector.detectFramework()).contains(TestFrameworkDetector.TestFramework.PYTEST);
    }

    @Test
    void detectsPytestProject_withSetupPy() throws IOException {
        Files.writeString(tempDir.resolve("setup.py"), "from setuptools import setup\nsetup()");

        TestFrameworkDetector detector = new TestFrameworkDetector(tempDir);
        Optional<String> testCommand = detector.detectTestCommand();

        assertThat(testCommand).contains("pytest");
        assertThat(detector.detectFramework()).contains(TestFrameworkDetector.TestFramework.PYTEST);
    }

    @Test
    void returnsEmpty_whenNoFrameworkDetected() {
        TestFrameworkDetector detector = new TestFrameworkDetector(tempDir);
        Optional<String> testCommand = detector.detectTestCommand();

        assertThat(testCommand).isEmpty();
        assertThat(detector.detectFramework()).isEmpty();
    }

    @Test
    void prioritizesMaven_overOtherFrameworks() throws IOException {
        // Create both Maven and Gradle files
        Files.writeString(tempDir.resolve("pom.xml"), "<project><dependencies><dependency>junit</dependency></dependencies></project>");
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }");
        Files.writeString(tempDir.resolve("package.json"), "{\"scripts\":{\"test\":\"jest\"}}");

        TestFrameworkDetector detector = new TestFrameworkDetector(tempDir);
        Optional<String> testCommand = detector.detectTestCommand();

        // Maven should win due to priority
        assertThat(testCommand).contains("mvn test");
    }

    @Test
    void prioritizesGradle_overNpmAndPytest() throws IOException {
        // Create Gradle, npm, and pytest files (no Maven)
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }");
        Files.writeString(tempDir.resolve("package.json"), "{\"scripts\":{\"test\":\"jest\"}}");
        Files.writeString(tempDir.resolve("pytest.ini"), "[pytest]");

        TestFrameworkDetector detector = new TestFrameworkDetector(tempDir);
        Optional<String> testCommand = detector.detectTestCommand();

        // Gradle should win due to priority
        assertThat(testCommand.get()).contains("gradle test");
    }

    @Test
    void testFrameworkEnum_hasCorrectValues() {
        assertThat(TestFrameworkDetector.TestFramework.MAVEN.getDisplayName()).isEqualTo("Maven");
        assertThat(TestFrameworkDetector.TestFramework.MAVEN.getDefaultCommand()).isEqualTo("mvn test");

        assertThat(TestFrameworkDetector.TestFramework.GRADLE.getDisplayName()).isEqualTo("Gradle");
        assertThat(TestFrameworkDetector.TestFramework.GRADLE.getDefaultCommand()).isEqualTo("./gradlew test");

        assertThat(TestFrameworkDetector.TestFramework.NPM.getDisplayName()).isEqualTo("npm");
        assertThat(TestFrameworkDetector.TestFramework.NPM.getDefaultCommand()).isEqualTo("npm test");

        assertThat(TestFrameworkDetector.TestFramework.PYTEST.getDisplayName()).isEqualTo("pytest");
        assertThat(TestFrameworkDetector.TestFramework.PYTEST.getDefaultCommand()).isEqualTo("pytest");
    }

    @Test
    void constructor_requiresNonNullPath() {
        assertThatThrownBy(() -> new TestFrameworkDetector(null))
                .isInstanceOf(NullPointerException.class);
    }
}
