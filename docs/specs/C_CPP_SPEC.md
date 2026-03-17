# C/C++ Common Specification (CodeFrame)

## Purpose

This document defines the shared C/C++ analysis contract for CodeFrame.

It is normative for common output shape and extraction semantics used by both C and C++ analyzers.

---

## 1. Family Decision: One Family, Two Analyzers

C and C++ are treated as a **single language family** with **two analyzers**:

- `CAnalyzer` for C files
- `CppAnalyzer` for C++ files

Rationale:
- Tree-sitter provides separate grammars/parsers for C and C++.
- C++ has language constructs that do not exist in C (classes, templates, namespaces, overloaded operators, lambdas).
- Keeping analyzers separate reduces grammar-branching complexity and keeps extraction deterministic.
- Shared helper utilities can still be reused for overlapping extraction behavior (includes, functions, calls, structs/enums).

---

## 2. Shared Scope Rules

### 2.1 Parser model

- Syntax extraction only.
- No semantic type solving or symbol binding.
- No preprocessor execution.

### 2.2 Header ownership rule (`.h`)

Because `.h` headers may be either C or C++, V1 uses a deterministic parser-selection policy:
- `.hpp`/`.hh`/`.hxx` -> C++ (extension-based)
- `.h` -> try C++ first
  - if C++ parse quality is clearly poor (error-heavy), fallback to C
  - if both parses are acceptable, keep C++ result

Parse-quality comparison is syntax-only and does not use semantic compilation context.

---

## 3. Shared Output Contract

Both analyzers use `FileAnalysis`.

Top-level fields:
- `filePath`
- `language` = set by the concrete analyzer (`"c"` for C, `"cpp"` for C++)
- `imports` (`#include` directives as raw text)
- `types` (C: struct/union/enum/typedef aliases; C++: class/struct/union/enum)
- `methods` (functions and methods)
- `fields` (file-scope variables/constants)
- `methodCalls` (top-level calls in executable file scope)

`packageName` is not used.

---

## 4. Shared Extraction Rules

### 4.1 Includes

- Extract preprocessor include directives into `imports` as raw text.
- No include resolution or dependency graph expansion.

### 4.2 Functions/Methods

Extract:
- name
- return type text (when syntactically present)
- parameters (`name`, `type` when present)
- local variables (identifier names)
- method/function calls from body

Type-text preservation rule:
- Return/parameter type text is preserved in source-like form when syntactically clear.
- Declarator qualifiers/symbols are retained where present (for example `const`, `restrict`, `*`, `&`, and function-pointer declarator forms such as `int (*cb)(int)`).

### 4.3 Calls

For each call:
- `methodName`
- `objectName` and `objectType` when syntactically clear, otherwise `null`
- `callCount`
- `parameterCount`

Chained/indirect expressions with unknown receiver type remain unresolved (`null` object fields).

### 4.4 Common type handling

- Extract concrete type declarations supported by each analyzer (see language-specific specs).
- Nested types are captured when explicit in syntax.
- File-scope field `type` text follows the same source-like preservation principle.

---

## 5. Shared Limitations (V1)

- No preprocessor macro expansion (`#define`, `#if`, token pasting).
- No semantic resolution (typedef expansion, overload binding, ADL, type inference).
- No cross-file symbol linkage.
- No compile-command awareness (`-std=...`, include paths, macros).

---

## 6. Language-Specific Specs

Common rules in this document are refined by language-specific normative specs:

- C spec: `docs/specs/C_SPEC.md`
- C++ spec: `docs/specs/CPP_SPEC.md`

If a rule conflicts, the language-specific spec has precedence for that language.

---

## 7. Test Baseline

Normative behavior should be verified with ApprovalTests:

- `src/test/java/org/dxworks/codeframe/analyzer/c/CAnalyzeApprovalTest.*`
- `src/test/java/org/dxworks/codeframe/analyzer/cpp/CppAnalyzeApprovalTest.*`

Sample inputs:

- `src/test/resources/samples/c/`
- `src/test/resources/samples/cpp/`

At minimum, each language sample set should cover:
1. Basic declarations and calls
2. Type declarations (struct/class/enum)
3. Includes + nested/chained call scenarios
