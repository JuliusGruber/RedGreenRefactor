package com.redgreenrefactor.git;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.redgreenrefactor.model.HandoffState;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages Git Notes for TDD handoff coordination.
 * Uses the namespace {@code refs/notes/tdd-handoffs} to store handoff state as JSON.
 */
public class GitNotesManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(GitNotesManager.class);

    /**
     * The Git notes reference namespace for TDD handoffs.
     */
    public static final String NOTES_REF = "refs/notes/tdd-handoffs";

    private final Repository repository;
    private final Git git;
    private final ObjectMapper objectMapper;

    /**
     * Creates a GitNotesManager for the repository at the specified path.
     *
     * @param repositoryPath Path to the Git repository (or any directory within it)
     * @throws IOException If the repository cannot be opened
     */
    public GitNotesManager(Path repositoryPath) throws IOException {
        Objects.requireNonNull(repositoryPath, "repositoryPath must not be null");

        this.repository = new FileRepositoryBuilder()
                .findGitDir(repositoryPath.toFile())
                .setMustExist(true)
                .build();
        this.git = new Git(repository);
        this.objectMapper = createObjectMapper();

        logger.debug("Opened Git repository at {}", repository.getDirectory());
    }

    /**
     * Creates a GitNotesManager for an existing Repository instance.
     * The repository will NOT be closed when this manager is closed.
     *
     * @param repository The Git repository
     */
    public GitNotesManager(Repository repository) {
        Objects.requireNonNull(repository, "repository must not be null");

        this.repository = repository;
        this.git = new Git(repository);
        this.objectMapper = createObjectMapper();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    /**
     * Writes a handoff state as a Git Note attached to the specified commit.
     * If a note already exists for this commit, it will be overwritten.
     *
     * @param commitId The commit to attach the note to
     * @param state    The handoff state to write
     * @throws GitNotesException If the note cannot be written
     */
    public void writeHandoff(ObjectId commitId, HandoffState state) throws GitNotesException {
        Objects.requireNonNull(commitId, "commitId must not be null");
        Objects.requireNonNull(state, "state must not be null");

        try {
            String json = objectMapper.writeValueAsString(state);
            RevCommit revCommit = getRevCommit(commitId);

            // Remove existing note if present (JGit doesn't support force overwrite)
            Note existing = git.notesShow()
                    .setNotesRef(NOTES_REF)
                    .setObjectId(revCommit)
                    .call();
            if (existing != null) {
                git.notesRemove()
                        .setNotesRef(NOTES_REF)
                        .setObjectId(revCommit)
                        .call();
                logger.debug("Removed existing note from commit {}", commitId.abbreviate(7).name());
            }

            // Add the new note
            git.notesAdd()
                    .setNotesRef(NOTES_REF)
                    .setObjectId(revCommit)
                    .setMessage(json)
                    .call();

            logger.info("Wrote handoff note to commit {} (phase: {})",
                    commitId.abbreviate(7).name(), state.phase());

        } catch (IOException | GitAPIException e) {
            throw new GitNotesException("Failed to write handoff note to commit " + commitId.name(), e);
        }
    }

    /**
     * Reads a handoff state from the Git Note attached to the specified commit.
     *
     * @param commitId The commit to read the note from
     * @return The handoff state, or empty if no note exists
     * @throws GitNotesException If the note cannot be read or parsed
     */
    public Optional<HandoffState> readHandoff(ObjectId commitId) throws GitNotesException {
        Objects.requireNonNull(commitId, "commitId must not be null");

        try {
            Note note = git.notesShow()
                    .setNotesRef(NOTES_REF)
                    .setObjectId(getRevCommit(commitId))
                    .call();

            if (note == null) {
                logger.debug("No handoff note found for commit {}", commitId.abbreviate(7).name());
                return Optional.empty();
            }

            String json = readNoteContent(note);
            HandoffState state = objectMapper.readValue(json, HandoffState.class);

            logger.debug("Read handoff note from commit {} (phase: {})",
                    commitId.abbreviate(7).name(), state.phase());

            return Optional.of(state);

        } catch (IOException | GitAPIException e) {
            throw new GitNotesException("Failed to read handoff note from commit " + commitId.name(), e);
        }
    }

    /**
     * Finds the most recent commit that has a handoff note.
     *
     * @return The commit ID and handoff state, or empty if no handoffs exist
     * @throws GitNotesException If the search fails
     */
    public Optional<HandoffEntry> findLatestHandoff() throws GitNotesException {
        try {
            Ref head = repository.exactRef("HEAD");
            if (head == null || head.getObjectId() == null) {
                logger.debug("Repository has no commits");
                return Optional.empty();
            }

            try (RevWalk revWalk = new RevWalk(repository)) {
                revWalk.markStart(revWalk.parseCommit(head.getObjectId()));

                for (RevCommit commit : revWalk) {
                    Optional<HandoffState> state = readHandoff(commit.getId());
                    if (state.isPresent()) {
                        logger.info("Found latest handoff at commit {} (phase: {})",
                                commit.abbreviate(7).name(), state.get().phase());
                        return Optional.of(new HandoffEntry(commit.getId(), state.get()));
                    }
                }
            }

            logger.debug("No handoff notes found in commit history");
            return Optional.empty();

        } catch (IOException e) {
            throw new GitNotesException("Failed to search for latest handoff", e);
        }
    }

    /**
     * Lists all commits that have handoff notes, ordered from most recent to oldest.
     *
     * @return List of commit IDs and their handoff states
     * @throws GitNotesException If the list cannot be retrieved
     */
    public List<HandoffEntry> listAllHandoffs() throws GitNotesException {
        List<HandoffEntry> entries = new ArrayList<>();

        try {
            Ref notesRef = repository.exactRef(NOTES_REF);
            if (notesRef == null) {
                logger.debug("No notes ref exists yet");
                return entries;
            }

            try (RevWalk revWalk = new RevWalk(repository);
                 ObjectReader reader = repository.newObjectReader()) {

                RevCommit notesCommit = revWalk.parseCommit(notesRef.getObjectId());
                NoteMap noteMap = NoteMap.read(reader, notesCommit);

                // Get all commits with notes
                Ref head = repository.exactRef("HEAD");
                if (head == null || head.getObjectId() == null) {
                    return entries;
                }

                revWalk.reset();
                revWalk.markStart(revWalk.parseCommit(head.getObjectId()));

                for (RevCommit commit : revWalk) {
                    Note note = noteMap.getNote(commit);
                    if (note != null) {
                        String json = readNoteContent(note);
                        HandoffState state = objectMapper.readValue(json, HandoffState.class);
                        entries.add(new HandoffEntry(commit.getId(), state));
                    }
                }
            }

            logger.debug("Found {} handoff notes in history", entries.size());
            return entries;

        } catch (IOException e) {
            throw new GitNotesException("Failed to list handoff notes", e);
        }
    }

    /**
     * Removes the handoff note from the specified commit.
     *
     * @param commitId The commit to remove the note from
     * @return true if a note was removed, false if no note existed
     * @throws GitNotesException If the removal fails
     */
    public boolean removeHandoff(ObjectId commitId) throws GitNotesException {
        Objects.requireNonNull(commitId, "commitId must not be null");

        try {
            RevCommit revCommit = getRevCommit(commitId);

            Note existing = git.notesShow()
                    .setNotesRef(NOTES_REF)
                    .setObjectId(revCommit)
                    .call();

            if (existing == null) {
                return false;
            }

            git.notesRemove()
                    .setNotesRef(NOTES_REF)
                    .setObjectId(revCommit)
                    .call();

            logger.info("Removed handoff note from commit {}", commitId.abbreviate(7).name());
            return true;

        } catch (IOException | GitAPIException e) {
            throw new GitNotesException("Failed to remove handoff note from commit " + commitId.name(), e);
        }
    }

    private RevCommit getRevCommit(ObjectId commitId) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            return revWalk.parseCommit(commitId);
        }
    }

    private String readNoteContent(Note note) throws IOException {
        try (ObjectReader reader = repository.newObjectReader()) {
            ObjectLoader loader = reader.open(note.getData());
            return new String(loader.getBytes(), StandardCharsets.UTF_8);
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

    @Override
    public void close() {
        git.close();
        repository.close();
    }

    /**
     * Represents a commit with its associated handoff state.
     */
    public record HandoffEntry(ObjectId commitId, HandoffState state) {
        public HandoffEntry {
            Objects.requireNonNull(commitId, "commitId must not be null");
            Objects.requireNonNull(state, "state must not be null");
        }
    }
}
