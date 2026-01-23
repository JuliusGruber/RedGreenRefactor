package com.redgreenrefactor.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides Git operations for the TDD orchestrator.
 * Handles commits, resets, and diff operations.
 */
public class GitOperations implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(GitOperations.class);

    private final Repository repository;
    private final Git git;

    /**
     * Creates GitOperations for the repository at the specified path.
     *
     * @param repositoryPath Path to the Git repository (or any directory within it)
     * @throws IOException If the repository cannot be opened
     */
    public GitOperations(Path repositoryPath) throws IOException {
        Objects.requireNonNull(repositoryPath, "repositoryPath must not be null");

        this.repository = new FileRepositoryBuilder()
                .findGitDir(repositoryPath.toFile())
                .setMustExist(true)
                .build();
        this.git = new Git(repository);

        logger.debug("Opened Git repository at {}", repository.getDirectory());
    }

    /**
     * Creates GitOperations for an existing Repository instance.
     *
     * @param repository The Git repository
     */
    public GitOperations(Repository repository) {
        Objects.requireNonNull(repository, "repository must not be null");

        this.repository = repository;
        this.git = new Git(repository);
    }

    /**
     * Stages all changes and creates a commit.
     *
     * @param message The commit message
     * @return The ID of the created commit
     * @throws GitOperationException If the commit fails
     */
    public ObjectId commitChanges(String message) throws GitOperationException {
        Objects.requireNonNull(message, "message must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }

        try {
            // Stage all changes (including untracked files)
            git.add()
                    .addFilepattern(".")
                    .call();

            // Also stage deletions
            git.add()
                    .addFilepattern(".")
                    .setUpdate(true)
                    .call();

            // Create commit
            RevCommit commit = git.commit()
                    .setMessage(message)
                    .call();

            logger.info("Created commit {} with message: {}",
                    commit.abbreviate(7).name(), message.lines().findFirst().orElse(""));

            return commit.getId();

        } catch (GitAPIException e) {
            throw new GitOperationException("Failed to commit changes: " + message, e);
        }
    }

    /**
     * Gets the current HEAD commit.
     *
     * @return The HEAD commit ID, or empty if the repository has no commits
     * @throws GitOperationException If the operation fails
     */
    public Optional<ObjectId> getLatestCommit() throws GitOperationException {
        try {
            Ref head = repository.exactRef("HEAD");
            if (head == null || head.getObjectId() == null) {
                logger.debug("Repository has no commits");
                return Optional.empty();
            }

            ObjectId commitId = head.getObjectId();
            logger.debug("Latest commit: {}", commitId.abbreviate(7).name());
            return Optional.of(commitId);

        } catch (IOException e) {
            throw new GitOperationException("Failed to get latest commit", e);
        }
    }

    /**
     * Resets the repository to the specified commit (hard reset).
     * This discards all changes after the specified commit.
     *
     * @param commitId The commit to reset to
     * @throws GitOperationException If the reset fails
     */
    public void rollbackToCommit(ObjectId commitId) throws GitOperationException {
        Objects.requireNonNull(commitId, "commitId must not be null");

        try {
            git.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef(commitId.name())
                    .call();

            logger.info("Reset repository to commit {}", commitId.abbreviate(7).name());

        } catch (GitAPIException e) {
            throw new GitOperationException("Failed to rollback to commit " + commitId.name(), e);
        }
    }

    /**
     * Gets the diff for a specific commit (changes introduced by the commit).
     *
     * @param commitId The commit to get the diff for
     * @return The diff as a unified diff string
     * @throws GitOperationException If the diff cannot be generated
     */
    public String getCommitDiff(ObjectId commitId) throws GitOperationException {
        Objects.requireNonNull(commitId, "commitId must not be null");

        try (RevWalk revWalk = new RevWalk(repository);
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             DiffFormatter diffFormatter = new DiffFormatter(out)) {

            RevCommit commit = revWalk.parseCommit(commitId);

            diffFormatter.setRepository(repository);
            diffFormatter.setDetectRenames(true);

            AbstractTreeIterator oldTreeIter;
            if (commit.getParentCount() > 0) {
                RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
                oldTreeIter = prepareTreeParser(parent);
            } else {
                // First commit - compare against empty tree
                oldTreeIter = new EmptyTreeIterator();
            }

            AbstractTreeIterator newTreeIter = prepareTreeParser(commit);

            List<DiffEntry> diffs = diffFormatter.scan(oldTreeIter, newTreeIter);
            for (DiffEntry entry : diffs) {
                diffFormatter.format(entry);
            }

            String diff = out.toString(StandardCharsets.UTF_8);
            logger.debug("Generated diff for commit {} ({} bytes)",
                    commitId.abbreviate(7).name(), diff.length());

            return diff;

        } catch (IOException e) {
            throw new GitOperationException("Failed to get diff for commit " + commitId.name(), e);
        }
    }

    /**
     * Gets a list of files changed in a specific commit.
     *
     * @param commitId The commit to analyze
     * @return List of changed file paths
     * @throws GitOperationException If the operation fails
     */
    public List<String> getChangedFiles(ObjectId commitId) throws GitOperationException {
        Objects.requireNonNull(commitId, "commitId must not be null");

        try (RevWalk revWalk = new RevWalk(repository);
             DiffFormatter diffFormatter = new DiffFormatter(null)) {

            RevCommit commit = revWalk.parseCommit(commitId);

            diffFormatter.setRepository(repository);
            diffFormatter.setDetectRenames(true);

            AbstractTreeIterator oldTreeIter;
            if (commit.getParentCount() > 0) {
                RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
                oldTreeIter = prepareTreeParser(parent);
            } else {
                oldTreeIter = new EmptyTreeIterator();
            }

            AbstractTreeIterator newTreeIter = prepareTreeParser(commit);

            List<DiffEntry> diffs = diffFormatter.scan(oldTreeIter, newTreeIter);

            return diffs.stream()
                    .map(entry -> entry.getChangeType() == DiffEntry.ChangeType.DELETE
                            ? entry.getOldPath()
                            : entry.getNewPath())
                    .toList();

        } catch (IOException e) {
            throw new GitOperationException("Failed to get changed files for commit " + commitId.name(), e);
        }
    }

    /**
     * Checks if the working directory has uncommitted changes.
     *
     * @return true if there are uncommitted changes
     * @throws GitOperationException If the check fails
     */
    public boolean hasUncommittedChanges() throws GitOperationException {
        try {
            var status = git.status().call();
            boolean hasChanges = !status.isClean();

            logger.debug("Working directory has uncommitted changes: {}", hasChanges);
            return hasChanges;

        } catch (GitAPIException e) {
            throw new GitOperationException("Failed to check for uncommitted changes", e);
        }
    }

    /**
     * Gets the commit message for a specific commit.
     *
     * @param commitId The commit to get the message from
     * @return The full commit message
     * @throws GitOperationException If the operation fails
     */
    public String getCommitMessage(ObjectId commitId) throws GitOperationException {
        Objects.requireNonNull(commitId, "commitId must not be null");

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            return commit.getFullMessage();

        } catch (IOException e) {
            throw new GitOperationException("Failed to get commit message for " + commitId.name(), e);
        }
    }

    private AbstractTreeIterator prepareTreeParser(RevCommit commit) throws IOException {
        RevTree tree = commit.getTree();
        try (ObjectReader reader = repository.newObjectReader()) {
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            treeParser.reset(reader, tree.getId());
            return treeParser;
        }
    }

    /**
     * Returns the underlying Git repository.
     *
     * @return The Git repository
     */
    public Repository getRepository() {
        return repository;
    }

    /**
     * Returns the working directory of the repository.
     *
     * @return The working directory path
     */
    public Path getWorkingDirectory() {
        return repository.getWorkTree().toPath();
    }

    @Override
    public void close() {
        git.close();
        repository.close();
    }
}
