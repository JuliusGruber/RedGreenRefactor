package com.redgreenrefactor.git;

import com.redgreenrefactor.model.ErrorDetails;
import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.Phase;
import com.redgreenrefactor.model.TestCase;
import com.redgreenrefactor.model.TestResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitNotesManagerTest {

    @TempDir
    Path tempDir;

    private Git git;
    private GitNotesManager notesManager;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize a new Git repository in the temp directory
        git = Git.init().setDirectory(tempDir.toFile()).call();
        disableGpgSigning(git);

        // Create an initial commit so we have something to attach notes to
        Files.writeString(tempDir.resolve("README.md"), "# Test Project");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Initial commit").call();

        notesManager = new GitNotesManager(tempDir);
    }

    @AfterEach
    void tearDown() {
        if (notesManager != null) {
            notesManager.close();
        }
        if (git != null) {
            git.close();
        }
    }

    @Test
    void writeHandoff_createsNoteOnCommit() throws Exception {
        ObjectId commitId = git.getRepository().resolve("HEAD");
        HandoffState state = HandoffState.initial();

        notesManager.writeHandoff(commitId, state);

        // Verify by reading it back
        Optional<HandoffState> retrieved = notesManager.readHandoff(commitId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().phase()).isEqualTo(Phase.PLAN);
    }

    @Test
    void writeHandoff_overwritesExistingNote() throws Exception {
        ObjectId commitId = git.getRepository().resolve("HEAD");

        // Write first state
        HandoffState state1 = HandoffState.builder()
                .phase(Phase.PLAN)
                .cycleNumber(1)
                .build();
        notesManager.writeHandoff(commitId, state1);

        // Overwrite with second state
        HandoffState state2 = HandoffState.builder()
                .phase(Phase.RED)
                .cycleNumber(2)
                .build();
        notesManager.writeHandoff(commitId, state2);

        // Verify only the second state exists
        Optional<HandoffState> retrieved = notesManager.readHandoff(commitId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().phase()).isEqualTo(Phase.RED);
        assertThat(retrieved.get().cycleNumber()).isEqualTo(2);
    }

    @Test
    void readHandoff_returnsEmptyForCommitWithoutNote() throws Exception {
        ObjectId commitId = git.getRepository().resolve("HEAD");

        Optional<HandoffState> retrieved = notesManager.readHandoff(commitId);

        assertThat(retrieved).isEmpty();
    }

    @Test
    void readHandoff_deserializesCompleteState() throws Exception {
        ObjectId commitId = git.getRepository().resolve("HEAD");

        TestCase testCase = new TestCase(
                "should add two numbers",
                "src/test/java/CalculatorTest.java",
                "src/main/java/Calculator.java"
        );

        HandoffState state = HandoffState.builder()
                .phase(Phase.GREEN)
                .nextPhase(Phase.REFACTOR)
                .cycleNumber(3)
                .currentTest(testCase)
                .completedTests(List.of("test1", "test2"))
                .pendingTests(List.of("test3", "test4"))
                .testResult(TestResult.FAIL)
                .error("Test failed")
                .errorDetails(new ErrorDetails("TestFailure", "Expected 4 but got 5"))
                .retryCount(1)
                .build();

        notesManager.writeHandoff(commitId, state);

        Optional<HandoffState> retrieved = notesManager.readHandoff(commitId);
        assertThat(retrieved).isPresent();
        HandoffState actual = retrieved.get();

        assertThat(actual.phase()).isEqualTo(Phase.GREEN);
        assertThat(actual.nextPhase()).isEqualTo(Phase.REFACTOR);
        assertThat(actual.cycleNumber()).isEqualTo(3);
        assertThat(actual.currentTest()).isEqualTo(testCase);
        assertThat(actual.completedTests()).containsExactly("test1", "test2");
        assertThat(actual.pendingTests()).containsExactly("test3", "test4");
        assertThat(actual.testResult()).isEqualTo(TestResult.FAIL);
        assertThat(actual.error()).isEqualTo("Test failed");
        assertThat(actual.errorDetails().type()).isEqualTo("TestFailure");
        assertThat(actual.errorDetails().message()).isEqualTo("Expected 4 but got 5");
        assertThat(actual.retryCount()).isEqualTo(1);
    }

    @Test
    void findLatestHandoff_returnsEmptyWhenNoNotes() throws Exception {
        Optional<GitNotesManager.HandoffEntry> latest = notesManager.findLatestHandoff();

        assertThat(latest).isEmpty();
    }

    @Test
    void findLatestHandoff_returnsMostRecentCommitWithNote() throws Exception {
        // Create multiple commits
        ObjectId commit1 = git.getRepository().resolve("HEAD");

        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        git.add().addFilepattern(".").call();
        RevCommit revCommit2 = git.commit().setMessage("Second commit").call();
        ObjectId commit2 = revCommit2.getId();

        Files.writeString(tempDir.resolve("file2.txt"), "content2");
        git.add().addFilepattern(".").call();
        RevCommit revCommit3 = git.commit().setMessage("Third commit").call();
        ObjectId commit3 = revCommit3.getId();

        // Add notes to first and third commits (not second)
        notesManager.writeHandoff(commit1, HandoffState.builder()
                .phase(Phase.PLAN)
                .cycleNumber(1)
                .build());

        notesManager.writeHandoff(commit3, HandoffState.builder()
                .phase(Phase.GREEN)
                .cycleNumber(2)
                .build());

        // Find latest should return commit3's note
        Optional<GitNotesManager.HandoffEntry> latest = notesManager.findLatestHandoff();

        assertThat(latest).isPresent();
        assertThat(latest.get().commitId()).isEqualTo(commit3);
        assertThat(latest.get().state().phase()).isEqualTo(Phase.GREEN);
        assertThat(latest.get().state().cycleNumber()).isEqualTo(2);
    }

    @Test
    void listAllHandoffs_returnsEmptyListWhenNoNotes() throws Exception {
        List<GitNotesManager.HandoffEntry> entries = notesManager.listAllHandoffs();

        assertThat(entries).isEmpty();
    }

    @Test
    void listAllHandoffs_returnsAllNotesInOrder() throws Exception {
        // Create multiple commits with notes
        ObjectId commit1 = git.getRepository().resolve("HEAD");

        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        git.add().addFilepattern(".").call();
        RevCommit revCommit2 = git.commit().setMessage("Second commit").call();
        ObjectId commit2 = revCommit2.getId();

        Files.writeString(tempDir.resolve("file2.txt"), "content2");
        git.add().addFilepattern(".").call();
        RevCommit revCommit3 = git.commit().setMessage("Third commit").call();
        ObjectId commit3 = revCommit3.getId();

        // Add notes to all commits
        notesManager.writeHandoff(commit1, HandoffState.builder()
                .phase(Phase.PLAN)
                .cycleNumber(1)
                .build());

        notesManager.writeHandoff(commit2, HandoffState.builder()
                .phase(Phase.RED)
                .cycleNumber(1)
                .build());

        notesManager.writeHandoff(commit3, HandoffState.builder()
                .phase(Phase.GREEN)
                .cycleNumber(1)
                .build());

        List<GitNotesManager.HandoffEntry> entries = notesManager.listAllHandoffs();

        assertThat(entries).hasSize(3);
        // Should be in reverse chronological order (newest first)
        assertThat(entries.get(0).state().phase()).isEqualTo(Phase.GREEN);
        assertThat(entries.get(1).state().phase()).isEqualTo(Phase.RED);
        assertThat(entries.get(2).state().phase()).isEqualTo(Phase.PLAN);
    }

    @Test
    void removeHandoff_removesExistingNote() throws Exception {
        ObjectId commitId = git.getRepository().resolve("HEAD");
        notesManager.writeHandoff(commitId, HandoffState.initial());

        boolean removed = notesManager.removeHandoff(commitId);

        assertThat(removed).isTrue();
        assertThat(notesManager.readHandoff(commitId)).isEmpty();
    }

    @Test
    void removeHandoff_returnsFalseWhenNoNoteExists() throws Exception {
        ObjectId commitId = git.getRepository().resolve("HEAD");

        boolean removed = notesManager.removeHandoff(commitId);

        assertThat(removed).isFalse();
    }

    @Test
    void writeHandoff_throwsOnNullCommitId() {
        assertThatThrownBy(() -> notesManager.writeHandoff(null, HandoffState.initial()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("commitId");
    }

    @Test
    void writeHandoff_throwsOnNullState() throws Exception {
        ObjectId commitId = git.getRepository().resolve("HEAD");

        assertThatThrownBy(() -> notesManager.writeHandoff(commitId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("state");
    }

    @Test
    void readHandoff_throwsOnNullCommitId() {
        assertThatThrownBy(() -> notesManager.readHandoff(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("commitId");
    }

    @Test
    void notesAreVisibleFromCLI() throws Exception {
        ObjectId commitId = git.getRepository().resolve("HEAD");
        HandoffState state = HandoffState.initial();

        notesManager.writeHandoff(commitId, state);

        // Verify using git notes CLI command
        ProcessBuilder pb = new ProcessBuilder(
                "git", "notes", "--ref=" + GitNotesManager.NOTES_REF, "show", "HEAD"
        );
        pb.directory(tempDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        int exitCode = process.waitFor();

        assertThat(exitCode).isEqualTo(0);
        assertThat(output.toString()).contains("\"phase\"");
        assertThat(output.toString()).contains("PLAN");
    }

    @Test
    void constructor_throwsOnNullPath() {
        assertThatThrownBy(() -> new GitNotesManager((Path) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsOnNonGitDirectory() throws IOException {
        // Create a temp directory outside of any git repository
        // Use a sibling of the system temp root to ensure no parent .git exists
        Path isolatedDir = Files.createTempDirectory("non-git-test");
        try {
            // JGit throws IllegalArgumentException when no git directory is found
            assertThatThrownBy(() -> new GitNotesManager(isolatedDir))
                    .isInstanceOfAny(IOException.class, IllegalArgumentException.class);
        } finally {
            Files.deleteIfExists(isolatedDir);
        }
    }

    @Test
    void getRepository_returnsUnderlyingRepository() {
        assertThat(notesManager.getRepository()).isNotNull();
        assertThat(notesManager.getRepository().getDirectory())
                .isEqualTo(tempDir.resolve(".git").toFile());
    }

    @Test
    void handoffEntry_validatesNullCommitId() {
        HandoffState state = HandoffState.initial();

        assertThatThrownBy(() -> new GitNotesManager.HandoffEntry(null, state))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("commitId");
    }

    @Test
    void handoffEntry_validatesNullState() throws Exception {
        ObjectId commitId = git.getRepository().resolve("HEAD");

        assertThatThrownBy(() -> new GitNotesManager.HandoffEntry(commitId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("state");
    }

    private static void disableGpgSigning(Git git) throws IOException {
        var config = git.getRepository().getConfig();
        config.setBoolean("commit", null, "gpgsign", false);
        config.save();
    }
}
