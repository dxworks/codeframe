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
{"filePath":"src/Example.java","language":"java","packageName":"com.example","types":[{"kind":"class","name":"Example","visibility":"public","modifiers":["public"],"annotations":["@Component"],"extendsType":"BaseClass","implementsInterfaces":["Interface1"],"mixins":[]}],"fields":[{"name":"service","type":"MyService","visibility":"private","modifiers":["private","final"],"annotations":["@Autowired"]}],"methods":[{"name":"processData","returnType":"Result","visibility":"public","modifiers":["public"],"annotations":["@Override"],"parameters":[{"name":"input","type":"String"}],"localVariables":["result"],"methodCalls":[{"methodName":"validate","objectType":"String","objectName":"input","callCount":1}]}],"imports":["import com.example.MyService;"]}
```

**Type Information Fields:**
- `extendsType`: Base class (single inheritance)
- `implementsInterfaces`: Interfaces implemented by the type
- `mixins`: Modules/traits mixed into the type (Ruby `include`/`extend`/`prepend`, PHP `use`)
  - Ruby: `include StringHelpers` → `"mixins": ["StringHelpers"]`
  - PHP: `use LoggerTrait;` → `"mixins": ["LoggerTrait"]`
  - Other languages: Empty array
- `properties`: Property declarations with accessors
  - Ruby: `attr_accessor :name` → `{"name": "name", "accessors": [{"kind": "get"}, {"kind": "set"}]}`
  - Ruby: `attr_reader :email` → `{"name": "email", "accessors": [{"kind": "get"}]}`
  - C#: Properties with get/set accessors
  - Other languages: Empty array

**Error records (if any):**
```json
{"kind":"error","file":"src/Bad.java","language":"java","error":"Parse error"}
```

**Last line - Completion metadata:**
```json
{"kind":"done","ended_at":"2025-09-30T11:00:05Z","files_analyzed":998,"files_with_errors":2,"duration_seconds":5}
```

### SQL Analysis Output

For `.sql` files, the analyzer emits a SQL-specific structure inside each file analysis:

- **topLevelReferences**: Relations (tables/views) used by top-level DML statements (outside any definition).
- **topLevelCalls**: Top-level function and procedure calls (outside any definition).
- **Operation lists**: Per-file operations discovered (definitions and drops):
  - `createTables`, `alterTables`, `createViews`, `createIndexes`, `createProcedures`, `createFunctions`, `createTriggers`, `dropOperations`.

Example (abbreviated):

```json
{
  "filePath": "src/test/resources/samples/sql/top_level_statements.sql",
  "language": "sql",
  "topLevelReferences": { "relations": ["sales.orders", "sales.order_summaries"] },
  "topLevelCalls": { "functions": ["COALESCE", "SUM"], "procedures": ["sales.recalc_order_total"] },
  "createTables": [],
  "alterTables": [],
  "createViews": [],
  "createIndexes": [],
  "createProcedures": [],
  "createFunctions": [],
  "createTriggers": [],
  "dropOperations": []
}
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
- `RubyAnalyzer` - Parses Ruby classes, modules, methods
- `SQLAnalyzer` - Parses SQL files for DDL operations and top-level references/calls

### Tree-sitter Integration

The project uses Tree-sitter grammar libraries:
- `tree-sitter-java`
- `tree-sitter-javascript`
- `tree-sitter-typescript`
- `tree-sitter-python`
- `tree-sitter-c-sharp`
- `tree-sitter-php`
- `tree-sitter-ruby`
- `tree-sitter-sql`

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
6. Create test samples and approval tests

### Analyzer Implementation Conventions

When implementing a new language analyzer, follow these conventions to ensure consistency across all analyzers:

#### Method Call Extraction

**Chained Method Calls:**
- For chained calls where the receiver is the result of another method call (e.g., `obj.method1().method2()`), set both `objectName` and `objectType` to `null`
- This is because the type cannot be determined without semantic analysis
- Example: For `db.query().debug().printTo(output())`:
  - `query` → `objectName: "db"`, `objectType: "DatabaseConnection"`
  - `debug` → `objectName: null`, `objectType: null` (chained)
  - `printTo` → `objectName: null`, `objectType: null` (chained)
  - `output` → `objectName: null`, `objectType: null` (standalone)

