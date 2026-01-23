package com.redgreenrefactor.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HandoffStateTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldSerializeToJson() throws Exception {
        TestCase testCase = new TestCase(
                "should add two numbers",
                "src/test/java/CalculatorTest.java",
                "src/main/java/Calculator.java"
        );

        HandoffState state = HandoffState.builder()
                .phase(Phase.RED)
                .nextPhase(Phase.GREEN)
                .cycleNumber(1)
                .currentTest(testCase)
                .completedTests(List.of("test1", "test2"))
                .pendingTests(List.of("test3"))
                .testResult(TestResult.FAIL)
                .retryCount(0)
                .build();

        String json = objectMapper.writeValueAsString(state);

        assertThat(json).contains("\"phase\":\"RED\"");
        assertThat(json).contains("\"nextPhase\":\"GREEN\"");
        assertThat(json).contains("\"cycleNumber\":1");
        assertThat(json).contains("\"description\":\"should add two numbers\"");
        assertThat(json).contains("\"testFile\":\"src/test/java/CalculatorTest.java\"");
        assertThat(json).contains("\"completedTests\":[\"test1\",\"test2\"]");
        assertThat(json).contains("\"pendingTests\":[\"test3\"]");
        assertThat(json).contains("\"testResult\":\"FAIL\"");
    }

    @Test
    void shouldDeserializeFromJson() throws Exception {
        String json = """
                {
                    "phase": "GREEN",
                    "nextPhase": "REFACTOR",
                    "cycleNumber": 2,
                    "currentTest": {
                        "description": "should subtract numbers",
                        "testFile": "src/test/java/CalcTest.java",
                        "implFile": "src/main/java/Calc.java"
                    },
                    "completedTests": ["test1"],
                    "pendingTests": ["test2", "test3"],
                    "testResult": "PASS",
                    "error": null,
                    "errorDetails": null,
                    "retryCount": 1
                }
                """;

        HandoffState state = objectMapper.readValue(json, HandoffState.class);

        assertThat(state.phase()).isEqualTo(Phase.GREEN);
        assertThat(state.nextPhase()).isEqualTo(Phase.REFACTOR);
        assertThat(state.cycleNumber()).isEqualTo(2);
        assertThat(state.currentTest()).isNotNull();
        assertThat(state.currentTest().description()).isEqualTo("should subtract numbers");
        assertThat(state.completedTests()).containsExactly("test1");
        assertThat(state.pendingTests()).containsExactly("test2", "test3");
        assertThat(state.testResult()).isEqualTo(TestResult.PASS);
        assertThat(state.retryCount()).isEqualTo(1);
    }

    @Test
    void shouldRoundTripSerializeAndDeserialize() throws Exception {
        ErrorDetails errorDetails = new ErrorDetails("CompilationError", "Missing semicolon");

        HandoffState original = HandoffState.builder()
                .phase(Phase.REFACTOR)
                .nextPhase(Phase.PLAN)
                .cycleNumber(3)
                .currentTest(new TestCase("test", "test.java", "impl.java"))
                .completedTests(List.of("a", "b", "c"))
                .pendingTests(List.of())
                .testResult(TestResult.PASS)
                .error("Compilation failed")
                .errorDetails(errorDetails)
                .retryCount(2)
                .build();

        String json = objectMapper.writeValueAsString(original);
        HandoffState deserialized = objectMapper.readValue(json, HandoffState.class);

        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void shouldHandleNullCurrentTest() throws Exception {
        String json = """
                {
                    "phase": "COMPLETE",
                    "nextPhase": null,
                    "cycleNumber": 5,
                    "currentTest": null,
                    "completedTests": ["all", "tests"],
                    "pendingTests": [],
                    "testResult": "PASS",
                    "error": null,
                    "errorDetails": null,
                    "retryCount": 0
                }
                """;

        HandoffState state = objectMapper.readValue(json, HandoffState.class);

        assertThat(state.phase()).isEqualTo(Phase.COMPLETE);
        assertThat(state.currentTest()).isNull();
        assertThat(state.completedTests()).containsExactly("all", "tests");
        assertThat(state.pendingTests()).isEmpty();
    }

    @Test
    void shouldCreateInitialState() {
        HandoffState initial = HandoffState.initial();

        assertThat(initial.phase()).isEqualTo(Phase.PLAN);
        assertThat(initial.nextPhase()).isEqualTo(Phase.RED);
        assertThat(initial.cycleNumber()).isEqualTo(1);
        assertThat(initial.currentTest()).isNull();
        assertThat(initial.completedTests()).isEmpty();
        assertThat(initial.pendingTests()).isEmpty();
        assertThat(initial.testResult()).isNull();
        assertThat(initial.error()).isNull();
        assertThat(initial.errorDetails()).isNull();
        assertThat(initial.retryCount()).isEqualTo(0);
    }

    @Test
    void shouldBuildModifiedCopyWithToBuilder() {
        HandoffState original = HandoffState.builder()
                .phase(Phase.RED)
                .nextPhase(Phase.GREEN)
                .cycleNumber(1)
                .build();

        HandoffState modified = original.toBuilder()
                .phase(Phase.GREEN)
                .nextPhase(Phase.REFACTOR)
                .testResult(TestResult.PASS)
                .build();

        // Original unchanged
        assertThat(original.phase()).isEqualTo(Phase.RED);
        assertThat(original.testResult()).isNull();

        // Modified has new values
        assertThat(modified.phase()).isEqualTo(Phase.GREEN);
        assertThat(modified.nextPhase()).isEqualTo(Phase.REFACTOR);
        assertThat(modified.testResult()).isEqualTo(TestResult.PASS);
        assertThat(modified.cycleNumber()).isEqualTo(1); // Preserved
    }

    @Test
    void shouldHaveProperEqualsAndHashCode() {
        HandoffState state1 = HandoffState.builder()
                .phase(Phase.RED)
                .cycleNumber(1)
                .build();

        HandoffState state2 = HandoffState.builder()
                .phase(Phase.RED)
                .cycleNumber(1)
                .build();

        HandoffState state3 = HandoffState.builder()
                .phase(Phase.GREEN)
                .cycleNumber(1)
                .build();

        assertThat(state1).isEqualTo(state2);
        assertThat(state1.hashCode()).isEqualTo(state2.hashCode());
        assertThat(state1).isNotEqualTo(state3);
    }

    @Test
    void shouldHaveReadableToString() {
        HandoffState state = HandoffState.builder()
                .phase(Phase.RED)
                .cycleNumber(1)
                .build();

        String toString = state.toString();

        assertThat(toString).contains("HandoffState");
        assertThat(toString).contains("phase=RED");
        assertThat(toString).contains("cycleNumber=1");
    }

    @Test
    void shouldMakeDefensiveCopiesOfLists() {
        var mutableList = new java.util.ArrayList<>(List.of("test1", "test2"));

        HandoffState state = HandoffState.builder()
                .phase(Phase.PLAN)
                .completedTests(mutableList)
                .build();

        // Modify original list
        mutableList.add("test3");

        // State should not be affected
        assertThat(state.completedTests()).containsExactly("test1", "test2");
    }
}
