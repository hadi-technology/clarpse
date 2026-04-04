# Incremental Parsing Architecture

## Overview

The incremental parsing feature allows Clarpse to update an existing code model by re-parsing only changed files, rather than re-parsing the entire codebase. This provides significant performance improvements for scenarios where only a small subset of files change between analysis runs.

## Core Components

### 1. OOPSourceCodeModel Enhancements

The `OOPSourceCodeModel` class has been enhanced with file-to-component indexing:

```java
// File-to-component index for efficient file-based operations
private final Map<String, Set<String>> componentsByFile = new HashMap<>();
```

**Key Methods:**
- `getComponentDirect(String componentName)` - Returns component without defensive copy (read-only)
- `removeComponentsForFile(String sourceFile)` - Removes all components from a file
- `getComponentNamesForFile(String sourceFile)` - Returns component names for a file
- `sourceFiles()` - Returns all source files in the model

**Design Decision:** The `getComponentDirect()` method returns the internal component instance without copying. This is safe for read-only operations and provides significant performance benefits in hot paths like relationship extraction.

### 2. ClarpseProject.updateModel()

The primary API for incremental updates:

```java
public static CompileResult updateModel(
    OOPSourceCodeModel baseModel,
    Map<String, String> changedFiles,
    Set<String> deletedFiles,
    Lang lang
) throws CompileException
```

**Algorithm:**
1. **Deep-copy base model** - Ensures original model is unmodified
2. **Remove affected components** - Remove all components from changed + deleted files
3. **Parse changed files** - Parse only the changed files using the language-specific compiler
4. **Merge new components** - Merge newly parsed components into the updated model
5. **Re-classify references** - Re-classify internal/external references for affected components against the FULL model

**Critical Design Note:** Reference classification is performed against the FULL updated model, not a sub-model. This ensures that references to unchanged components are correctly marked as internal.

### 3. Reference Re-classification

When files are modified, components that reference changed components must have their references re-classified:

```java
private static Set<String> findReferrersTo(
    OOPSourceCodeModel currentModel,
    OOPSourceCodeModel previousModel,
    Set<String> targetComponentNames
) {
    return previousModel.components()
        .filter(cmp -> cmp.references().stream()
            .anyMatch(ref -> targetComponentNames.contains(ref.invokedComponent())))
        .map(Component::uniqueName)
        .filter(currentModel::containsComponent)
        .collect(Collectors.toSet());
}
```

## Daemon Reuse

### TypeScript and Python Daemons

Both TypeScript and Python compilers now maintain shared daemon instances:

```java
private static TypeScriptDaemon sharedDaemon = null;
private static final Object DAEMON_LOCK = new Object();
private static volatile long lastUsedTimestamp = 0;

private static TypeScriptDaemon acquireDaemon() throws TypeScriptDaemonException {
    synchronized (DAEMON_LOCK) {
        if (sharedDaemon != null) {
            lastUsedTimestamp = System.currentTimeMillis();
            return sharedDaemon;
        }
        TypeScriptDaemon daemon = new TypeScriptDaemon();
        daemon.start();
        sharedDaemon = daemon;
        lastUsedTimestamp = System.currentTimeMillis();
        return daemon;
    }
}
```

**Idle Timeout:** Daemons are automatically closed after 30 minutes of inactivity (configurable via `clarpse.daemon.idleTimeout` system property).

## Performance Characteristics

### Phase-by-Phase Speedup

| Phase | Description | Speedup |
|-------|-------------|---------|
| Phase 1 | Deduplicated relationship extraction | 35-40% |
| Phase 2 | Eliminated defensive copy overhead | 10-15% |
| Phase 3 | Incremental parsing (small changes) | 5-10× |
| Phase 4 | Incremental relationship extraction | 20-30% |
| Phase 5 | Daemon reuse (TS/Python) | 10-20% |

### Overall Performance

For typical scenarios with 1-10 changed files in a 1000+ file codebase:
- **Full parse:** ~5-10 seconds
- **Incremental:** ~0.5-1 second (5-20× speedup)

## Usage Example

```java
// Initial full parse
ProjectFiles files = new ProjectFiles();
// ... add files ...
ClarpseProject project = new ClarpseProject(files, Lang.JAVA);
OOPSourceCodeModel baseModel = project.result().model();

// Later: update with changed files
Map<String, String> changedFiles = new HashMap<>();
changedFiles.put("src/Changed.java", "package test; public class Changed { ... }");

Set<String> deletedFiles = new HashSet<>();
deletedFiles.add("src/Removed.java");

CompileResult updated = ClarpseProject.updateModel(
    baseModel, changedFiles, deletedFiles, Lang.JAVA);
OOPSourceCodeModel updatedModel = updated.model();
```

## Testing

See `IncrementalParsingTest` in:
- `com.hadi.test.IncrementalParsingTest` (clarpse)
- `striff.test.model.IncrementalParsingTest` (striff-lib)

Test coverage includes:
- No changes scenario
- File addition
- File deletion
- File modification
- Cross-file reference reclassification
- Component lookup by file
- Component removal by file
- Source files listing
- Direct component access (no copy)
- Multiple file modifications
- Combined add/delete operations

## Thread Safety

- **OOPSourceCodeModel:** Not thread-safe for concurrent modification. Create copies for concurrent use.
- **Shared daemons:** Thread-safe with synchronized access. Multiple threads can share the same daemon.
- **Incremental updates:** Single-threaded. For concurrent incremental updates, use separate models.
