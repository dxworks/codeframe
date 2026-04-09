# Extraction Contract (CodeFrame)

## Purpose

This document is the canonical extraction contract for analyzer behavior across languages.

Language specs in `docs/specs/*_SPEC.md` remain normative for language-specific behavior.

---

## 1. Core Rules (All Parsers)

- Extract facts only. Do not infer capabilities or intent.
- Keep output deterministic.
- Preserve structural signal over semantic completeness/perfection; partial output is acceptable when syntax is recoverable.
- Do not write files as side effects.
- On parse errors, return partial results when possible.
- Keep analyzers simple and fast; enrichment belongs to post-processing tools.

---

## 2. Shared Helper Conventions

Use `TreeSitterHelper` utilities to maintain consistency and reduce duplication:

| Method | Purpose |
|--------|---------|
| `extractName(source, node, childType)` | Extract name from a child node by type |
| `collectMethodCall(...)` | Add method call with deduplication |
| `identifyNestedNodes(...)` | Identify nested class/type declarations |
| `renderObjectName(...)` | Build object name for chained expressions |
| `isValidIdentifier(name)` | Validate identifier syntax |
| `METHOD_CALL_COMPARATOR` | Sort method calls consistently |
| `findFirstChild`, `findAllDescendants` | Node traversal helpers |

---

## 3. Method Call Extraction

### 3.1 Chained calls

- For chained calls where the receiver is the result of another method call (for example `obj.method1().method2()`), set `objectName` and `objectType` to `null`.
- The type cannot be determined without semantic analysis.

Example for `db.query().debug().printTo(output())`:

| Call | `objectName` | `objectType` |
|------|--------------|--------------|
| `query` | `"db"` | `"DatabaseConnection"` |
| `debug` | `null` | `null` (chained) |
| `printTo` | `null` | `null` (chained) |
| `output` | `null` | `null` (standalone) |

### 3.2 Qualified calls

- Keep `methodName` as the unqualified leaf callee name.
- Do **not** map namespace/type/module/package qualifier text into `objectName` or `objectType`.
- Set `objectName` / `objectType` only when there is a true receiver object expression.

Examples:
- C++ `util::declared_ns(2)` -> `methodName: "declared_ns"`, `objectName: null`, `objectType: null`
- C# `System.Console.WriteLine(a)` -> `methodName: "WriteLine"`, qualifier not mapped to object fields
- Java `Math.max(a, b)` -> `methodName: "max"`, qualifier not mapped to object fields

### 3.3 Property access vs method calls

- **C#**: Record property accesses as `get_PropertyName` or `set_PropertyName` method calls.
- **Ruby**: Only record calls with arguments or special suffixes (`?`, `!`); skip simple property accessors.
- **Java**: Record explicit getter/setter method calls; field access is not a method call.

### 3.4 Object type resolution

- Resolve `objectType` from local variables, parameters, or fields when possible.
- Use `null` when type cannot be determined from syntax alone.

---

## 4. Nested Functions

Nested functions (functions defined inside other functions) are not extracted as separate top-level methods. Instead:

- nested function method calls are captured in parent `methodCalls`
- nested function local variables are captured in parent `localVariables`

This is typically achieved with `findAllDescendants` during body analysis.

---

## 5. File-Level Fields and Calls

All analyzers should extract file/module-level elements into `FileAnalysis` when applicable for the language.

`fields`:
- Python: top-level assignments with type annotations
- JavaScript/TypeScript: module-level `const`/`let`/`var`
- Ruby: module-level constants
- PHP: top-level `const` declarations
- Java/C#: static fields are class-level, not file-level

`methodCalls`:
- capture top-level calls outside class/function declarations
- include calls inside callbacks nested under top-level statements/expressions
- do not duplicate calls already attributed to named functions/methods

---

## 6. Field/Variable Visibility

- Ruby: instance variables (`@var`) are private by default.
- Java/C#/TypeScript/PHP/Rust: emit visibility only when explicitly present in source.
- Python: `_var` is protected-by-convention; `__var` is private-by-convention.
