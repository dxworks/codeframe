# JavaScript Specification (CodeFrame)

## Purpose

This document defines the JavaScript analysis contract for CodeFrame.

It is normative for extracted output shape and semantics.

---

## 1. Language Scope

- File extensions: `.js`, `.jsx`
- Parser model: syntax extraction only (no runtime/module resolution)

---

## 2. Output Contract

JavaScript uses `FileAnalysis`.

Top-level fields:
- `filePath`
- `language` = `"javascript"`
- `imports` (ES module `import` statements)
- `types` (classes)
- `methods` (standalone/exported functions and top-level function-valued declarations)
- `fields` (module-level `const`/`let`/`var`, excluding function/class expressions)
- `methodCalls` (top-level calls from expression statements)

`packageName` is not used.

---

## 3. Extraction Rules

### 3.1 Types

`types[].kind`:
- `class`

Extracts:
- class declarations
- class expressions assigned to top-level variables
- nested classes via `types`
- class `extendsType` and class `modifiers` (for example `export`, `default`)
- class fields and class methods

### 3.2 Methods

Extracted as `methods` when outside classes:
- function declarations
- generator functions
- top-level arrow/function expressions assigned to variables
- prototype/static assignment patterns (for example `A.prototype.m = function(){}`)

Notes:
- Named function declarations found in non-class nested scopes (for example inside IIFEs/module-pattern wrappers) are also extracted into file-level `methods`.
- This is syntax-only extraction and does not imply module-level visibility/export.

Method metadata:
- `name`, `modifiers`, `parameters`, `localVariables`, `methodCalls`

### 3.3 Fields

Module-level `const`/`let`/`var` become `fields`.

Type is heuristic (`Number`, `String`, `Boolean`, etc.) when inferable from syntax.

### 3.4 Method Calls

Calls are captured in:
- method bodies (`methods[].methodCalls`, `types[].methods[].methodCalls`)
- file scope (`methodCalls`)

Each call includes:
- `methodName`
- optional `objectName`
- optional `objectType` (syntactic only)
- `callCount`

Qualified-call handling follows `docs/EXTRACTION_CONTRACT.md` (§3.2).

Top-level extraction includes nested callback calls inside top-level expressions.

---

## 4. Current Limitations

- No CommonJS dependency extraction (`require`) as imports.
- No semantic inference for dynamic dispatch/module aliasing.
- Property access without invocation is not a method call.

---

## 5. Test Baseline

Normative behavior is validated by:

`src/test/java/org/dxworks/codeframe/analyzer/javascript/JavaScriptAnalyzeApprovalTest.*`

Sample inputs:

`src/test/resources/samples/javascript/`
