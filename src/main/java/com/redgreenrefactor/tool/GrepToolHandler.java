package com.redgreenrefactor.tool;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Handler for the Grep tool.
 * Searches for text patterns in files using regular expressions.
 */
public class GrepToolHandler implements ToolExecutor {

    private final Path defaultDirectory;
    private static final int MAX_RESULTS = 100;

    public GrepToolHandler(Path defaultDirectory) {
        this.defaultDirectory = defaultDirectory;
    }

    @Override
    public String getToolName() {
        return "Grep";
    }

    @Override
    public ToolResult execute(Map<String, Object> inputs) throws ToolExecutionException {
        String patternStr = (String) inputs.get("pattern");
        String pathStr = (String) inputs.get("path");
        String globStr = (String) inputs.get("glob");

        if (patternStr == null || patternStr.isBlank()) {
            return ToolResult.failure("pattern is required");
        }

        Pattern regex;
        try {
            regex = Pattern.compile(patternStr);
        } catch (PatternSyntaxException e) {
            return ToolResult.failure("Invalid regex pattern: " + e.getMessage());
        }

        Path searchPath = pathStr != null && !pathStr.isBlank()
                ? Path.of(pathStr)
                : defaultDirectory;

        if (!Files.exists(searchPath)) {
            return ToolResult.failure("Path not found: " + searchPath);
        }

        try {
            List<GrepMatch> matches = searchFiles(searchPath, regex, globStr);

            if (matches.isEmpty()) {
                return ToolResult.success("No matches found for pattern: " + patternStr);
            }

            StringBuilder result = new StringBuilder();
            result.append("Found ").append(matches.size());
            if (matches.size() >= MAX_RESULTS) {
                result.append("+ (truncated)");
            }
            result.append(" match(es):\n\n");

            for (GrepMatch match : matches) {
                result.append(match.file())
                        .append(":")
                        .append(match.lineNumber())
                        .append(": ")
                        .append(match.line())
                        .append("\n");
            }
            return ToolResult.success(result.toString().trim());
        } catch (IOException e) {
            throw new ToolExecutionException("Failed to search files: " + e.getMessage(), e);
        }
    }

    private List<GrepMatch> searchFiles(Path searchPath, Pattern regex, String glob) throws IOException {
        List<GrepMatch> matches = new ArrayList<>();

        PathMatcher globMatcher = glob != null && !glob.isBlank()
                ? FileSystems.getDefault().getPathMatcher("glob:" + glob)
                : null;

        if (Files.isRegularFile(searchPath)) {
            searchFile(searchPath, regex, matches);
        } else {
            Files.walkFileTree(searchPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (matches.size() >= MAX_RESULTS) {
                        return FileVisitResult.TERMINATE;
                    }

                    // Skip binary files and hidden files
                    String fileName = file.getFileName().toString();
                    if (fileName.startsWith(".") || isBinaryFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }

                    // Apply glob filter if specified
                    if (globMatcher != null) {
                        Path relativePath = searchPath.relativize(file);
                        if (!globMatcher.matches(file.getFileName()) &&
                            !globMatcher.matches(relativePath)) {
                            return FileVisitResult.CONTINUE;
                        }
                    }

                    try {
                        searchFile(file, regex, matches);
                    } catch (IOException e) {
                        // Skip files we can't read
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Skip hidden directories and common non-source directories
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (dirName.startsWith(".") || dirName.equals("node_modules") ||
                        dirName.equals("target") || dirName.equals("build") ||
                        dirName.equals("dist") || dirName.equals("__pycache__")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return matches;
    }

    private void searchFile(Path file, Pattern regex, List<GrepMatch> matches) throws IOException {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size() && matches.size() < MAX_RESULTS; i++) {
            String line = lines.get(i);
            Matcher matcher = regex.matcher(line);
            if (matcher.find()) {
                matches.add(new GrepMatch(file.toString(), i + 1, line.trim()));
            }
        }
    }

    private boolean isBinaryFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.endsWith(".class") ||
               fileName.endsWith(".jar") ||
               fileName.endsWith(".war") ||
               fileName.endsWith(".ear") ||
               fileName.endsWith(".zip") ||
               fileName.endsWith(".tar") ||
               fileName.endsWith(".gz") ||
               fileName.endsWith(".png") ||
               fileName.endsWith(".jpg") ||
               fileName.endsWith(".jpeg") ||
               fileName.endsWith(".gif") ||
               fileName.endsWith(".ico") ||
               fileName.endsWith(".pdf") ||
               fileName.endsWith(".exe") ||
               fileName.endsWith(".dll") ||
               fileName.endsWith(".so") ||
               fileName.endsWith(".dylib");
    }

    private record GrepMatch(String file, int lineNumber, String line) {}
}
