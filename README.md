# CodeFrame - Multi-Language Code Parser

A Tree-sitter-based code parser that extracts structural information from source files across multiple programming languages.

## Supported Languages

- **Java** (.java)
- **JavaScript** (.js)
- **TypeScript** (.ts)
- **Python** (.py)
- **C#** (.cs)
- **PHP** (.php)

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

### Docker workflow

Use separate folders in the container:
- `/workspace`: the CodeFrame project (bind-mounted to your repo)
- `/src`: the codebase to analyze (mounted read-only)
- Results are written under `/workspace/.out` (persisted on your host via the `/workspace` bind mount; `.out/` is gitignored)

#### 1) Build the image
```bash
docker build -t codeframe-dev .
```

#### 2) Run the container with volumes

- Windows (PowerShell):
```powershell
docker run --rm -it `
  -v "$PWD:/workspace" `
  -v "C:\data\repos\my-project\src:/src:ro" `
  -w /workspace `
  codeframe-dev
```

- Linux/macOS:
```bash
docker run --rm -it \
  -v "$PWD:/workspace" \
  -v "/absolute/path/to/your/repo:/src:ro" \
  -w /workspace \
  codeframe-dev
```

Optional debug port:
```bash
docker run --rm -it -p 5005:5005 \
  -e "JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  -v "$PWD:/workspace" \
  -v "/absolute/path/to/your/repo:/src:ro" \
  -w /workspace \
  codeframe-dev
```

#### 3) Run the program inside the container
```bash
./gradlew clean run --args="/src /workspace/.out/analysis.jsonl"
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

#### Why JSONL?

- **Memory efficient**: Constant memory usage regardless of codebase size
- **Streamable**: Process results line-by-line without loading entire file
- **Resumable**: Can stop/restart analysis without losing progress
- **Parallel-friendly**: Multiple threads can write safely

#### Output Structure

Each line is a separate JSON object with a `kind` field:

**Line 1 - Run metadata:**
```json
{"kind":"run","started_at":"2025-09-30T11:00:00Z","input_path":"src","total_files":1000}
```

**Lines 2-N - File analyses:**
```json
{"filePath":"src/Example.java","language":"java","packageName":"com.example","types":[{"kind":"class","name":"Example","visibility":"public","modifiers":["public"],"annotations":["@Component"],"extendsType":"BaseClass","implementsInterfaces":["Interface1"]}],"fields":[{"name":"service","type":"MyService","visibility":"private","modifiers":["private","final"],"annotations":["@Autowired"]}],"methods":[{"name":"processData","returnType":"Result","visibility":"public","modifiers":["public"],"annotations":["@Override"],"parameters":[{"name":"input","type":"String"}],"localVariables":["result"],"methodCalls":[{"methodName":"validate","objectType":"String","objectName":"input","callCount":1}]}],"imports":["import com.example.MyService;"]}
```

**Error records (if any):**
```json
{"kind":"error","file":"src/Bad.java","language":"java","error":"Parse error"}
```

**Last line - Completion metadata:**
```json
{"kind":"done","ended_at":"2025-09-30T11:00:05Z","files_analyzed":998,"files_with_errors":2,"duration_seconds":5}
```

#### Processing JSONL Output

**Python example:**
```python
import json

with open('codeframe-out/analysis.jsonl', 'r') as f:
    for line in f:
        obj = json.loads(line)
        if obj.get('kind') == 'run':
            print(f"Starting analysis of {obj['total_files']} files")
        elif 'filePath' in obj:  # File analysis
            print(f"Analyzed: {obj['filePath']}")
        elif obj.get('kind') == 'done':
            print(f"Complete: {obj['files_analyzed']} files")
```

**Shell example:**
```bash
# Count successful analyses
grep -v '"kind"' analysis.jsonl | grep '"filePath"' | wc -l

# Extract all Java files
grep '"language":"java"' analysis.jsonl > java-files.jsonl

