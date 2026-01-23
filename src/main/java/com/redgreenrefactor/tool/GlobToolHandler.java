package com.redgreenrefactor.tool;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handler for the Glob tool.
 * Finds files matching a glob pattern.
 */
public class GlobToolHandler implements ToolExecutor {

    private final Path defaultDirectory;

    public GlobToolHandler(Path defaultDirectory) {
        this.defaultDirectory = defaultDirectory;
    }

    @Override
    public String getToolName() {
        return "Glob";
    }

    @Override
    public ToolResult execute(Map<String, Object> inputs) throws ToolExecutionException {
        String pattern = (String) inputs.get("pattern");
        String pathStr = (String) inputs.get("path");

        if (pattern == null || pattern.isBlank()) {
            return ToolResult.failure("pattern is required");
        }

        Path searchPath = resolveSearchPath(pathStr);

        if (!Files.exists(searchPath)) {
            return ToolResult.failure("Directory not found: " + searchPath);
        }

        try {
            List<String> matches = findMatches(searchPath, pattern);

            if (matches.isEmpty()) {
                return ToolResult.success("No files found matching pattern: " + pattern);
            }

            StringBuilder result = new StringBuilder();
            result.append("Found ").append(matches.size()).append(" file(s):\n");
            for (String match : matches) {
                result.append(match).append("\n");
            }
            return ToolResult.success(result.toString().trim());
        } catch (IOException e) {
            throw new ToolExecutionException("Failed to search for files with pattern: " + pattern, e);
        }
    }

    private List<String> findMatches(Path searchPath, String pattern) throws IOException {
        List<String> matches = new ArrayList<>();

        // Normalize pattern for PathMatcher
        String normalizedPattern = pattern;
        if (!pattern.startsWith("glob:") && !pattern.startsWith("regex:")) {
            normalizedPattern = "glob:" + pattern;
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(normalizedPattern);

        Files.walkFileTree(searchPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path relativePath = searchPath.relativize(file);
                if (matcher.matches(relativePath)) {
                    matches.add(file.toString());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // Skip files we can't access
                return FileVisitResult.CONTINUE;
            }
        });

        return matches;
    }

    private Path resolveSearchPath(String pathStr) {
        if (pathStr != null && !pathStr.isBlank()) {
            return Path.of(pathStr);
        }
        return defaultDirectory;
    }
}
