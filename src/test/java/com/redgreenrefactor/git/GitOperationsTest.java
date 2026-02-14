package com.redgreenrefactor.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitOperationsTest {

    @TempDir
    Path tempDir;

    private Git git;
    private GitOperations gitOps;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize a new Git repository
        git = Git.init().setDirectory(tempDir.toFile()).call();
        disableGpgSigning(git);

        // Create initial commit
        Files.writeString(tempDir.resolve("README.md"), "# Test Project");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Initial commit").call();

        gitOps = new GitOperations(tempDir);
    }

    @AfterEach
    void tearDown() {
        if (gitOps != null) {
            gitOps.close();
        }
        if (git != null) {
            git.close();
        }
    }

    @Test
    void commitChanges_createsCommitWithMessage() throws Exception {
        Files.writeString(tempDir.resolve("test.txt"), "test content");

        ObjectId commitId = gitOps.commitChanges("test: add test file");

        assertThat(commitId).isNotNull();

        String message = gitOps.getCommitMessage(commitId);
        assertThat(message).isEqualTo("test: add test file");
    }

    @Test
    void commitChanges_stagesUntrackedFiles() throws Exception {
        Files.writeString(tempDir.resolve("new-file.txt"), "new content");

        ObjectId commitId = gitOps.commitChanges("feat: add new file");

        List<String> changedFiles = gitOps.getChangedFiles(commitId);
        assertThat(changedFiles).contains("new-file.txt");
    }

    @Test
    void commitChanges_stagesModifiedFiles() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# Updated content");

        ObjectId commitId = gitOps.commitChanges("docs: update readme");

        List<String> changedFiles = gitOps.getChangedFiles(commitId);
        assertThat(changedFiles).contains("README.md");
    }

    @Test
    void commitChanges_stagesDeletedFiles() throws Exception {
        Files.delete(tempDir.resolve("README.md"));

        ObjectId commitId = gitOps.commitChanges("chore: remove readme");

        List<String> changedFiles = gitOps.getChangedFiles(commitId);
        assertThat(changedFiles).contains("README.md");
    }

    @Test
    void commitChanges_throwsOnNullMessage() {
        assertThatThrownBy(() -> gitOps.commitChanges(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void commitChanges_throwsOnBlankMessage() {
        assertThatThrownBy(() -> gitOps.commitChanges("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void getLatestCommit_returnsHeadCommit() throws Exception {
        Optional<ObjectId> latestCommit = gitOps.getLatestCommit();

        assertThat(latestCommit).isPresent();
        assertThat(latestCommit.get()).isEqualTo(git.getRepository().resolve("HEAD"));
    }

    @Test
    void getLatestCommit_returnsEmptyForEmptyRepo() throws Exception {
        // Create a new empty repository
        Path emptyRepoDir = tempDir.resolve("empty-repo");
        Files.createDirectories(emptyRepoDir);
        try (Git emptyGit = Git.init().setDirectory(emptyRepoDir.toFile()).call();
             GitOperations emptyOps = new GitOperations(emptyRepoDir)) {
            disableGpgSigning(emptyGit);

            Optional<ObjectId> latestCommit = emptyOps.getLatestCommit();

            assertThat(latestCommit).isEmpty();
        }
    }

    @Test
    void rollbackToCommit_resetsToSpecifiedCommit() throws Exception {
        // Get initial commit
        ObjectId initialCommit = git.getRepository().resolve("HEAD");

        // Create additional commits
        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Second commit").call();

        Files.writeString(tempDir.resolve("file2.txt"), "content2");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Third commit").call();

        // Verify files exist
        assertThat(tempDir.resolve("file1.txt")).exists();
        assertThat(tempDir.resolve("file2.txt")).exists();

        // Rollback to initial commit
        gitOps.rollbackToCommit(initialCommit);

        // Verify HEAD is at initial commit
        assertThat(git.getRepository().resolve("HEAD")).isEqualTo(initialCommit);

        // Verify files are gone
        assertThat(tempDir.resolve("file1.txt")).doesNotExist();
        assertThat(tempDir.resolve("file2.txt")).doesNotExist();
    }

    @Test
    void rollbackToCommit_throwsOnNullCommitId() {
        assertThatThrownBy(() -> gitOps.rollbackToCommit(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getCommitDiff_returnsUnifiedDiff() throws Exception {
        Files.writeString(tempDir.resolve("newfile.txt"), "line1\nline2\nline3\n");
        git.add().addFilepattern(".").call();
        RevCommit commit = git.commit().setMessage("Add newfile").call();

        String diff = gitOps.getCommitDiff(commit.getId());

        assertThat(diff).contains("diff --git");
        assertThat(diff).contains("newfile.txt");
        assertThat(diff).contains("+line1");
        assertThat(diff).contains("+line2");
        assertThat(diff).contains("+line3");
    }

    @Test
    void getCommitDiff_handlesFirstCommit() throws Exception {
        // Create a new repo with a single commit
        Path newRepoDir = tempDir.resolve("new-repo");
        Files.createDirectories(newRepoDir);
        try (Git newGit = Git.init().setDirectory(newRepoDir.toFile()).call()) {
            disableGpgSigning(newGit);
            Files.writeString(newRepoDir.resolve("first.txt"), "first content");
            newGit.add().addFilepattern(".").call();
            RevCommit firstCommit = newGit.commit().setMessage("First commit").call();

            try (GitOperations newOps = new GitOperations(newRepoDir)) {
                String diff = newOps.getCommitDiff(firstCommit.getId());

                assertThat(diff).contains("diff --git");
                assertThat(diff).contains("first.txt");
                assertThat(diff).contains("+first content");
            }
        }
    }

    @Test
    void getCommitDiff_showsModifications() throws Exception {
        // Modify the README
        Files.writeString(tempDir.resolve("README.md"), "# Modified Title\nNew content");
        git.add().addFilepattern(".").call();
        RevCommit commit = git.commit().setMessage("Update readme").call();

        String diff = gitOps.getCommitDiff(commit.getId());

        assertThat(diff).contains("diff --git");
        assertThat(diff).contains("README.md");
        assertThat(diff).contains("-# Test Project");
        assertThat(diff).contains("+# Modified Title");
    }

    @Test
    void getCommitDiff_throwsOnNullCommitId() {
        assertThatThrownBy(() -> gitOps.getCommitDiff(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getChangedFiles_returnsListOfPaths() throws Exception {
        Files.writeString(tempDir.resolve("file1.txt"), "content1");
        Files.writeString(tempDir.resolve("file2.txt"), "content2");
        git.add().addFilepattern(".").call();
        RevCommit commit = git.commit().setMessage("Add files").call();

        List<String> changedFiles = gitOps.getChangedFiles(commit.getId());

        assertThat(changedFiles).containsExactlyInAnyOrder("file1.txt", "file2.txt");
    }

    @Test
    void getChangedFiles_returnsOldPathForDeletes() throws Exception {
        Files.delete(tempDir.resolve("README.md"));
        git.add().addFilepattern(".").setUpdate(true).call();
        RevCommit commit = git.commit().setMessage("Delete readme").call();

        List<String> changedFiles = gitOps.getChangedFiles(commit.getId());

        assertThat(changedFiles).contains("README.md");
    }

    @Test
    void getChangedFiles_throwsOnNullCommitId() {
        assertThatThrownBy(() -> gitOps.getChangedFiles(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void hasUncommittedChanges_returnsFalseWhenClean() throws Exception {
        boolean hasChanges = gitOps.hasUncommittedChanges();

        assertThat(hasChanges).isFalse();
    }

    @Test
    void hasUncommittedChanges_returnsTrueForUntrackedFiles() throws Exception {
        Files.writeString(tempDir.resolve("untracked.txt"), "content");

        boolean hasChanges = gitOps.hasUncommittedChanges();

        assertThat(hasChanges).isTrue();
    }

    @Test
    void hasUncommittedChanges_returnsTrueForModifiedFiles() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "modified content");

        boolean hasChanges = gitOps.hasUncommittedChanges();

        assertThat(hasChanges).isTrue();
    }

    @Test
    void hasUncommittedChanges_returnsTrueForStagedFiles() throws Exception {
        Files.writeString(tempDir.resolve("staged.txt"), "content");
        git.add().addFilepattern(".").call();

        boolean hasChanges = gitOps.hasUncommittedChanges();

        assertThat(hasChanges).isTrue();
    }

    @Test
    void getCommitMessage_returnsFullMessage() throws Exception {
        String multilineMessage = "feat: add feature\n\nThis is the body of the commit.\nIt has multiple lines.";
        Files.writeString(tempDir.resolve("feature.txt"), "content");
        git.add().addFilepattern(".").call();
        RevCommit commit = git.commit().setMessage(multilineMessage).call();

        String message = gitOps.getCommitMessage(commit.getId());

        assertThat(message).isEqualTo(multilineMessage);
    }

    @Test
    void getCommitMessage_throwsOnNullCommitId() {
        assertThatThrownBy(() -> gitOps.getCommitMessage(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getRepository_returnsUnderlyingRepository() {
        assertThat(gitOps.getRepository()).isNotNull();
        assertThat(gitOps.getRepository().getDirectory())
                .isEqualTo(tempDir.resolve(".git").toFile());
    }

    @Test
    void getWorkingDirectory_returnsRepoWorkTree() {
        Path workDir = gitOps.getWorkingDirectory();

        assertThat(workDir).isEqualTo(tempDir);
    }

    @Test
    void constructor_throwsOnNullPath() {
        assertThatThrownBy(() -> new GitOperations((Path) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_throwsOnNonGitDirectory() throws IOException {
        // Create a temp directory outside of any git repository
        // Use a sibling of the system temp root to ensure no parent .git exists
        Path isolatedDir = Files.createTempDirectory("non-git-test");
        try {
            // JGit throws IllegalArgumentException when no git directory is found
            assertThatThrownBy(() -> new GitOperations(isolatedDir))
                    .isInstanceOfAny(IOException.class, IllegalArgumentException.class);
        } finally {
            Files.deleteIfExists(isolatedDir);
        }
    }

    @Test
    void commitChanges_handlesNestedDirectories() throws Exception {
        Path nestedDir = tempDir.resolve("src/main/java");
        Files.createDirectories(nestedDir);
        Files.writeString(nestedDir.resolve("App.java"), "public class App {}");

        ObjectId commitId = gitOps.commitChanges("feat: add nested file");

        List<String> changedFiles = gitOps.getChangedFiles(commitId);
        assertThat(changedFiles).contains("src/main/java/App.java");
    }

    private static void disableGpgSigning(Git git) throws IOException {
        var config = git.getRepository().getConfig();
        config.setBoolean("commit", null, "gpgsign", false);
        config.save();
    }
}
