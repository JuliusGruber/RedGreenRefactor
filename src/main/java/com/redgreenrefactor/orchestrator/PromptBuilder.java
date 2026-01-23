package com.redgreenrefactor.orchestrator;

import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.TestCase;

import java.util.Objects;

/**
 * Builds context-aware prompts for each TDD phase.
 * <p>
 * Each prompt includes the current phase information, test case details,
 * cycle number, and any error context for retries.
 */
public class PromptBuilder {

    /**
     * Builds a prompt for the PLAN phase (Test List Agent).
     *
     * @param featureRequest the user's feature request
     * @param state          the current handoff state
     * @return the constructed prompt
     */
    public String buildPlanPrompt(String featureRequest, HandoffState state) {
        Objects.requireNonNull(featureRequest, "featureRequest cannot be null");
        Objects.requireNonNull(state, "state cannot be null");

        StringBuilder prompt = new StringBuilder();
        prompt.append("## Feature Request\n\n");
        prompt.append(featureRequest);
        prompt.append("\n\n## Current Status\n\n");
        prompt.append("- Phase: PLAN\n");
        prompt.append("- Cycle: ").append(state.cycleNumber()).append("\n");

        if (!state.completedTests().isEmpty()) {
            prompt.append("- Completed tests: ").append(state.completedTests().size()).append("\n");
            prompt.append("\n### Completed Tests\n");
            for (String test : state.completedTests()) {
                prompt.append("- [x] ").append(test).append("\n");
            }
        }

        if (!state.pendingTests().isEmpty()) {
            prompt.append("\n### Pending Tests\n");
            for (String test : state.pendingTests()) {
                prompt.append("- [ ] ").append(test).append("\n");
            }
        }

        appendErrorContext(prompt, state);

        prompt.append("\n## Instructions\n\n");
        if (state.cycleNumber() == 1 && state.completedTests().isEmpty()) {
            prompt.append("This is a new feature. Please:\n");
            prompt.append("1. Analyze the feature requirements\n");
            prompt.append("2. Create or update test-list.md with the planned tests\n");
            prompt.append("3. Select the first test to implement\n");
            prompt.append("4. Commit the plan\n");
            prompt.append("5. Output the selected test in JSON format\n");
        } else {
            prompt.append("Select the next pending test from test-list.md and output it in JSON format.\n");
            prompt.append("If all tests are complete, output {\"currentTest\": null}\n");
        }

        return prompt.toString();
    }

    /**
     * Builds a prompt for the RED phase (Test Agent).
     *
     * @param state the current handoff state
     * @return the constructed prompt
     */
    public String buildRedPrompt(HandoffState state) {
        Objects.requireNonNull(state, "state cannot be null");

        TestCase currentTest = state.currentTest();
        if (currentTest == null) {
            throw new IllegalArgumentException("Cannot build RED prompt without a current test");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("## Current Phase: RED (Write Failing Test)\n\n");
        prompt.append("- Cycle: ").append(state.cycleNumber()).append("\n\n");

        prompt.append("## Test to Write\n\n");
        prompt.append("- **Description**: ").append(currentTest.description()).append("\n");
        prompt.append("- **Test File**: ").append(currentTest.testFile()).append("\n");
        prompt.append("- **Implementation File**: ").append(currentTest.implFile()).append("\n");

        appendErrorContext(prompt, state);

        prompt.append("\n## Instructions\n\n");
        prompt.append("1. Write a test that verifies: ").append(currentTest.description()).append("\n");
        prompt.append("2. The test MUST fail when run (this is the RED phase)\n");
        prompt.append("3. Run all tests to verify the new test fails and existing tests pass\n");
        prompt.append("4. Commit the test with a 'test:' prefix\n");

        return prompt.toString();
    }

    /**
     * Builds a prompt for the GREEN phase (Implementing Agent).
     *
     * @param state the current handoff state
     * @return the constructed prompt
     */
    public String buildGreenPrompt(HandoffState state) {
        Objects.requireNonNull(state, "state cannot be null");

        TestCase currentTest = state.currentTest();
        if (currentTest == null) {
            throw new IllegalArgumentException("Cannot build GREEN prompt without a current test");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("## Current Phase: GREEN (Make Test Pass)\n\n");
        prompt.append("- Cycle: ").append(state.cycleNumber()).append("\n\n");

        prompt.append("## Test to Make Pass\n\n");
        prompt.append("- **Description**: ").append(currentTest.description()).append("\n");
        prompt.append("- **Test File**: ").append(currentTest.testFile()).append("\n");
        prompt.append("- **Implementation File**: ").append(currentTest.implFile()).append("\n");

        if (state.testResult() != null) {
            prompt.append("\n## Previous Test Result: ").append(state.testResult().name()).append("\n\n");
        }

        appendErrorContext(prompt, state);

        prompt.append("\n## Instructions\n\n");
        prompt.append("1. Read the failing test in ").append(currentTest.testFile()).append("\n");
        prompt.append("2. Write the MINIMUM code in ").append(currentTest.implFile()).append(" to make it pass\n");
        prompt.append("3. Run all tests to verify they ALL pass\n");
        prompt.append("4. Commit with a 'feat:' or 'fix:' prefix\n");

        return prompt.toString();
    }

    /**
     * Builds a prompt for the REFACTOR phase (Refactor Agent).
     *
     * @param state the current handoff state
     * @return the constructed prompt
     */
    public String buildRefactorPrompt(HandoffState state) {
        Objects.requireNonNull(state, "state cannot be null");

        TestCase currentTest = state.currentTest();
        if (currentTest == null) {
            throw new IllegalArgumentException("Cannot build REFACTOR prompt without a current test");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("## Current Phase: REFACTOR (Improve Code Quality)\n\n");
        prompt.append("- Cycle: ").append(state.cycleNumber()).append("\n\n");

        prompt.append("## Files to Review\n\n");
        prompt.append("- **Test File**: ").append(currentTest.testFile()).append("\n");
        prompt.append("- **Implementation File**: ").append(currentTest.implFile()).append("\n");
        prompt.append("- **Test Description**: ").append(currentTest.description()).append("\n");

        appendErrorContext(prompt, state);

        prompt.append("\n## Instructions\n\n");
        prompt.append("1. Review both the test and implementation code for improvements\n");
        prompt.append("2. Refactor for clarity, maintainability, and good design\n");
        prompt.append("3. Run tests after each change to ensure they still pass\n");
        prompt.append("4. Mark the test as complete in test-list.md (change [ ] to [x])\n");
        prompt.append("5. Commit with a 'refactor:' prefix (use --allow-empty if no changes needed)\n");

        return prompt.toString();
    }

    private void appendErrorContext(StringBuilder prompt, HandoffState state) {
        if (state.error() != null) {
            prompt.append("\n## Error Context (Retry Attempt ").append(state.retryCount()).append(")\n\n");
            prompt.append("The previous attempt failed with the following error:\n\n");
            prompt.append("```\n").append(state.error()).append("\n```\n");

            if (state.errorDetails() != null) {
                prompt.append("\n**Error Details**:\n");
                prompt.append("- Type: ").append(state.errorDetails().type()).append("\n");
                prompt.append("- Message: ").append(state.errorDetails().message()).append("\n");
            }

            prompt.append("\nPlease address the error and try again.\n");
        }
    }
}
