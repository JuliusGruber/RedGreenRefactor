# CLI Specification

## Overview

Simple command-line interface using [Picocli](https://picocli.info/) to start TDD workflows.

## Maven Dependency

```xml
<dependency>
    <groupId>info.picocli</groupId>
    <artifactId>picocli</artifactId>
    <version>4.7.6</version>
</dependency>
```

## Commands

### Start TDD with inline description

```bash
tdd "feature description"
```

Starts the TDD workflow with the provided feature description.

**Example:**
```bash
tdd "Add user authentication with JWT tokens"
```

### Start TDD from markdown file

```bash
tdd path/to/feature.md
```

Starts the TDD workflow using the feature description from the specified markdown file.

**Example:**
```bash
tdd features/user-auth.md
```

## Implementation

```java
@Command(name = "tdd",
         description = "Start a TDD workflow",
         mixinStandardHelpOptions = true)
public class TddCommand implements Runnable {

    @Parameters(index = "0",
                description = "Feature description or path to .md file")
    private String input;

    @Override
    public void run() {
        String featureDescription = resolveInput(input);
        // Start TDD workflow with featureDescription
    }

    private String resolveInput(String input) {
        if (input.endsWith(".md")) {
            return readFile(input);
        }
        return input;
    }
}
```

## Behavior

1. If input ends with `.md` → read file contents as feature description
2. Otherwise → use input directly as feature description
3. Pass feature description to Test List Agent to start the workflow
