package com.redgreenrefactor.error;

import com.redgreenrefactor.git.GitOperationException;
import com.redgreenrefactor.git.GitOperations;
import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.Phase;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("RecoveryStrategy")
@ExtendWith(MockitoExtension.class)
class RecoveryStrategyTest {

    @Mock
    private GitOperations gitOperations;

    private RetryHandler retryHandler;
    private RecoveryStrategy recoveryStrategy;

    @BeforeEach
    void setUp() {
        retryHandler = new RetryHandler();
        recoveryStrategy = new RecoveryStrategy(gitOperations, retryHandler);
    }

    @Nested
    @DisplayName("determineRecoveryAction")
    class DetermineRecoveryAction {

        @Test
        @DisplayName("returns ABORT when retries exhausted")
        void returnsAbortWhenRetriesExhausted() {
            HandoffState state = HandoffState.builder()
                    .retryCount(3)
                    .build();

            RecoveryStrategy.RecoveryAction action = recoveryStrategy.determineRecoveryAction(
                    ErrorType.COMPILATION, Phase.GREEN, state
            );

            assertThat(action).isEqualTo(RecoveryStrategy.RecoveryAction.ABORT);
        }

        @Test
        @DisplayName("returns RETRY_WITH_CONTEXT for compilation errors")
        void returnsRetryForCompilation() {
            HandoffState state = HandoffState.initial();

            RecoveryStrategy.RecoveryAction action = recoveryStrategy.determineRecoveryAction(
                    ErrorType.COMPILATION, Phase.GREEN, state
            );

            assertThat(action).isEqualTo(RecoveryStrategy.RecoveryAction.RETRY_WITH_CONTEXT);
        }

        @Test
        @DisplayName("returns CONTINUE for test failure in RED phase")
        void returnsContinueForTestFailureInRed() {
            HandoffState state = HandoffState.initial();

            RecoveryStrategy.RecoveryAction action = recoveryStrategy.determineRecoveryAction(
                    ErrorType.TEST_FAILURE, Phase.RED, state
            );

            assertThat(action).isEqualTo(RecoveryStrategy.RecoveryAction.CONTINUE);
        }

        @Test
        @DisplayName("returns ROLLBACK_AND_RETRY for test failure in GREEN phase")
        void returnsRollbackForTestFailureInGreen() {
            HandoffState state = HandoffState.initial();

            RecoveryStrategy.RecoveryAction action = recoveryStrategy.determineRecoveryAction(
                    ErrorType.TEST_FAILURE, Phase.GREEN, state
            );

            assertThat(action).isEqualTo(RecoveryStrategy.RecoveryAction.ROLLBACK_AND_RETRY);
        }

        @Test
        @DisplayName("returns ROLLBACK_AND_RETRY for test failure in REFACTOR phase")
        void returnsRollbackForTestFailureInRefactor() {
            HandoffState state = HandoffState.initial();

            RecoveryStrategy.RecoveryAction action = recoveryStrategy.determineRecoveryAction(
                    ErrorType.TEST_FAILURE, Phase.REFACTOR, state
            );

            assertThat(action).isEqualTo(RecoveryStrategy.RecoveryAction.ROLLBACK_AND_RETRY);
        }

        @Test
        @DisplayName("returns RETRY_WITH_CONTEXT for unexpected pass")
        void returnsRetryForUnexpectedPass() {
            HandoffState state = HandoffState.initial();

            RecoveryStrategy.RecoveryAction action = recoveryStrategy.determineRecoveryAction(
                    ErrorType.UNEXPECTED_PASS, Phase.RED, state
            );

            assertThat(action).isEqualTo(RecoveryStrategy.RecoveryAction.RETRY_WITH_CONTEXT);
        }

