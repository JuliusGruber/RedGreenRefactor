package com.redgreenrefactor.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redgreenrefactor.model.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Test List Agent's JSON output to extract test selection.
 * <p>
 * The agent outputs JSON in one of two formats:
 * <ul>
 *   <li>Selected test: {@code {"currentTest": {"description": "...", "testFile": "...", "implFile": "..."}}}</li>
 *   <li>Workflow complete: {@code {"currentTest": null}}</li>
 * </ul>
 * <p>
 * The JSON may be wrapped in markdown code blocks or appear inline in the response text.
 */
public class TestListOutputParser {

    private static final Logger LOG = LoggerFactory.getLogger(TestListOutputParser.class);

    /**
     * Pattern to extract JSON from markdown code blocks.
     * Matches ```json ... ``` or ``` ... ```
     */
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile(
            "```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```",
            Pattern.MULTILINE
    );

    /**
     * Pattern to find JSON object starting with {"currentTest"
     */
    private static final Pattern INLINE_JSON_PATTERN = Pattern.compile(
            "\\{\\s*\"currentTest\"\\s*:.*?\\}(?:\\s*\\})?",
            Pattern.DOTALL
    );

    private final ObjectMapper objectMapper;

    /**
     * Creates a new parser with a default ObjectMapper.
     */
    public TestListOutputParser() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a new parser with the specified ObjectMapper.
     *
     * @param objectMapper the ObjectMapper to use for parsing
     */
    public TestListOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    /**
     * Parses the agent's response text to extract the selected test case.
     *
     * @param responseText the full response text from the Test List Agent
     * @return Optional containing the TestCase if selected, or empty if workflow is complete
     *         (indicated by {@code {"currentTest": null}})
     * @throws OrchestratorException if the response cannot be parsed or is malformed
     */
    public Optional<TestCase> parseTestSelection(String responseText) {
        Objects.requireNonNull(responseText, "responseText cannot be null");

        String json = extractJson(responseText);
        if (json == null) {
            throw new OrchestratorException(
                    "Could not find JSON output in agent response. Expected format: {\"currentTest\": {...}} or {\"currentTest\": null}");
        }

        LOG.debug("Extracted JSON: {}", json);

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode currentTestNode = root.get("currentTest");

            if (currentTestNode == null) {
                throw new OrchestratorException(
                        "JSON response missing 'currentTest' field. Got: " + json);
            }

            if (currentTestNode.isNull()) {
                LOG.info("All tests complete - currentTest is null");
                return Optional.empty();
            }

            if (!currentTestNode.isObject()) {
                throw new OrchestratorException(
                        "Expected 'currentTest' to be an object or null, but got: " + currentTestNode.getNodeType());
            }

            TestCase testCase = parseTestCaseNode(currentTestNode);
            LOG.info("Parsed test selection: {}", testCase.description());
            return Optional.of(testCase);

        } catch (OrchestratorException e) {
            throw e;
        } catch (Exception e) {
            throw new OrchestratorException("Failed to parse JSON: " + e.getMessage(), e);
        }
    }

    private String extractJson(String text) {
        // First try to extract from markdown code block
        Matcher blockMatcher = JSON_BLOCK_PATTERN.matcher(text);
        while (blockMatcher.find()) {
            String content = blockMatcher.group(1).trim();
            if (content.contains("\"currentTest\"")) {
                return content;
            }
        }

        // Fall back to finding inline JSON
        Matcher inlineMatcher = INLINE_JSON_PATTERN.matcher(text);
        if (inlineMatcher.find()) {
            String match = inlineMatcher.group();
            // Ensure we have balanced braces
            return balanceBraces(match);
        }

        // Last resort: try to find any JSON object with currentTest
        int start = text.indexOf("{\"currentTest\"");
        if (start == -1) {
            start = text.indexOf("{ \"currentTest\"");
        }
        if (start != -1) {
            return extractJsonObject(text, start);
        }

        return null;
    }

    private String balanceBraces(String json) {
        int openBraces = 0;
        int closeBraces = 0;
        for (char c : json.toCharArray()) {
            if (c == '{') openBraces++;
            if (c == '}') closeBraces++;
        }

        StringBuilder result = new StringBuilder(json);
        while (closeBraces < openBraces) {
            result.append('}');
            closeBraces++;
        }
        return result.toString();
    }

    private String extractJsonObject(String text, int start) {
        int braceCount = 0;
        int end = start;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    end = i + 1;
                    break;
                }
            }
        }

        if (end > start) {
            return text.substring(start, end);
        }
        return null;
    }

    private TestCase parseTestCaseNode(JsonNode node) {
        String description = getRequiredField(node, "description");
        String testFile = getRequiredField(node, "testFile");
        String implFile = getRequiredField(node, "implFile");

        return new TestCase(description, testFile, implFile);
    }

    private String getRequiredField(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            throw new OrchestratorException(
                    "Missing required field '" + fieldName + "' in currentTest object");
        }
        if (!fieldNode.isTextual()) {
            throw new OrchestratorException(
                    "Field '" + fieldName + "' must be a string, but got: " + fieldNode.getNodeType());
        }
        String value = fieldNode.asText();
        if (value.isBlank()) {
            throw new OrchestratorException(
                    "Field '" + fieldName + "' cannot be blank");
        }
        return value;
    }
}
