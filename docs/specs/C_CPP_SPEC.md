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

Because `.h` headers may be either C or C++, this uses a deterministic parser-selection policy:
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
- `types` (C: struct/union/enum/typedef aliases; C++: class/struct/union/enum/enum class/namespace/typedef)
- `methods` (functions and methods)
- `fields` (file-scope variables/constants)
- `methodCalls` (top-level calls in executable file scope)

`packageName` is not used.

---

## 4. Shared Extraction Rules

### 4.1 Includes

- Extract preprocessor include directives into `imports` as raw text.
- Preprocessor directives other than `#include` (for example `#define`, `#if`, `#ifdef`, `#ifndef`, `#elif`, `#else`, `#endif`, `#pragma`) are not emitted in analysis output.
- No include resolution or dependency graph expansion.

### 4.2 Functions/Methods

Extract:
- name
- return type text (when syntactically present)
- `isDeclarationOnly` = `true` when the extracted method comes from a declaration without a body
- `visibility` (see language-specific specs; C has no visibility, C++ uses access specifiers)
- `modifiers` (storage-class and qualifier keywords present on the declaration)
- parameters (`name`, `type` when present)
- local variables (identifier names)
- method/function calls from body

`isDeclarationOnly` is emitted only when `true`.
For definitions (or any non-declaration-only entry), the field is omitted and not emitted as `false`.

Unnamed parameters:
- Parameters without an identifier are still emitted in `parameters` with `name: ""`.
- `type` preserves the source-like parameter text (for example `const Counter&`).

Shared modifier keywords captured for functions/methods:
- `static`, `extern`, `inline`
- Language-specific modifiers are defined in `C_SPEC.md` and `CPP_SPEC.md`.

Type-text preservation rule:
- Return/parameter type text is preserved in source-like form when syntactically clear.
- Declarator qualifiers/symbols are retained where present (for example `const`, `restrict`, `*`, `&`, and function-pointer declarator forms such as `int (*cb)(int)`).

Variadic parameters:
- C/C++ variadic `...` is extracted as a parameter with `name: "..."` and no type.

C++ linkage blocks:
- Declarations inside `extern "C" { ... }` linkage specifications are extracted the same way as equivalent top-level declarations.
- Linkage text itself is not emitted as a separate entity.

Example — given:

```c
static inline int add(int a, int b) { return a + b; }
void log(const char *fmt, ...);
```

Extracted methods:

```json
{ "name": "add", "returnType": "int", "modifiers": ["static", "inline"],
  "parameters": [{"name": "a", "type": "int"}, {"name": "b", "type": "int"}] }
{ "name": "log", "returnType": "void",
  "parameters": [{"name": "fmt", "type": "const char *"}, {"name": "..."}] }
```

### 4.3 Calls

For each call:
- `methodName`
- `objectName` and `objectType` when syntactically clear, otherwise `null`
- `callCount`
- `parameterCount`

Chained/indirect expressions with unknown receiver type remain unresolved (`null` object fields).

Qualified-call handling follows `docs/EXTRACTION_CONTRACT.md` (§3.2).

### 4.4 Common type handling

- Extract concrete type declarations supported by each analyzer (see language-specific specs).
- `modifiers` are captured on types when syntactically present (see language-specific specs).
- Nested types are captured when explicit in syntax.
- Forward declarations (bodyless `struct Foo;`) are skipped — no empty type is emitted.
- Typedef alias target text in `extendsType` is preserved in source-like form.
  - For named inline aggregate typedefs (for example `typedef struct Logger { ... } Logger;`), `extendsType` keeps a compact target (for example `struct Logger`) rather than the full aggregate body.
  - Function-pointer typedef targets are likewise preserved as source-like declarator text.

### 4.5 File-scope fields

Extract:
- `name`
- `type` (source-like text)
- `modifiers` (storage-class and qualifier keywords: `static`, `extern`, `const`, `volatile`)

Example — given:

```c
static int counter = 0;
extern const char *app_name;
```

Extracted fields:

```json
{ "name": "counter", "type": "int", "modifiers": ["static"] }
{ "name": "app_name", "type": "const char *", "modifiers": ["extern"] }
```

---

## 5. Shared Limitations

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
