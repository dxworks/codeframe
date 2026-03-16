# C++ Specification (CodeFrame)

## Purpose

This document defines the C++ analysis contract for CodeFrame.

It is normative for C++-specific behavior and output semantics.

Shared rules are defined in **[C/C++ Common Specification](C_CPP_SPEC.md)** and are normative for C++ unless explicitly overridden here.

---

## 1. Language Scope

- File extensions: `.cpp`, `.cc`, `.cxx`, `.hpp`, `.hh`, `.hxx`
- Parser: Tree-sitter C++ (`CppAnalyzer`)
- Parser model: syntax extraction only

Header ownership for `.h` follows the shared deterministic policy in `C_CPP_SPEC.md`.

---

## 2. Output Contract (C++ Deltas)

C++ uses `FileAnalysis` as defined in the shared spec.

C++-specific type kinds:
- `class`
- `struct`
- `union`
- `enum`

`language` is always `"cpp"`.

---

## 3. C++-Specific Extraction Rules

### 3.1 Classes and inheritance

- Extract classes with:
  - `name`, `kind`, `fields`, `methods`, nested `types`
  - inheritance list in `extendsType` (raw base-specifier text)

### 3.2 Free functions

- Extract free functions at namespace/file scope as `methods`.

### 3.3 Constructors and destructors

- Constructors/destructors are represented as methods with `returnType = null`.

### 3.4 Operator overloads

- Operator overloads are represented as methods with textual names (for example `operator+`).

### 3.5 Templates

- Template declarations are captured with template parameter text attached to names.
- No template instantiation semantics are performed.

---

## 4. Current Limitations (C++ V1)

In addition to shared limitations from `C_CPP_SPEC.md`:

- No overload resolution.
- No ADL/namespace semantic binding.
- No C++20 module semantic analysis.

---

## 5. Test Baseline

Normative behavior is validated by:

- `src/test/java/org/dxworks/codeframe/analyzer/cpp/CppAnalyzeApprovalTest.*`

Sample inputs:

- `src/test/resources/samples/cpp/`
