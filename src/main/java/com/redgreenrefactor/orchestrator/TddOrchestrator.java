package com.redgreenrefactor.orchestrator;

import com.redgreenrefactor.model.ErrorDetails;
import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.Phase;
import com.redgreenrefactor.model.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Main orchestration loop for the TDD workflow.
 * <p>
 * The workflow follows this pattern:
 * <pre>
 * PLAN (initial) -> extract TestCase
 *   |
 *   +---(currentTest: null)---> COMPLETE (no tests)
 *   |
 *   +---(currentTest: {...})---> TDD CYCLE:
 *         |
 *         RED -> GREEN -> REFACTOR
 *         |
 *         +---> PLAN (next test)
 *               |
 *               +---(currentTest: null)---> COMPLETE
 *               |
 *               +---(currentTest: {...})---> RED (loop)
 * </pre>
 */
public class TddOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(TddOrchestrator.class);
    private static final int MAX_RETRIES_PER_PHASE = 3;
    private static final int MAX_CYCLES = 100;  // Safety limit

    private final PhaseExecutor phaseExecutor;
    private final TestListOutputParser outputParser;

    /**
     * Creates a new TddOrchestrator.
     *
     * @param phaseExecutor the phase executor
     */
    public TddOrchestrator(PhaseExecutor phaseExecutor) {
        this.phaseExecutor = Objects.requireNonNull(phaseExecutor, "phaseExecutor cannot be null");
        this.outputParser = new TestListOutputParser();
    }

    /**
     * Creates a new TddOrchestrator with a custom output parser.
     *
     * @param phaseExecutor the phase executor
     * @param outputParser  the output parser for test selection
     */
    public TddOrchestrator(PhaseExecutor phaseExecutor, TestListOutputParser outputParser) {
        this.phaseExecutor = Objects.requireNonNull(phaseExecutor, "phaseExecutor cannot be null");
        this.outputParser = Objects.requireNonNull(outputParser, "outputParser cannot be null");
    }

    /**
     * Runs the complete TDD workflow for a feature request.
     *
     * @param featureRequest the user's feature request
     * @return the result of the workflow execution
     */
    public WorkflowResult runWorkflow(String featureRequest) {
        Objects.requireNonNull(featureRequest, "featureRequest cannot be null");
        if (featureRequest.isBlank()) {
            throw new IllegalArgumentException("featureRequest cannot be blank");
        }

        LOG.info("Starting TDD workflow for feature request");
        List<PhaseResult> allPhaseResults = new ArrayList<>();
        HandoffState state = HandoffState.initial();
        int completedCycles = 0;

        try {
            // Initial PLAN phase
            PhaseResult planResult = executePhaseWithRetry(Phase.PLAN, state, featureRequest, allPhaseResults);
            if (!planResult.success()) {
                return WorkflowResult.failure(planResult.updatedState(), completedCycles, allPhaseResults,
                        "Initial PLAN phase failed: " + planResult.error());
            }
            state = planResult.updatedState();

            // Extract test selection from PLAN response
            Optional<TestCase> testSelection = parseTestSelection(planResult.agentResponse());
            if (testSelection.isEmpty()) {
                LOG.info("No tests to implement - workflow complete");
                state = state.toBuilder().phase(Phase.COMPLETE).nextPhase(Phase.COMPLETE).build();
                return WorkflowResult.success(state, completedCycles, allPhaseResults);
            }

            state = state.toBuilder().currentTest(testSelection.get()).build();

            // Main TDD cycle loop
            while (completedCycles < MAX_CYCLES) {
                completedCycles++;
                LOG.info("Starting TDD cycle {}", completedCycles);

                // RED phase
                PhaseResult redResult = executePhaseWithRetry(Phase.RED, state, featureRequest, allPhaseResults);
                if (!redResult.success()) {
                    return WorkflowResult.failure(redResult.updatedState(), completedCycles, allPhaseResults,
                            "RED phase failed in cycle " + completedCycles + ": " + redResult.error());
                }
                state = redResult.updatedState();

                // GREEN phase
                PhaseResult greenResult = executePhaseWithRetry(Phase.GREEN, state, featureRequest, allPhaseResults);
                if (!greenResult.success()) {
                    return WorkflowResult.failure(greenResult.updatedState(), completedCycles, allPhaseResults,
                            "GREEN phase failed in cycle " + completedCycles + ": " + greenResult.error());
                }
                state = greenResult.updatedState();

                // REFACTOR phase
                PhaseResult refactorResult = executePhaseWithRetry(Phase.REFACTOR, state, featureRequest, allPhaseResults);
                if (!refactorResult.success()) {
                    return WorkflowResult.failure(refactorResult.updatedState(), completedCycles, allPhaseResults,
                            "REFACTOR phase failed in cycle " + completedCycles + ": " + refactorResult.error());
                }
                state = refactorResult.updatedState();

                // Update completed tests list
                state = updateCompletedTests(state);

                // PLAN phase for next test
                state = state.toBuilder()
                        .cycleNumber(completedCycles + 1)
                        .currentTest(null)
                        .build();

                PhaseResult nextPlanResult = executePhaseWithRetry(Phase.PLAN, state, featureRequest, allPhaseResults);
                if (!nextPlanResult.success()) {
                    return WorkflowResult.failure(nextPlanResult.updatedState(), completedCycles, allPhaseResults,
                            "PLAN phase failed after cycle " + completedCycles + ": " + nextPlanResult.error());
                }
                state = nextPlanResult.updatedState();

                // Check if more tests remain
                testSelection = parseTestSelection(nextPlanResult.agentResponse());
                if (testSelection.isEmpty()) {
                    LOG.info("All tests complete after {} cycles", completedCycles);
                    state = state.toBuilder().phase(Phase.COMPLETE).nextPhase(Phase.COMPLETE).build();
                    return WorkflowResult.success(state, completedCycles, allPhaseResults);
                }

                state = state.toBuilder().currentTest(testSelection.get()).build();
            }

            // Max cycles reached
            LOG.warn("Maximum cycles ({}) reached", MAX_CYCLES);
            return WorkflowResult.failure(state, completedCycles, allPhaseResults,
                    "Maximum cycles (" + MAX_CYCLES + ") reached without completing all tests");

        } catch (Exception e) {
            LOG.error("Workflow failed with unexpected error: {}", e.getMessage(), e);
            return WorkflowResult.failure(state, completedCycles, allPhaseResults,
                    "Unexpected error: " + e.getMessage());
        }
    }

    private PhaseResult executePhaseWithRetry(Phase phase, HandoffState state, String featureRequest,
                                               List<PhaseResult> allPhaseResults) {
        int retryCount = 0;
        HandoffState currentState = state;

        while (retryCount <= MAX_RETRIES_PER_PHASE) {
            PhaseResult result = phaseExecutor.runPhase(phase, currentState, featureRequest);
            allPhaseResults.add(result);

            if (result.success()) {
                return result;
            }

            retryCount++;
            if (retryCount <= MAX_RETRIES_PER_PHASE) {
                LOG.warn("Phase {} failed, retry {}/{}: {}", phase, retryCount, MAX_RETRIES_PER_PHASE, result.error());
                currentState = currentState.toBuilder()
                        .error(result.error())
                        .errorDetails(new ErrorDetails("PhaseFailure", result.error()))
                        .retryCount(retryCount)
                        .build();
            }
        }

        // Return the last failed result
        return allPhaseResults.get(allPhaseResults.size() - 1);
    }

    private Optional<TestCase> parseTestSelection(String agentResponse) {
        try {
            return outputParser.parseTestSelection(agentResponse);
        } catch (OrchestratorException e) {
            LOG.warn("Failed to parse test selection: {}", e.getMessage());
            throw e;
        }
    }

    private HandoffState updateCompletedTests(HandoffState state) {
        if (state.currentTest() == null) {
            return state;
        }

        List<String> completedTests = new ArrayList<>(state.completedTests());
        completedTests.add(state.currentTest().description());

        // Remove from pending if present
        List<String> pendingTests = new ArrayList<>(state.pendingTests());
        pendingTests.remove(state.currentTest().description());

        return state.toBuilder()
                .completedTests(completedTests)
                .pendingTests(pendingTests)
                .build();
    }
}
