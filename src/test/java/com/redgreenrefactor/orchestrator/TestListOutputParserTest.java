package com.redgreenrefactor.orchestrator;

import com.redgreenrefactor.model.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestListOutputParserTest {

    private TestListOutputParser parser;

    @BeforeEach
    void setUp() {
        parser = new TestListOutputParser();
    }

    @Test
    void parseTestSelection_extractsFromJsonCodeBlock() {
        String response = """
                I've analyzed the requirements and created the test list.

                ```json
                {
                  "currentTest": {
                    "description": "should add two numbers",
                    "testFile": "src/test/java/CalculatorTest.java",
                    "implFile": "src/main/java/Calculator.java"
                  }
                }
                ```

                I've committed the plan.
                """;

        Optional<TestCase> result = parser.parseTestSelection(response);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isEqualTo("should add two numbers");
        assertThat(result.get().testFile()).isEqualTo("src/test/java/CalculatorTest.java");
        assertThat(result.get().implFile()).isEqualTo("src/main/java/Calculator.java");
    }

    @Test
    void parseTestSelection_extractsFromPlainCodeBlock() {
        String response = """
                Here's the selected test:

                ```
                {
                  "currentTest": {
                    "description": "should handle empty input",
                    "testFile": "src/test/java/ParserTest.java",
                    "implFile": "src/main/java/Parser.java"
                  }
                }
                ```
                """;

        Optional<TestCase> result = parser.parseTestSelection(response);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isEqualTo("should handle empty input");
    }

    @Test
    void parseTestSelection_extractsInlineJson() {
        String response = """
                The next test is: {"currentTest": {"description": "test login", "testFile": "LoginTest.java", "implFile": "Login.java"}}
                """;

        Optional<TestCase> result = parser.parseTestSelection(response);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isEqualTo("test login");
    }

    @Test
    void parseTestSelection_returnsEmpty_whenCurrentTestIsNull() {
        String response = """
                All tests are complete!

                ```json
                {"currentTest": null}
                ```
                """;

        Optional<TestCase> result = parser.parseTestSelection(response);

        assertThat(result).isEmpty();
    }

    @Test
    void parseTestSelection_handlesSpacesInJson() {
        String response = """
                {
                    "currentTest"  :  {
                        "description"  :  "test with spaces",
                        "testFile"  :  "Test.java",
                        "implFile"  :  "Impl.java"
                    }
                }
                """;

        Optional<TestCase> result = parser.parseTestSelection(response);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isEqualTo("test with spaces");
    }

    @Test
    void parseTestSelection_throwsOnMissingJson() {
        String response = "No JSON here, just some text.";

        assertThatThrownBy(() -> parser.parseTestSelection(response))
                .isInstanceOf(OrchestratorException.class)
                .hasMessageContaining("Could not find JSON");
    }

    @Test
    void parseTestSelection_throwsOnMissingCurrentTestField() {
        String response = """
                ```json
                {"otherField": "value"}
                ```
                """;

        assertThatThrownBy(() -> parser.parseTestSelection(response))
                .isInstanceOf(OrchestratorException.class)
                .hasMessageContaining("Could not find JSON");
    }

    @Test
    void parseTestSelection_throwsOnMissingDescription() {
        String response = """
                ```json
                {
                  "currentTest": {
                    "testFile": "Test.java",
                    "implFile": "Impl.java"
                  }
                }
                ```
                """;

        assertThatThrownBy(() -> parser.parseTestSelection(response))
                .isInstanceOf(OrchestratorException.class)
                .hasMessageContaining("description");
    }

    @Test
    void parseTestSelection_throwsOnMissingTestFile() {
        String response = """
                ```json
                {
                  "currentTest": {
                    "description": "test",
                    "implFile": "Impl.java"
                  }
                }
                ```
                """;

        assertThatThrownBy(() -> parser.parseTestSelection(response))
                .isInstanceOf(OrchestratorException.class)
                .hasMessageContaining("testFile");
    }

    @Test
    void parseTestSelection_throwsOnMissingImplFile() {
        String response = """
                ```json
                {
                  "currentTest": {
                    "description": "test",
                    "testFile": "Test.java"
                  }
                }
                ```
                """;

        assertThatThrownBy(() -> parser.parseTestSelection(response))
                .isInstanceOf(OrchestratorException.class)
                .hasMessageContaining("implFile");
    }

    @Test
    void parseTestSelection_throwsOnBlankDescription() {
        String response = """
                ```json
                {
                  "currentTest": {
                    "description": "   ",
                    "testFile": "Test.java",
                    "implFile": "Impl.java"
                  }
                }
                ```
                """;

        assertThatThrownBy(() -> parser.parseTestSelection(response))
                .isInstanceOf(OrchestratorException.class)
                .hasMessageContaining("description");
    }

    @Test
    void parseTestSelection_throwsOnNullResponse() {
        assertThatThrownBy(() -> parser.parseTestSelection(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("responseText");
    }

    @Test
    void parseTestSelection_handlesMultipleCodeBlocks_selectsOneWithCurrentTest() {
        String response = """
                Here's some other JSON:
                ```json
                {"status": "ok"}
                ```

                And here's the test selection:
                ```json
                {"currentTest": {"description": "the right one", "testFile": "T.java", "implFile": "I.java"}}
                ```
                """;

        Optional<TestCase> result = parser.parseTestSelection(response);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isEqualTo("the right one");
    }

    @Test
    void parseTestSelection_handlesJsonWithExtraFields() {
        String response = """
                ```json
                {
                  "currentTest": {
                    "description": "test with extras",
                    "testFile": "Test.java",
                    "implFile": "Impl.java",
                    "extraField": "ignored"
                  },
                  "metadata": "also ignored"
                }
                ```
                """;

        Optional<TestCase> result = parser.parseTestSelection(response);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isEqualTo("test with extras");
    }
}
