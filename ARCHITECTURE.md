# Architecture

## Core Components

- **`Language`** - Enum defining supported languages
- **`LanguageDetector`** - Detects language from file extension
- **`LanguageAnalyzer`** - Interface for language-specific analyzers
- **`FileAnalysis`** - Model containing analysis results

## Language Analyzers

Each language has a dedicated analyzer:

| Language | Analyzer | Parser |
|----------|----------|--------|
| Java | `JavaAnalyzer` | Tree-sitter |
| JavaScript | `JavaScriptAnalyzer` | Tree-sitter |
| TypeScript | `TypeScriptAnalyzer` | Tree-sitter |
| Python | `PythonAnalyzer` | Tree-sitter |
| C# | `CSharpAnalyzer` | Tree-sitter |
| PHP | `PHPAnalyzer` | Tree-sitter |
| Ruby | `RubyAnalyzer` | Tree-sitter |
| SQL | `SQLAnalyzer` | JSqlParser + ANTLR |

## Design Decisions

### Why Tree-sitter?

- **Incremental, robust parsing**: Concrete syntax trees with stable node types across languages, suitable for structural extraction (`types`, `methods`, `fields`, `calls`).
- **Multi-language, consistent API**: A single parsing approach across Java, JS/TS, Python, C#, PHP simplifies analyzer design and maintenance.
- **Performance and memory**: Fast parsing with small memory footprint; aligns with streaming JSONL output to keep RAM low for large repos.
- **Runtime constraints**: Deterministic, offline-friendly tooling. Tree-sitter grammars are shipped as Maven artifacts, avoiding runtime downloads or external CLIs.

### Why tree-sitter-bonede Java bindings?

- **Bundled native libraries**: The `io.github.bonede:tree-sitter` artifacts include native binaries for Windows/Linux/macOS—no local C toolchain required.
- **Cross-OS compatibility**: Works identically on developer machines, Docker (Linux), and Windows hosts.
- **Portable**: Prebuilt natives make the analyzer ready-to-run without extra setup in sandboxed environments.

### Why JSqlParser + ANTLR for SQL?

SQL required a different approach than other languages:

1. **Tree-sitter SQL grammar**: Produced too many parser errors on real-world SQL files
2. **JSqlParser alone**: Parses DDL well but doesn't parse routine bodies (stored procedures, functions)
3. **ANTLR grammars for MySQL/PostgreSQL**: Couldn't find production-quality grammars

**Solution**: Hybrid approach using JSqlParser for DDL/DML structure, with ANTLR grammars for T-SQL and PL/SQL routine body analysis. See [SQL_ANALYSIS.md](SQL_ANALYSIS.md) for details.

### Why JSONL output?

- **Memory efficient**: Constant memory usage regardless of codebase size
- **Streamable**: Process results line-by-line without loading entire file
- **Resumable**: Can stop/restart analysis without losing progress
- **Parallel-friendly**: Multiple threads can write safely
