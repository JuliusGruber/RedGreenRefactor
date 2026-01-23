package com.redgreenrefactor.orchestrator;

import com.redgreenrefactor.agent.AgentInvoker;
import com.redgreenrefactor.agent.ImplementingAgent;
import com.redgreenrefactor.agent.RefactorAgent;
import com.redgreenrefactor.agent.TestAgent;
import com.redgreenrefactor.agent.TestListAgent;
import com.redgreenrefactor.git.GitNotesManager;
import com.redgreenrefactor.git.GitOperations;
import com.redgreenrefactor.model.AgentConfig;
import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.Phase;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Executes individual phases of the TDD workflow.
 * <p>
 * For each phase:
 * <ol>
 *   <li>Gets the appropriate AgentConfig</li>
 *   <li>Builds the prompt via PromptBuilder</li>
 *   <li>Invokes the agent via AgentInvoker</li>
 *   <li>Gets the latest commit ID (agent commits via Bash)</li>
 *   <li>Writes the handoff note to the commit</li>
 *   <li>Returns the PhaseResult with updated state</li>
 * </ol>
 */
public class PhaseExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(PhaseExecutor.class);

    private final AgentInvoker agentInvoker;
    private final GitOperations gitOperations;
    private final GitNotesManager gitNotesManager;
    private final PromptBuilder promptBuilder;

    /**
     * Creates a new PhaseExecutor.
     *
     * @param agentInvoker    the agent invoker for running agents
     * @param gitOperations   the git operations handler
     * @param gitNotesManager the git notes manager for handoff state
     */
    public PhaseExecutor(AgentInvoker agentInvoker, GitOperations gitOperations, GitNotesManager gitNotesManager) {
        this.agentInvoker = Objects.requireNonNull(agentInvoker, "agentInvoker cannot be null");
        this.gitOperations = Objects.requireNonNull(gitOperations, "gitOperations cannot be null");
        this.gitNotesManager = Objects.requireNonNull(gitNotesManager, "gitNotesManager cannot be null");
        this.promptBuilder = new PromptBuilder();
    }

    /**
     * Creates a new PhaseExecutor with a custom PromptBuilder.
     *
     * @param agentInvoker    the agent invoker for running agents
     * @param gitOperations   the git operations handler
     * @param gitNotesManager the git notes manager for handoff state
     * @param promptBuilder   the prompt builder
     */
    public PhaseExecutor(AgentInvoker agentInvoker, GitOperations gitOperations,
                         GitNotesManager gitNotesManager, PromptBuilder promptBuilder) {
        this.agentInvoker = Objects.requireNonNull(agentInvoker, "agentInvoker cannot be null");
        this.gitOperations = Objects.requireNonNull(gitOperations, "gitOperations cannot be null");
        this.gitNotesManager = Objects.requireNonNull(gitNotesManager, "gitNotesManager cannot be null");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder cannot be null");
    }

    /**
     * Executes a single phase of the TDD workflow.
     *
     * @param phase          the phase to execute
     * @param state          the current handoff state
     * @param featureRequest the original feature request (used for PLAN phase)
     * @return the result of executing the phase
     */
    public PhaseResult runPhase(Phase phase, HandoffState state, String featureRequest) {
        Objects.requireNonNull(phase, "phase cannot be null");
        Objects.requireNonNull(state, "state cannot be null");

        LOG.info("Executing phase: {} (cycle {})", phase, state.cycleNumber());

        try {
            AgentConfig agentConfig = getAgentConfig(phase);
            String prompt = buildPrompt(phase, state, featureRequest);

            LOG.debug("Invoking agent {} with prompt length: {}", agentConfig.name(), prompt.length());
            AgentInvoker.AgentResponse response = agentInvoker.invoke(agentConfig, prompt);

            // Get the latest commit (the agent should have committed)
            Optional<ObjectId> commitId = gitOperations.getLatestCommit();

            // Build updated state
            HandoffState updatedState = buildUpdatedState(phase, state, response.responseText());

            // Write handoff note if we have a commit
            if (commitId.isPresent()) {
                gitNotesManager.writeHandoff(commitId.get(), updatedState);
                LOG.info("Phase {} completed successfully, commit: {}", phase, commitId.get().abbreviate(7).name());
                return PhaseResult.success(phase, updatedState, commitId.get(), response.responseText());
            } else {
                LOG.warn("Phase {} completed but no commit was found", phase);
                return PhaseResult.success(phase, updatedState, null, response.responseText());
            }

        } catch (Exception e) {
            LOG.error("Phase {} failed: {}", phase, e.getMessage(), e);
            return PhaseResult.failure(phase, state, "", e.getMessage());
        }
    }

    private AgentConfig getAgentConfig(Phase phase) {
        return switch (phase) {
            case PLAN -> TestListAgent.createConfig();
            case RED -> TestAgent.createConfig();
            case GREEN -> ImplementingAgent.createConfig();
            case REFACTOR -> RefactorAgent.createConfig();
            case COMPLETE -> throw new IllegalArgumentException("Cannot execute COMPLETE phase");
        };
    }

    private String buildPrompt(Phase phase, HandoffState state, String featureRequest) {
        return switch (phase) {
            case PLAN -> promptBuilder.buildPlanPrompt(
                    featureRequest != null ? featureRequest : "Continue with next test",
                    state);
            case RED -> promptBuilder.buildRedPrompt(state);
            case GREEN -> promptBuilder.buildGreenPrompt(state);
            case REFACTOR -> promptBuilder.buildRefactorPrompt(state);
            case COMPLETE -> throw new IllegalArgumentException("Cannot build prompt for COMPLETE phase");
        };
    }

    private HandoffState buildUpdatedState(Phase phase, HandoffState currentState, String response) {
        // Determine next phase based on workflow
        Phase nextPhase = determineNextPhase(phase);

        return currentState.toBuilder()
                .phase(phase)
                .nextPhase(nextPhase)
                .error(null)  // Clear any previous error on success
                .errorDetails(null)
                .retryCount(0)
                .build();
    }

    private Phase determineNextPhase(Phase currentPhase) {
        return switch (currentPhase) {
            case PLAN -> Phase.RED;
            case RED -> Phase.GREEN;
            case GREEN -> Phase.REFACTOR;
            case REFACTOR -> Phase.PLAN;  // Back to PLAN to select next test
            case COMPLETE -> Phase.COMPLETE;
        };
    }
}