# Get summary
head -1 analysis.jsonl  # Run info
tail -1 analysis.jsonl  # Done info
```

## Architecture

### Core Components

- **`Language`** - Enum defining supported languages
- **`LanguageDetector`** - Detects language from file extension
- **`LanguageAnalyzer`** - Interface for language-specific analyzers
- **`FileAnalysis`** - Model containing analysis results

### Language Analyzers

Each language has a dedicated analyzer:

- `JavaAnalyzer` - Parses Java classes, interfaces, methods
- `TypeScriptAnalyzer` - Parses TypeScript classes, interfaces, functions
- `JavaScriptAnalyzer` - Parses JavaScript classes and functions
- `PythonAnalyzer` - Parses Python classes and functions
- `CSharpAnalyzer` - Parses C# classes, interfaces, methods
- `PHPAnalyzer` - Parses PHP classes, interfaces, functions

### Tree-sitter Integration

The project uses Tree-sitter grammar libraries:
- `tree-sitter-java`
- `tree-sitter-javascript`
- `tree-sitter-typescript`
- `tree-sitter-python`
- `tree-sitter-c-sharp`
- `tree-sitter-php`

## Architectural Decisions

### 1) Choosing Tree-sitter

- **Incremental, robust parsing**: Tree-sitter provides concrete syntax trees with stable node types across languages, suitable for structural extraction (`types`, `methods`, `fields`, `calls`).
- **Multi-language, consistent API**: A single parsing approach across Java, JS/TS, Python, C#, PHP simplifies analyzer design and maintenance.
- **Performance and memory**: Fast parsing with small memory footprint; aligns with our streaming JSONL output to keep RAM low for large repos.
- **Runtime constraints**: In constrained runners/containers, we need deterministic, offline-friendly tooling. Tree-sitter grammars are shipped as Maven artifacts, avoiding runtime downloads or external CLIs.

### 2) Java binding: tree-sitter bonede

- **Bundled native libraries**: The `io.github.bonede:tree-sitter` artifacts include native binaries for Windows/Linux/macOS. This removes the need for a local C toolchain or building native libs during CI/runtime.
- **Cross-OS compatibility**: Works the same on developer machines, Docker (Linux), and Windows hosts—critical for heterogeneous environments.
- **Runtime constraints**: In sandboxed environments we cannot install system packages or compile natives. Bonede’s prebuilt natives make the analyzer portable and ready-to-run without extra steps.

## Extending

### Adding a New Language

1. Add the Tree-sitter grammar dependency to `build.gradle`
2. Add the language to the `Language` enum
3. Update `LanguageDetector` with file extension mapping
4. Create a new analyzer implementing `LanguageAnalyzer`
5. Register the language and analyzer in `App.java`

### Example: Adding Go Support

```java
// 1. Add to Language enum
GO("go")

// 2. Update LanguageDetector
if (fileName.endsWith(".go")) {
    return Optional.of(Language.GO);
}

// 3. Create GoAnalyzer.java
public class GoAnalyzer implements LanguageAnalyzer {
    @Override
    public FileAnalysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        // Implementation
    }
}

// 4. Register in App.java
TREE_SITTER_LANGUAGES.put(Language.GO, new TreeSitterGo());
ANALYZERS.put(Language.GO, new GoAnalyzer());
```

## Requirements

- Java 21+
- Gradle 8.x

## License

This project uses Tree-sitter and its language grammars, which are licensed under MIT.

## Limitations

### C#

- **Called constructors and fields are not captured**
  - Current call extraction focuses on method invocations and property accessors. Constructor calls (e.g., `new Type(...)` and `base(...)`/`this(...)`) and direct field reads/writes are not emitted in `methodCalls`.

- **Loop local variables are not captured**
  - Variables declared in loop headers (e.g., `for (var i = 0; ...)`, `foreach (var x in ...)`) are not added to `localVariables`.
  - See `src/test/resources/samples/csharp/LoopLocalsSample.cs` for examples.

- **Events are not handled**
  - Event declarations/subscriptions/raises are not modeled.
  - See `src/test/resources/samples/csharp/DelegatesEventsLambdasSample.cs`
    

### Java

- **Constructor calls are not captured**
  - Constructor invocations (e.g., `new ClassName(...)`) are not emitted in `methodCalls`.
  - See `src/test/resources/samples/java/MultipleClasses.java` for an example (`new ExtraClass()`).

- **Loop header locals are not captured**
  - Variables declared in loop headers (e.g., `for (int i = 0; ...)`) are not added to `localVariables`.
  - See `src/test/resources/samples/java/MultipleClasses.java` for an example (`for (int i = 0; i < times; i++)`).

- **Local and anonymous classes are not extracted as separate types**
  - Bodies are analyzed within the enclosing method or type, and their method calls are recorded.
  - The classes themselves do not appear as distinct `types` entries.
  - See `src/test/resources/samples/java/AnonymousInnerClassesSample.java`.
