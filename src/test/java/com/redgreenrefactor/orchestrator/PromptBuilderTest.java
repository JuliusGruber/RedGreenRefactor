package com.redgreenrefactor.orchestrator;

import com.redgreenrefactor.model.ErrorDetails;
import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.Phase;
import com.redgreenrefactor.model.TestCase;
import com.redgreenrefactor.model.TestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptBuilderTest {

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilder();
    }

    @Test
    void buildPlanPrompt_includesFeatureRequest() {
        HandoffState state = HandoffState.initial();

        String prompt = promptBuilder.buildPlanPrompt("Add user authentication", state);

        assertThat(prompt).contains("Add user authentication");
        assertThat(prompt).contains("## Feature Request");
    }

    @Test
    void buildPlanPrompt_includesPhaseAndCycleInfo() {
        HandoffState state = HandoffState.builder()
                .phase(Phase.PLAN)
                .cycleNumber(3)
                .build();

        String prompt = promptBuilder.buildPlanPrompt("Test feature", state);

        assertThat(prompt).contains("Phase: PLAN");
        assertThat(prompt).contains("Cycle: 3");
    }

    @Test
    void buildPlanPrompt_includesCompletedTests() {
        HandoffState state = HandoffState.builder()
                .phase(Phase.PLAN)
                .cycleNumber(2)
                .completedTests(List.of("test login", "test logout"))
                .build();

        String prompt = promptBuilder.buildPlanPrompt("Test feature", state);

        assertThat(prompt).contains("Completed tests: 2");
        assertThat(prompt).contains("[x] test login");
        assertThat(prompt).contains("[x] test logout");
    }

    @Test
    void buildPlanPrompt_includesPendingTests() {
        HandoffState state = HandoffState.builder()
                .phase(Phase.PLAN)
                .cycleNumber(1)
                .pendingTests(List.of("test signup", "test password reset"))
                .build();

        String prompt = promptBuilder.buildPlanPrompt("Test feature", state);

        assertThat(prompt).contains("[ ] test signup");
        assertThat(prompt).contains("[ ] test password reset");
    }

    @Test
    void buildPlanPrompt_showsNewFeatureInstructionsForFirstCycle() {
        HandoffState state = HandoffState.initial();

        String prompt = promptBuilder.buildPlanPrompt("Test feature", state);

        assertThat(prompt).contains("This is a new feature");
        assertThat(prompt).contains("Analyze the feature requirements");
    }

    @Test
    void buildPlanPrompt_showsSelectNextTestInstructionsForSubsequentCycles() {
        HandoffState state = HandoffState.builder()
                .phase(Phase.PLAN)
                .cycleNumber(2)
                .completedTests(List.of("first test"))
                .build();

        String prompt = promptBuilder.buildPlanPrompt("Test feature", state);

        assertThat(prompt).contains("Select the next pending test");
        assertThat(prompt).doesNotContain("This is a new feature");
    }

    @Test
    void buildPlanPrompt_throwsOnNullFeatureRequest() {
        HandoffState state = HandoffState.initial();

        assertThatThrownBy(() -> promptBuilder.buildPlanPrompt(null, state))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("featureRequest");
    }

    @Test
    void buildPlanPrompt_throwsOnNullState() {
        assertThatThrownBy(() -> promptBuilder.buildPlanPrompt("feature", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("state");
    }

    @Test
    void buildRedPrompt_includesTestCaseDetails() {
        TestCase testCase = new TestCase(
                "should add two numbers",
                "src/test/java/CalculatorTest.java",
                "src/main/java/Calculator.java"
        );
        HandoffState state = HandoffState.builder()
                .phase(Phase.RED)
                .cycleNumber(1)
                .currentTest(testCase)
                .build();

        String prompt = promptBuilder.buildRedPrompt(state);

        assertThat(prompt).contains("RED (Write Failing Test)");
        assertThat(prompt).contains("should add two numbers");
        assertThat(prompt).contains("src/test/java/CalculatorTest.java");
        assertThat(prompt).contains("src/main/java/Calculator.java");
    }

    @Test
    void buildRedPrompt_includesCycleNumber() {
        TestCase testCase = new TestCase("test", "test.java", "impl.java");
        HandoffState state = HandoffState.builder()
                .phase(Phase.RED)
                .cycleNumber(5)
                .currentTest(testCase)
                .build();

        String prompt = promptBuilder.buildRedPrompt(state);

        assertThat(prompt).contains("Cycle: 5");
    }

    @Test
    void buildRedPrompt_throwsWithoutCurrentTest() {
        HandoffState state = HandoffState.builder()
                .phase(Phase.RED)
                .cycleNumber(1)
                .build();

        assertThatThrownBy(() -> promptBuilder.buildRedPrompt(state))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("current test");
    }

    @Test
    void buildGreenPrompt_includesTestCaseDetails() {
        TestCase testCase = new TestCase(
                "should multiply numbers",
                "src/test/java/MathTest.java",
                "src/main/java/MathUtils.java"
        );
        HandoffState state = HandoffState.builder()
                .phase(Phase.GREEN)
                .cycleNumber(2)
                .currentTest(testCase)
                .build();

        String prompt = promptBuilder.buildGreenPrompt(state);

        assertThat(prompt).contains("GREEN (Make Test Pass)");
        assertThat(prompt).contains("should multiply numbers");
        assertThat(prompt).contains("MathTest.java");
        assertThat(prompt).contains("MathUtils.java");
    }

    @Test
    void buildGreenPrompt_includesTestResult() {
        TestCase testCase = new TestCase("test", "test.java", "impl.java");
        HandoffState state = HandoffState.builder()
                .phase(Phase.GREEN)
                .cycleNumber(1)
                .currentTest(testCase)
                .testResult(TestResult.FAIL)
                .build();

        String prompt = promptBuilder.buildGreenPrompt(state);

        assertThat(prompt).contains("Previous Test Result: FAIL");
    }

    @Test
    void buildGreenPrompt_throwsWithoutCurrentTest() {
        HandoffState state = HandoffState.builder()
                .phase(Phase.GREEN)
                .cycleNumber(1)
                .build();

        assertThatThrownBy(() -> promptBuilder.buildGreenPrompt(state))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("current test");
    }

    @Test
    void buildRefactorPrompt_includesTestCaseDetails() {
        TestCase testCase = new TestCase(
                "should validate input",
                "src/test/java/ValidatorTest.java",
                "src/main/java/Validator.java"
        );
        HandoffState state = HandoffState.builder()
                .phase(Phase.REFACTOR)
                .cycleNumber(3)
                .currentTest(testCase)
                .build();

        String prompt = promptBuilder.buildRefactorPrompt(state);

        assertThat(prompt).contains("REFACTOR (Improve Code Quality)");
        assertThat(prompt).contains("should validate input");
        assertThat(prompt).contains("ValidatorTest.java");
        assertThat(prompt).contains("Validator.java");
    }

    @Test
    void buildRefactorPrompt_includesMarkTestCompleteInstruction() {
        TestCase testCase = new TestCase("test", "test.java", "impl.java");
        HandoffState state = HandoffState.builder()
                .phase(Phase.REFACTOR)
                .cycleNumber(1)
                .currentTest(testCase)
                .build();

        String prompt = promptBuilder.buildRefactorPrompt(state);

        assertThat(prompt).contains("Mark the test as complete");
        assertThat(prompt).contains("test-list.md");
    }

    @Test
    void buildRefactorPrompt_throwsWithoutCurrentTest() {
        HandoffState state = HandoffState.builder()
                .phase(Phase.REFACTOR)
                .cycleNumber(1)
                .build();

        assertThatThrownBy(() -> promptBuilder.buildRefactorPrompt(state))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("current test");
    }

    @Test
    void allPrompts_includeErrorContext_whenErrorPresent() {
        TestCase testCase = new TestCase("test", "test.java", "impl.java");
        ErrorDetails errorDetails = new ErrorDetails("CompilationError", "Missing semicolon");

        HandoffState state = HandoffState.builder()
                .phase(Phase.RED)
                .cycleNumber(1)
                .currentTest(testCase)
                .error("Compilation failed at line 42")
                .errorDetails(errorDetails)
                .retryCount(2)
                .build();

        String prompt = promptBuilder.buildRedPrompt(state);

        assertThat(prompt).contains("Error Context (Retry Attempt 2)");
        assertThat(prompt).contains("Compilation failed at line 42");
        assertThat(prompt).contains("Type: CompilationError");
        assertThat(prompt).contains("Message: Missing semicolon");
        assertThat(prompt).contains("Please address the error");
    }
}
