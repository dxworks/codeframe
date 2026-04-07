# AGENTS.md

Agent playbook for working in `codeframe`.

## 1) Project at a Glance

- Language/runtime: Java 17, Gradle 8.x.
- Main entry point: `org.dxworks.codeframe.App`.
- Packaging: Shadow JAR output is `build/libs/codeframe.jar`.
- Core purpose: parse multi-language source files and emit JSONL analysis.
- Parser stack:
  - Tree-sitter: Java, JavaScript, TypeScript, Python, C#, PHP, Ruby, Rust.
  - Hybrid: SQL (JSqlParser + ANTLR), COBOL (ANTLR), Markdown (commonmark).

## 2) Environment Requirements

- Required JDK: 17+.
- If Gradle fails with "build uses a Java 11 JVM", set `JAVA_HOME` to JDK 17.
- Gradle wrapper is included; prefer wrapper over system Gradle.
- Cross-platform command variants:
  - Unix/macOS: `./gradlew ...`
  - Windows: `./gradlew.bat ...`

## 3) Build, Test, and Run Commands

### Build

- Full build (includes tests + shadow jar):
  - `./gradlew build`
- Clean build:
  - `./gradlew clean build`
- Build shadow jar directly:
  - `./gradlew shadowJar`

### Test

- Run all tests:
  - `./gradlew test`
- Run a single test class:
  - `./gradlew test --tests "*JavaAnalyzeApprovalTest"`
- Run a single test method (most common agent workflow):
  - `./gradlew test --tests "*JavaAnalyzeApprovalTest.analyze_Java_GenericsSample"`
- Run a package slice:
  - `./gradlew test --tests "org.dxworks.codeframe.analyzer.sql.*"`
- Useful debugging flags:
  - `./gradlew test --stacktrace --info`

### Lint / Static Checks

- There is no dedicated Checkstyle/SpotBugs/Spotless task configured.
- Use `./gradlew check` for standard Gradle verification lifecycle.
- In this repo, test suites are the primary quality gate.

### Run Application

- Run via Gradle:
  - `./gradlew run --args="<input-path> <output-file>"`
- Run packaged jar:
  - `java -jar build/libs/codeframe.jar <input-path> <output-file>`
- Typical local example:
  - `./gradlew run --args="src codeframe-out/analysis.jsonl"`

### Grammar Generation (ANTLR)

- Generate SQL + COBOL grammars:
  - `./gradlew generateAllGrammarSource`
- `compileJava` and `test` already depend on grammar generation.

## 4) CI and Release Behavior

- CI workflow (`.github/workflows/build-and-test.yml`) runs:
  - `./gradlew --no-daemon --stacktrace test`
- Release workflow (`.github/workflows/release.yml`) runs:
  - `./gradlew --no-daemon clean shadowJar`
- Release artifact zip contains:
  - `codeframe.jar`, `instrument.yml`, `.ignore`, `codeframe-config.yml`.

## 5) Test Conventions (ApprovalTests)

- Framework: JUnit Jupiter + ApprovalTests.
- Approval snapshots live beside tests as `*.approved.txt`.
- On failure, inspect `*.received.txt` and confirm expected behavior.
- To accept intentional output changes, replace approved files with received files.
- Commit code and corresponding approved-output updates together.

## 6) Code Style and Structure

### Formatting

- Use 4-space indentation, UTF-8 source files.
- Keep methods focused and small where practical.
- Prefer early returns for guard clauses.
- Preserve deterministic output ordering when possible.

### Imports

- Prefer explicit imports over wildcard imports.
- Keep `java.*` imports grouped and readable.
- Use static imports only for well-scoped utility usage.

### Types and Data Modeling

- Use concrete model classes under `org.dxworks.codeframe.model`.
- Public model fields are used intentionally in this project; follow existing model style.
- Use `Optional` only where APIs already expose optionality (e.g., language detection).
- Favor immutable/unmodifiable collections for configuration and registries.

### Naming

- Classes/interfaces/enums: PascalCase.
- Methods/fields/local vars: camelCase.
- Constants: UPPER_SNAKE_CASE.
- Tests use descriptive snake-like method names with scenario context
  (example: `analyze_Java_GenericsSample`).

### Analyzer-Specific Design

- Implement new analyzers via `LanguageAnalyzer` and register in `LanguageRegistry`.
- Reuse `TreeSitterHelper` utilities rather than duplicating traversal logic.
- Prefer AST-driven extraction over regex.
- Keep extraction syntactic/factual; avoid semantic inference.
- Ensure stable sorting/deduplication for method call outputs.

### Error Handling

- Fail fast for invalid startup conditions (e.g., missing input path).
- During file analysis, catch exceptions per file and emit error JSONL records.
- Prefer partial results over hard failure when parsing problematic inputs.
- Do not add side effects (writing extra files, network calls) inside analyzers.

### Backward Compatibility and Complexity

- Do not add complexity solely for backward compatibility unless requested.
- Do not ship hacks; understand grammar/tree shape and solve at parser level.
- Use as little regex as practical; regex is a fallback, not default strategy.

## 7) Extraction Contract (Must Follow)

- Extract facts only.
- Keep output deterministic.
- Avoid analyzer side effects.
- Return partial results on parse errors when feasible.
- Keep analyzers simple and fast; enrichment belongs elsewhere.

## 8) Repository-Specific Agent Rules

- Relevant guidance source files:
  - `docs/CONTRIBUTING.md`
  - `docs/ARCHITECTURE.md`
  - `README.md`
  - `docs/AGENTS.md` (legacy short guidance)
- Cursor rules:
  - No `.cursor/rules/` directory found.
  - No `.cursorrules` file found.
- Copilot rules:
  - No `.github/copilot-instructions.md` file found.

## 9) Practical Agent Workflow

- Before changing analyzers:
  - Read matching analyzer tests and approved snapshots first.
- After changes:
  - Run targeted single-test command.
  - Run broader language test class.
  - Run full `./gradlew test` if scope is cross-cutting.
- If output shape changes:
  - Update approved files intentionally and review diffs carefully.
