# CodeFrame - Multi-Language Code Parser

A Tree-sitter-based code parser that extracts structural information from source files across multiple programming languages.

## Supported Languages

- **Java** (.java)
- **JavaScript** (.js)
- **TypeScript** (.ts)
- **Python** (.py)
- **C#** (.cs)
- **PHP** (.php)
- **Ruby** (.rb)
- **SQL** (.sql)

## Features

For each supported language, CodeFrame extracts:

- **Type Information**
  - Class/Interface declarations
  - Base classes (extends)
  - Implemented interfaces
  
- **Method Information**
  - Method/Function names
  - Parameters
  - Local variables
  - Method calls with object context

## Usage

### Build the project

```bash
./gradlew build
```

### Run analysis (two arguments required)

CodeFrame requires two arguments: `<input-path>` and `<output-file>`.

```bash
# Gradle
./gradlew run --args="<input-path> <output-file>"

# Direct JAR
java -jar codeframe.jar <input-path> <output-file>
```

Examples:

```bash
# Analyze a single file, write to codeframe-out/analysis.jsonl
./gradlew run --args="src/main/java/org/example/MyClass.java codeframe-out/analysis.jsonl"

# Analyze an entire directory
./gradlew run --args="src/main/java codeframe-out/analysis.jsonl"

# Analyze the entire project
./gradlew run --args=". codeframe-out/analysis.jsonl"

# Run directly via java
java -jar codeframe.jar src codeframe-out/analysis.jsonl
```

### Docker

```bash
# Build
docker build -t codeframe-dev .

# Run (mount your code at /src)
docker run --rm -it -v "$PWD:/workspace" -v "/path/to/code:/src:ro" -w /workspace codeframe-dev

# Inside container
./gradlew run --args="/src /workspace/.out/analysis.jsonl"
```

### Output

The analysis results are written to the path you pass as the second argument (e.g., `/workspace/.out/analysis.jsonl`) in **JSONL format** (JSON Lines - one JSON object per line). Parent directories for the output file are created automatically, and `.out/` is gitignored by default.

### Ignore patterns (.ignore)

- Location: project root `.ignore` (included in releases).
- Default contents:
  ```
  **node_modules**
  **.git**
  **.Designer.cs**
  **.Designer.vb**
  ```
- Syntax:
  - Blank lines and lines starting with `#` are ignored.
  - Globs supported: `*` (within a segment), `**` (across segments).
  - Paths are matched against normalized project paths relative to the input root.
- Examples:
  - `**node_modules**` → ignore anything under any node_modules folder.
  - `**.Designer.cs` → ignore files ending with `.Designer.cs` anywhere.
  - `src/generated/**` → ignore everything under `src/generated/`.

How it works:
- CodeFrame loads `.ignore` at startup using `dx-ignore` and filters files before analysis.
- If `.ignore` is missing, no files are excluded by ignore rules.

### Configuration (codeframe-config.yml)

CodeFrame supports optional configuration via a `codeframe-config.yml` file in the project root.

**Available options:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `maxFileLines` | integer | 20000 | Maximum number of lines a file can have. Files exceeding this limit are skipped during analysis. |

**Example configuration:**
```yaml
maxFileLines: 20000
```

**Behavior:**
- If `codeframe-config.yml` is missing, default values are used.
- If the file exists but contains invalid YAML or missing/invalid values, defaults are applied silently.

### Output Format

Output is **JSONL** (one JSON object per line) for memory efficiency and streaming.

Each line has a `kind` field:
- `"run"` - Start metadata (timestamp, input path, file count)
- File analysis objects (one per file)
- `"error"` - Parse errors (if any)
- `"done"` - Completion metadata (duration, counts)

**Example outputs:** See [approved test outputs](src/test/java/org/dxworks/codeframe/analyzer/) for real analysis results, e.g.:
- [Java sample](src/test/java/org/dxworks/codeframe/analyzer/java/JavaAnalyzeApprovalTest.analyze_Java_Sample.approved.txt)
- [C# sample](src/test/java/org/dxworks/codeframe/analyzer/csharp/CSharpAnalyzeApprovalTest.analyze_CSharp_DataClass.approved.txt)
- [SQL sample](src/test/java/org/dxworks/codeframe/analyzer/sql/SQLAnalyzeApprovalTest.analyze_SQL_Sample.approved.txt)

### SQL Analysis

SQL file analysis uses a hybrid JSqlParser + ANTLR approach to support multiple dialects (PostgreSQL, MySQL, T-SQL, PL/SQL) without configuration.

For complete documentation on SQL support, see **[SQL_ANALYSIS.md](SQL_ANALYSIS.md)**.

## Architecture

See **[ARCHITECTURE.md](ARCHITECTURE.md)** for details on core components and design decisions.

## Contributing

See **[CONTRIBUTING.md](CONTRIBUTING.md)** for guidelines on adding new languages and analyzer conventions.

## Requirements

- Java 11+
- Gradle 8.x
- No native toolchain required (Tree-sitter natives are bundled via Maven artifacts)

## License

This project uses Tree-sitter and its language grammars, which are licensed under MIT.

## Limitations

### General

- Top-level fields/constants are not emitted; focus is on types and methods
- Top-level function calls (e.g., `describe()`, `test()` in Jest files) are not captured; only calls within function bodies are recorded
- Parameter modifiers (e.g., `final`, `ref`, `out`) are not captured
- Constructor calls (`new ...`) are not captured in `methodCalls`
- Loop header variables are not added to `localVariables`

### Language-Specific

- **JavaScript**: Destructured parameters emit leaf names only; dynamic imports ignored; class expressions (`const X = class {}`) not detected as types; constructor functions appear as methods
- **C#**: Events not handled; see test samples for details
- **Java**: Local/anonymous classes not extracted as separate types
- **SQL**: See [SQL_ANALYSIS.md](SQL_ANALYSIS.md)

## Testing

```bash
./gradlew test
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for testing workflow and conventions.
