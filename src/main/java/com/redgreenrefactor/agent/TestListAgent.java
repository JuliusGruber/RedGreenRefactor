package com.redgreenrefactor.agent;

import com.redgreenrefactor.model.AgentConfig;
import com.redgreenrefactor.tool.TDDTools;

/**
 * Test List Agent for the PLAN phase.
 *
 * <p>This agent is responsible for:
 * <ul>
 *   <li>Analyzing feature requirements</li>
 *   <li>Creating or updating the test list file (test-list.md)</li>
 *   <li>Selecting the next pending test to implement</li>
 *   <li>Committing the plan with a "plan:" prefix</li>
 * </ul>
 *
 * <p>The agent outputs JSON with the selected test case:
 * <pre>{@code
 * {
 *   "currentTest": {
 *     "description": "test description",
 *     "testFile": "src/test/java/...",
 *     "implFile": "src/main/java/..."
 *   }
 * }
 * }</pre>
 *
 * <p>When all tests are complete, it outputs: {@code {"currentTest": null}}
 */
public final class TestListAgent {

    public static final String NAME = "TestListAgent";
    public static final String DESCRIPTION = "Planning agent that manages the test list and selects the next test to implement";

    private static final String SYSTEM_PROMPT = """
        You are the Test List Agent, responsible for planning and organizing test-driven development.

        ## Your Role
        You analyze feature requirements and maintain a prioritized list of tests to implement.
        You select ONE test at a time for the TDD cycle.

        ## Test List File
        - Maintain a file called `test-list.md` in the project root
        - Use markdown checkbox format:
          - `- [ ] Test description` for pending tests
          - `- [x] Test description` for completed tests
        - Group related tests under headers if helpful

        ## When Planning a New Feature
        1. Read the feature requirements carefully
        2. Break down the feature into small, testable increments
        3. Create or update `test-list.md` with the test list
        4. Order tests from simplest to most complex (build up functionality incrementally)

        ## When Selecting the Next Test
        1. Read `test-list.md` to find the current state
        2. Find the first unchecked test (`- [ ]`)
        3. Determine the appropriate test file path (follow project conventions)
        4. Determine the implementation file path

        ## Required Output Format
        After selecting a test, output a JSON block with the test details:

        ```json
        {
          "currentTest": {
            "description": "exact test description from test-list.md",
            "testFile": "src/test/java/com/example/FeatureTest.java",
            "implFile": "src/main/java/com/example/Feature.java"
          }
        }
        ```

        When ALL tests in test-list.md are marked complete (`[x]`), output:
        ```json
        {"currentTest": null}
        ```

        ## Committing
        After updating the test list, commit your changes using the Bash tool:
        ```
        git add test-list.md && git commit -m "plan: <brief description of what was planned>"
        ```

        ## Important Rules
        - Select only ONE test at a time
        - Tests should be small and focused (one assertion concept per test)
        - Follow existing project naming conventions for file paths
        - If test-list.md doesn't exist, create it
        - Never skip tests - process them in order
        """;

    private TestListAgent() {
        // Utility class
    }

    /**
     * Creates the AgentConfig for the Test List Agent.
     *
     * @return the agent configuration
     */
    public static AgentConfig createConfig() {
        return AgentConfig.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .systemPrompt(SYSTEM_PROMPT)
                .tools(TDDTools.getAllTools())
                .build();
    }
}
