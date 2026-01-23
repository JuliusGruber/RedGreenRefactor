package com.redgreenrefactor.orchestrator;

import com.redgreenrefactor.agent.AgentInvoker;
import com.redgreenrefactor.git.GitNotesManager;
import com.redgreenrefactor.git.GitOperations;
import com.redgreenrefactor.model.AgentConfig;
import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.Phase;
import com.redgreenrefactor.model.TestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhaseExecutorTest {

    private AgentInvoker mockAgentInvoker;
    private GitOperations mockGitOperations;
    private GitNotesManager mockGitNotesManager;
    private PromptBuilder mockPromptBuilder;
    private PhaseExecutor executor;

    @BeforeEach
    void setUp() {
        mockAgentInvoker = mock(AgentInvoker.class);
        mockGitOperations = mock(GitOperations.class);
        mockGitNotesManager = mock(GitNotesManager.class);
        mockPromptBuilder = mock(PromptBuilder.class);

        executor = new PhaseExecutor(mockAgentInvoker, mockGitOperations, mockGitNotesManager, mockPromptBuilder);
    }

    @Test
    void constructor_throwsOnNullAgentInvoker() {
        assertThatThrownBy(() -> new PhaseExecutor(null, mockGitOperations, mockGitNotesManager))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("agentInvoker");
    }

    @Test
    void constructor_throwsOnNullGitOperations() {
        assertThatThrownBy(() -> new PhaseExecutor(mockAgentInvoker, null, mockGitNotesManager))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("gitOperations");
    }

    @Test
    void constructor_throwsOnNullGitNotesManager() {
        assertThatThrownBy(() -> new PhaseExecutor(mockAgentInvoker, mockGitOperations, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("gitNotesManager");
    }

    @Test
    void runPhase_throwsOnNullPhase() {
        HandoffState state = HandoffState.initial();

        assertThatThrownBy(() -> executor.runPhase(null, state, "feature"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("phase");
    }

    @Test
    void runPhase_throwsOnNullState() {
        assertThatThrownBy(() -> executor.runPhase(Phase.PLAN, null, "feature"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("state");
    }

    @Test
    void runPhase_planPhase_invokesTestListAgent() throws Exception {
        HandoffState state = HandoffState.initial();
        ObjectId commitId = ObjectId.fromString("0123456789012345678901234567890123456789");
        AgentInvoker.AgentResponse agentResponse = new AgentInvoker.AgentResponse("planned", 1, List.of());

        when(mockPromptBuilder.buildPlanPrompt(anyString(), any(HandoffState.class))).thenReturn("test prompt");
        when(mockAgentInvoker.invoke(any(AgentConfig.class), eq("test prompt"))).thenReturn(agentResponse);
        when(mockGitOperations.getLatestCommit()).thenReturn(Optional.of(commitId));
        doNothing().when(mockGitNotesManager).writeHandoff(any(ObjectId.class), any(HandoffState.class));

        PhaseResult result = executor.runPhase(Phase.PLAN, state, "Add feature");

        assertThat(result.success()).isTrue();
        assertThat(result.executedPhase()).isEqualTo(Phase.PLAN);
        assertThat(result.commitId()).isEqualTo(commitId);

        ArgumentCaptor<AgentConfig> configCaptor = ArgumentCaptor.forClass(AgentConfig.class);
        verify(mockAgentInvoker).invoke(configCaptor.capture(), eq("test prompt"));
        assertThat(configCaptor.getValue().name()).isEqualTo("TestListAgent");
    }

    @Test
    void runPhase_redPhase_invokesTestAgent() throws Exception {
        TestCase testCase = new TestCase("test", "test.java", "impl.java");
        HandoffState state = HandoffState.builder()
                .phase(Phase.RED)
                .cycleNumber(1)
                .currentTest(testCase)
                .build();
        ObjectId commitId = ObjectId.fromString("0123456789012345678901234567890123456789");
        AgentInvoker.AgentResponse agentResponse = new AgentInvoker.AgentResponse("test written", 2, List.of());

        when(mockPromptBuilder.buildRedPrompt(any(HandoffState.class))).thenReturn("red prompt");
        when(mockAgentInvoker.invoke(any(AgentConfig.class), eq("red prompt"))).thenReturn(agentResponse);
        when(mockGitOperations.getLatestCommit()).thenReturn(Optional.of(commitId));
        doNothing().when(mockGitNotesManager).writeHandoff(any(ObjectId.class), any(HandoffState.class));

        PhaseResult result = executor.runPhase(Phase.RED, state, "feature");

        assertThat(result.success()).isTrue();
        assertThat(result.executedPhase()).isEqualTo(Phase.RED);

        ArgumentCaptor<AgentConfig> configCaptor = ArgumentCaptor.forClass(AgentConfig.class);
        verify(mockAgentInvoker).invoke(configCaptor.capture(), anyString());
        assertThat(configCaptor.getValue().name()).isEqualTo("TestAgent");
    }

    @Test
    void runPhase_greenPhase_invokesImplementingAgent() throws Exception {
        TestCase testCase = new TestCase("test", "test.java", "impl.java");
        HandoffState state = HandoffState.builder()
                .phase(Phase.GREEN)
                .cycleNumber(1)
                .currentTest(testCase)
                .build();
        AgentInvoker.AgentResponse agentResponse = new AgentInvoker.AgentResponse("implemented", 3, List.of());

        when(mockPromptBuilder.buildGreenPrompt(any(HandoffState.class))).thenReturn("green prompt");
        when(mockAgentInvoker.invoke(any(AgentConfig.class), eq("green prompt"))).thenReturn(agentResponse);
        when(mockGitOperations.getLatestCommit()).thenReturn(Optional.empty());

        PhaseResult result = executor.runPhase(Phase.GREEN, state, "feature");

        assertThat(result.success()).isTrue();
        assertThat(result.commitId()).isNull();

        ArgumentCaptor<AgentConfig> configCaptor = ArgumentCaptor.forClass(AgentConfig.class);
        verify(mockAgentInvoker).invoke(configCaptor.capture(), anyString());
        assertThat(configCaptor.getValue().name()).isEqualTo("ImplementingAgent");
    }

    @Test
    void runPhase_refactorPhase_invokesRefactorAgent() throws Exception {
        TestCase testCase = new TestCase("test", "test.java", "impl.java");
        HandoffState state = HandoffState.builder()
                .phase(Phase.REFACTOR)
                .cycleNumber(1)
                .currentTest(testCase)
                .build();
        ObjectId commitId = ObjectId.fromString("0123456789012345678901234567890123456789");
        AgentInvoker.AgentResponse agentResponse = new AgentInvoker.AgentResponse("refactored", 2, List.of());

        when(mockPromptBuilder.buildRefactorPrompt(any(HandoffState.class))).thenReturn("refactor prompt");
        when(mockAgentInvoker.invoke(any(AgentConfig.class), eq("refactor prompt"))).thenReturn(agentResponse);
        when(mockGitOperations.getLatestCommit()).thenReturn(Optional.of(commitId));
        doNothing().when(mockGitNotesManager).writeHandoff(any(ObjectId.class), any(HandoffState.class));

        PhaseResult result = executor.runPhase(Phase.REFACTOR, state, "feature");

        assertThat(result.success()).isTrue();

        ArgumentCaptor<AgentConfig> configCaptor = ArgumentCaptor.forClass(AgentConfig.class);
        verify(mockAgentInvoker).invoke(configCaptor.capture(), anyString());
        assertThat(configCaptor.getValue().name()).isEqualTo("RefactorAgent");
    }

    @Test
    void runPhase_throwsForCompletePhase() {
        HandoffState state = HandoffState.initial();

        assertThatThrownBy(() -> executor.runPhase(Phase.COMPLETE, state, "feature"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COMPLETE");
    }

    @Test
    void runPhase_updatesStateWithNextPhase() throws Exception {
        HandoffState state = HandoffState.initial();
        ObjectId commitId = ObjectId.fromString("0123456789012345678901234567890123456789");
        AgentInvoker.AgentResponse agentResponse = new AgentInvoker.AgentResponse("done", 1, List.of());

        when(mockPromptBuilder.buildPlanPrompt(anyString(), any(HandoffState.class))).thenReturn("prompt");
        when(mockAgentInvoker.invoke(any(AgentConfig.class), anyString())).thenReturn(agentResponse);
        when(mockGitOperations.getLatestCommit()).thenReturn(Optional.of(commitId));
        doNothing().when(mockGitNotesManager).writeHandoff(any(ObjectId.class), any(HandoffState.class));

        PhaseResult result = executor.runPhase(Phase.PLAN, state, "feature");

        assertThat(result.updatedState().phase()).isEqualTo(Phase.PLAN);
        assertThat(result.updatedState().nextPhase()).isEqualTo(Phase.RED);
    }

    @Test
    void runPhase_writesHandoffNoteOnSuccess() throws Exception {
        HandoffState state = HandoffState.initial();
        ObjectId commitId = ObjectId.fromString("0123456789012345678901234567890123456789");
        AgentInvoker.AgentResponse agentResponse = new AgentInvoker.AgentResponse("done", 1, List.of());

        when(mockPromptBuilder.buildPlanPrompt(anyString(), any(HandoffState.class))).thenReturn("prompt");
        when(mockAgentInvoker.invoke(any(AgentConfig.class), anyString())).thenReturn(agentResponse);
        when(mockGitOperations.getLatestCommit()).thenReturn(Optional.of(commitId));
        doNothing().when(mockGitNotesManager).writeHandoff(any(ObjectId.class), any(HandoffState.class));

        executor.runPhase(Phase.PLAN, state, "feature");

        verify(mockGitNotesManager).writeHandoff(eq(commitId), any(HandoffState.class));
    }

    @Test
    void runPhase_doesNotWriteHandoffNoteWhenNoCommit() throws Exception {
        HandoffState state = HandoffState.initial();
        AgentInvoker.AgentResponse agentResponse = new AgentInvoker.AgentResponse("done", 1, List.of());

        when(mockPromptBuilder.buildPlanPrompt(anyString(), any(HandoffState.class))).thenReturn("prompt");
        when(mockAgentInvoker.invoke(any(AgentConfig.class), anyString())).thenReturn(agentResponse);
        when(mockGitOperations.getLatestCommit()).thenReturn(Optional.empty());

        executor.runPhase(Phase.PLAN, state, "feature");

        verify(mockGitNotesManager, never()).writeHandoff(any(ObjectId.class), any(HandoffState.class));
    }

    @Test
    void runPhase_returnsFailureOnException() throws Exception {
        HandoffState state = HandoffState.initial();

        when(mockPromptBuilder.buildPlanPrompt(anyString(), any(HandoffState.class))).thenReturn("prompt");
        when(mockAgentInvoker.invoke(any(AgentConfig.class), anyString()))
                .thenThrow(new RuntimeException("API error"));

        PhaseResult result = executor.runPhase(Phase.PLAN, state, "feature");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("API error");
    }

    @Test
    void runPhase_clearsErrorOnSuccess() throws Exception {
        TestCase testCase = new TestCase("test", "test.java", "impl.java");
        HandoffState state = HandoffState.builder()
                .phase(Phase.RED)
                .cycleNumber(1)
                .currentTest(testCase)
                .error("Previous error")
                .retryCount(2)
                .build();
        ObjectId commitId = ObjectId.fromString("0123456789012345678901234567890123456789");
        AgentInvoker.AgentResponse agentResponse = new AgentInvoker.AgentResponse("done", 1, List.of());

        when(mockPromptBuilder.buildRedPrompt(any(HandoffState.class))).thenReturn("prompt");
        when(mockAgentInvoker.invoke(any(AgentConfig.class), anyString())).thenReturn(agentResponse);
        when(mockGitOperations.getLatestCommit()).thenReturn(Optional.of(commitId));
        doNothing().when(mockGitNotesManager).writeHandoff(any(ObjectId.class), any(HandoffState.class));

        PhaseResult result = executor.runPhase(Phase.RED, state, "feature");

        assertThat(result.success()).isTrue();
        assertThat(result.updatedState().error()).isNull();
        assertThat(result.updatedState().errorDetails()).isNull();
        assertThat(result.updatedState().retryCount()).isZero();
    }
}