        @Test
        @DisplayName("returns WAIT_AND_RETRY for timeout")
        void returnsWaitForTimeout() {
            HandoffState state = HandoffState.initial();

            RecoveryStrategy.RecoveryAction action = recoveryStrategy.determineRecoveryAction(
                    ErrorType.TIMEOUT, Phase.GREEN, state
            );

            assertThat(action).isEqualTo(RecoveryStrategy.RecoveryAction.WAIT_AND_RETRY);
        }

        @Test
        @DisplayName("returns WAIT_AND_RETRY for rate limit")
        void returnsWaitForRateLimit() {
            HandoffState state = HandoffState.initial();

            RecoveryStrategy.RecoveryAction action = recoveryStrategy.determineRecoveryAction(
                    ErrorType.RATE_LIMIT, Phase.GREEN, state
            );

            assertThat(action).isEqualTo(RecoveryStrategy.RecoveryAction.WAIT_AND_RETRY);
        }

        @Test
        @DisplayName("returns WAIT_AND_RETRY for network error")
        void returnsWaitForNetwork() {
            HandoffState state = HandoffState.initial();

            RecoveryStrategy.RecoveryAction action = recoveryStrategy.determineRecoveryAction(
                    ErrorType.NETWORK, Phase.GREEN, state
            );

            assertThat(action).isEqualTo(RecoveryStrategy.RecoveryAction.WAIT_AND_RETRY);
        }
    }

    @Nested
    @DisplayName("executeRecovery")
    class ExecuteRecovery {

        @Test
        @DisplayName("CONTINUE returns success without modification")
        void continueReturnsSuccess() throws Exception {
            HandoffState state = HandoffState.initial();

            RecoveryStrategy.RecoveryResult result = recoveryStrategy.executeRecovery(
                    RecoveryStrategy.RecoveryAction.CONTINUE,
                    state, Phase.RED, ErrorType.TEST_FAILURE, "error", null
            );

            assertThat(result.shouldContinue()).isTrue();
            assertThat(result.updatedState()).isEqualTo(state);
        }

