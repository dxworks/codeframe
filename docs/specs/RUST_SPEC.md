# Rust Specification (CodeFrame)

## Purpose

This document defines the Rust analysis contract for CodeFrame.

It is normative for extracted output shape and semantics.

---

## 1. Language Scope

- File extension: `.rs`
- Parser model: syntax extraction only (no borrow checker/type system integration)

---

## 2. Output Contract

Rust uses `FileAnalysis`.

Top-level fields:
- `filePath`
- `language` = `"rust"`
- `imports` (`use` declarations collected across the file, including nested modules)
- `types` (modules/structs/enums/traits/impl blocks)
- `methods` (top-level functions)
- `fields` (top-level `const` and `static`)

`packageName` is not used.

---

## 3. Extraction Rules

### 3.1 Type Kinds

`types[].kind` can be:
- `mod`
- `struct`
- `enum`
- `trait`
- `impl`

Type metadata may include:
- `name`
- `visibility`
- `modifiers`
- `annotations` (attributes)
- `implementsInterfaces` (trait implemented by an `impl`)
- `fields`
- `methods`
- `types` (nested types inside modules)

### 3.2 Functions and Methods

Extracted from:
- top-level `function_item` -> root `methods`
- trait items, impl items, and module-contained functions -> type methods

Method/function metadata:
- `name`
- `returnType`
- `isDeclarationOnly` (`true` when the declaration has no body)
- `visibility`/`modifiers` (for example `pub`, `pub(crate)`)
- `annotations`
- `parameters` (including `self`)
- `localVariables`
- `methodCalls`

`isDeclarationOnly` is omitted when `false`.

Type-text preservation rule:
- `returnType` and parameter `type` values preserve Rust reference syntax when present.
- Examples: `&User`, `&Point`, `&mut T`.

### 3.3 Fields

Extracted field sources:
- struct field declarations
- `const` and `static` items (top-level and module scope)
- associated `const` items inside `impl` blocks (as `impl` type fields)

Field metadata:
- `name`
- `type`
- `visibility`
- `modifiers` (`const`, `static`, `mut`, and visibility when present)

Field type text follows the same preservation principle (source-like syntax where possible).

### 3.4 Method Calls and Macros

Calls extracted from:
- `call_expression`
- `macro_invocation`

Call metadata:
- `methodName` (macros are recorded with `!`, e.g., `println!`)
- optional `objectName`
- optional `objectType`
- `callCount`
- `parameterCount`

Qualified-call handling follows `docs/EXTRACTION_CONTRACT.md` (§3.2); for example, `module::function()` records `methodName: "function"`.

---

## 4. Current Limitations

- No trait/where-clause semantic solving beyond syntactic extraction.
- No ownership/borrow/lifetime modeling.
- Pattern/match semantics are not represented as a separate model.

---

## 5. Test Baseline

Normative behavior is validated by:

`src/test/java/org/dxworks/codeframe/analyzer/rust/RustAnalyzeApprovalTest.*`

Sample inputs:

`src/test/resources/samples/rust/`
