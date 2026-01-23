package com.redgreenrefactor.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestCaseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldSerializeToJson() throws Exception {
        TestCase testCase = new TestCase(
                "should calculate sum of two numbers",
                "src/test/java/com/example/CalculatorTest.java",
                "src/main/java/com/example/Calculator.java"
        );

        String json = objectMapper.writeValueAsString(testCase);

        assertThat(json).contains("\"description\":\"should calculate sum of two numbers\"");
        assertThat(json).contains("\"testFile\":\"src/test/java/com/example/CalculatorTest.java\"");
        assertThat(json).contains("\"implFile\":\"src/main/java/com/example/Calculator.java\"");
    }

    @Test
    void shouldDeserializeFromJson() throws Exception {
        String json = """
                {
                    "description": "should handle negative numbers",
                    "testFile": "tests/NegativeTest.java",
                    "implFile": "src/Numbers.java"
                }
                """;

        TestCase testCase = objectMapper.readValue(json, TestCase.class);

        assertThat(testCase.description()).isEqualTo("should handle negative numbers");
        assertThat(testCase.testFile()).isEqualTo("tests/NegativeTest.java");
        assertThat(testCase.implFile()).isEqualTo("src/Numbers.java");
    }

    @Test
    void shouldThrowOnNullDescription() {
        assertThatThrownBy(() -> new TestCase(null, "test.java", "impl.java"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void shouldThrowOnBlankDescription() {
        assertThatThrownBy(() -> new TestCase("   ", "test.java", "impl.java"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void shouldThrowOnNullTestFile() {
        assertThatThrownBy(() -> new TestCase("desc", null, "impl.java"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("testFile");
    }

    @Test
    void shouldThrowOnNullImplFile() {
        assertThatThrownBy(() -> new TestCase("desc", "test.java", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("implFile");
    }

    @Test
    void shouldHaveProperEqualsHashCodeToString() {
        TestCase tc1 = new TestCase("desc", "test.java", "impl.java");
        TestCase tc2 = new TestCase("desc", "test.java", "impl.java");
        TestCase tc3 = new TestCase("different", "test.java", "impl.java");

        assertThat(tc1).isEqualTo(tc2);
        assertThat(tc1.hashCode()).isEqualTo(tc2.hashCode());
        assertThat(tc1).isNotEqualTo(tc3);
        assertThat(tc1.toString()).contains("desc", "test.java", "impl.java");
    }
}