        @Test
        @DisplayName("RETRY_WITH_CONTEXT updates state with error info")
        void retryWithContextUpdatesState() throws Exception {
            HandoffState state = HandoffState.initial();

            RecoveryStrategy.RecoveryResult result = recoveryStrategy.executeRecovery(
                    RecoveryStrategy.RecoveryAction.RETRY_WITH_CONTEXT,
                    state, Phase.GREEN, ErrorType.COMPILATION, "[ERROR] syntax error", null
            );

            assertThat(result.shouldContinue()).isTrue();
            assertThat(result.updatedState().error()).contains("syntax error");
            assertThat(result.updatedState().retryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("ROLLBACK_AND_RETRY calls rollback and updates state")
        void rollbackAndRetryPerformsRollback() throws Exception {
            ObjectId commitId = ObjectId.fromString("a".repeat(40));
            HandoffState state = HandoffState.initial();

            RecoveryStrategy.RecoveryResult result = recoveryStrategy.executeRecovery(
                    RecoveryStrategy.RecoveryAction.ROLLBACK_AND_RETRY,
                    state, Phase.GREEN, ErrorType.TEST_FAILURE, "test failed", commitId
            );

            verify(gitOperations).rollbackToCommit(commitId);
            assertThat(result.shouldContinue()).isTrue();
            assertThat(result.updatedState().retryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("ROLLBACK_AND_RETRY continues even if rollback fails")
        void rollbackAndRetryContinuesOnRollbackFailure() throws Exception {
            ObjectId commitId = ObjectId.fromString("a".repeat(40));
            doThrow(new GitOperationException("rollback failed", null))
                    .when(gitOperations).rollbackToCommit(any());
            HandoffState state = HandoffState.initial();

            RecoveryStrategy.RecoveryResult result = recoveryStrategy.executeRecovery(
                    RecoveryStrategy.RecoveryAction.ROLLBACK_AND_RETRY,
                    state, Phase.GREEN, ErrorType.TEST_FAILURE, "test failed", commitId
            );

            assertThat(result.shouldContinue()).isTrue();
        }

        @Test
        @DisplayName("ABORT returns failure with abort message")
        void abortReturnsFailure() throws Exception {
            HandoffState state = HandoffState.builder()
                    .retryCount(3)
                    .build();

            RecoveryStrategy.RecoveryResult result = recoveryStrategy.executeRecovery(
                    RecoveryStrategy.RecoveryAction.ABORT,
                    state, Phase.GREEN, ErrorType.COMPILATION, "error", null
            );

            assertThat(result.shouldContinue()).isFalse();
            assertThat(result.message()).contains("aborted");
            assertThat(result.updatedState().error()).contains("aborted");
        }
    }

    @Nested
    @DisplayName("rollbackAndPrepareRetry")
    class RollbackAndPrepareRetry {

        @Test
        @DisplayName("performs rollback when commit provided")
        void performsRollback() throws Exception {
            ObjectId commitId = ObjectId.fromString("b".repeat(40));
            HandoffState state = HandoffState.initial();

            recoveryStrategy.rollbackAndPrepareRetry(state, ErrorType.TEST_FAILURE, "error", commitId);

            verify(gitOperations).rollbackToCommit(commitId);
        }

        @Test
        @DisplayName("skips rollback when commit is null")
        void skipsRollbackWhenNull() throws Exception {
            HandoffState state = HandoffState.initial();

            recoveryStrategy.rollbackAndPrepareRetry(state, ErrorType.TEST_FAILURE, "error", null);

            verify(gitOperations, never()).rollbackToCommit(any());
        }

        @Test
        @DisplayName("updates state with error information")
        void updatesStateWithError() {
            HandoffState state = HandoffState.initial();

            HandoffState updated = recoveryStrategy.rollbackAndPrepareRetry(
                    state, ErrorType.TEST_FAILURE, "[ERROR] assertion failed", null
            );

            assertThat(updated.error()).contains("assertion failed");
            assertThat(updated.retryCount()).isEqualTo(1);
            assertThat(updated.errorDetails().type()).isEqualTo("TEST_FAILURE");
        }
    }

    @Nested
    @DisplayName("recordAbort")
    class RecordAbort {

        @Test
        @DisplayName("records abort message in state")
        void recordsAbortMessage() {
            HandoffState state = HandoffState.builder()
                    .retryCount(3)
                    .build();

            HandoffState aborted = recoveryStrategy.recordAbort(state, ErrorType.COMPILATION, "final error");

            assertThat(aborted.error()).contains("aborted");
            assertThat(aborted.error()).contains("3 retries");
        }

        @Test
        @DisplayName("includes error type in abort details")
        void includesErrorType() {
            HandoffState state = HandoffState.builder()
                    .retryCount(3)
                    .build();

            HandoffState aborted = recoveryStrategy.recordAbort(state, ErrorType.TIMEOUT, "timed out");

            assertThat(aborted.errorDetails().type()).isEqualTo("ABORT_TIMEOUT");
        }
    }

    @Nested
    @DisplayName("findRollbackCommit")
    class FindRollbackCommit {

        @Test
        @DisplayName("returns latest commit from git operations")
        void returnsLatestCommit() throws Exception {
            ObjectId commitId = ObjectId.fromString("c".repeat(40));
            when(gitOperations.getLatestCommit()).thenReturn(Optional.of(commitId));

            Optional<ObjectId> result = recoveryStrategy.findRollbackCommit(Phase.GREEN);

            assertThat(result).contains(commitId);
        }

        @Test
        @DisplayName("returns empty when git operation fails")
        void returnsEmptyOnFailure() throws Exception {
            when(gitOperations.getLatestCommit()).thenThrow(new GitOperationException("failed", null));

            Optional<ObjectId> result = recoveryStrategy.findRollbackCommit(Phase.GREEN);

            assertThat(result).isEmpty();
        }
    }
}