**Property Access vs Method Calls:**
- **Language-specific handling:**
  - **C#**: Record property accesses as `get_PropertyName` or `set_PropertyName` method calls (properties compile to methods)
  - **Ruby**: Only record calls with arguments or special suffixes (`?`, `!`); skip simple property accessors like `user.name`
  - **Java**: Record explicit getter/setter method calls (e.g., `user.getName()`); field access is not a method call
- The goal is to capture semantically meaningful operations while avoiding noise from simple field/property access where appropriate

**Object Type Resolution:**
- Try to resolve `objectType` from local variables, parameters, or fields when possible
- Use `null` when the type cannot be determined from the syntax tree alone

#### Field/Variable Visibility

- Use language-specific conventions for determining visibility
- Ruby: Instance variables (`@var`) are private by default
- Java/C#: Use explicit modifiers
- Python: Leading underscore (`_var`) indicates private by convention

#### Test Coverage

- Create at least 3 sample files covering:
  1. Basic class/method structure with method calls
  2. Modules/interfaces/mixins (if applicable)
  3. Inheritance and polymorphism
- Use ApprovalTests framework for output verification
- Ensure samples include chained method calls to verify correct handling

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

- Java 11+
- Gradle 8.x
- No native toolchain required (Tree-sitter natives are bundled via Maven artifacts)

## License

This project uses Tree-sitter and its language grammars, which are licensed under MIT.

## Limitations

### All languages

- Top-level fields/constants (for langauges that support them, e.g., JavaScript, TypeScript, Python, PHP) are not emitted as entries in the analysis output. The analyzer focuses on types (classes/interfaces/enums/records where applicable) and functions/methods.

### JavaScript

- Destructured parameter extraction is leaf-only. For a signature like `fn({ data: { user, settings }, meta: { timestamp } })`, parameters emitted are `user`, `settings`, `timestamp` (not `data`, `meta`).
- Generator functions are marked using syntax-like modifiers:
  - Top-level functions: `"function*"` (e.g., `export function* name()`)
  - Class methods: `"*"` (e.g., `*methodName()`)
- Dynamic import expressions `import("path")` are not modeled as method calls and are currently ignored in `methodCalls`.

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

### SQL

- SQL Analysis semantics
  - References are recorded as relations only. We do not distinguish between tables and views without a project-wide catalog. Each operation (view/function/procedure) exposes `references.relations`.
  - Calls are recorded per operation using explicit AST nodes only. Functions are captured from `function_call` nodes into `calls.functions`. Procedures are captured from `call_statement` nodes into `calls.procedures`.

- **DROP INDEX qualified names**
  - Some SQL grammar variants emit an `ERROR` node when parsing qualified index names like `DROP INDEX schema.index_name` (e.g., the dot-separated form may be partially outside the `drop_index` node).
  - We parse using AST-first approaches. If the index name token is not present under `drop_index`, the analyzer falls back conservatively and may report only the schema or leave the name unset.
  - This is by design to keep parsing robust. If necessary, a future enhancement can use a scoped text read around the statement to recover the missing identifier without relying on global text parsing.

- **MySQL CREATE FUNCTION (BEGIN...END) partial support**
  - The current Tree-sitter SQL grammar used by Codeframe often marks MySQL-style bodies as `ERROR`. The analyzer still parses the function header (name, parameters, `RETURNS`) but does not analyze the body.
  - As a result, referenced tables are not collected for these functions, and the captured `returnType` may include attributes (e.g., `DETERMINISTIC`).
  - PostgreSQL-style functions (including dollar-quoted bodies) are supported and will be extracted with name, schema (optional), parameters, return type, and referenced tables from `FROM`/`JOIN` clauses in the body.

## Testing

- **ApprovalTests-based strategy**
  - We use ApprovalTests-Java to snapshot analysis results. Each test verifies the pretty-printed JSON using an approved artifact.
  - When output changes, a `.received.txt` is generated next to the test class; review and promote it to `.approved.txt` if correct.

- **Running tests**
  - All tests: `./gradlew test`
  - Single test method, e.g. Java generics: `./gradlew test --tests "*JavaAnalyzeApprovalTest.analyze_Java_GenericsSample"`

- **Workflow**
  - Make a change → run tests → inspect `.received.txt` → approve if expected → commit both code and updated `.approved.txt`.
