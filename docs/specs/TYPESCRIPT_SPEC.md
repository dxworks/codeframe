# TypeScript Specification (CodeFrame)

## Purpose

This document defines the TypeScript analysis contract for CodeFrame.

It is normative for extracted output shape and semantics.

---

## 1. Language Scope

- File extensions: `.ts`, `.tsx`
- Parser model: syntax extraction only (no type-checker-based resolution)

---

## 2. Output Contract

TypeScript uses `FileAnalysis`.

Top-level fields:
- `filePath`
- `language` = `"typescript"`
- `imports` (raw `import` declarations)
- `types` (classes/interfaces/enums/type aliases)
- `methods` (standalone functions and top-level function-valued declarations)
- `fields` (module-level declarations)
- `methodCalls` (top-level calls from expression statements)

`packageName` is not used.

---

## 3. Extraction Rules

### 3.1 Types

`types[].kind` can be:
- `class`
- `interface`
- `enum`
- `type` (for `type` aliases)

Type metadata can include:
- `name` (including generic parameters)
- `visibility`
- `modifiers`
- `annotations` (decorators)
- `extendsType`
- `implementsInterfaces`
- `fields`
- `properties`
- `methods`
- `types` (nested classes)

Type aliases are encoded as:
- `kind: "type"`
- `name`
- `extendsType` (alias target text)

### 3.2 Class-Specific Behavior

- Constructor parameter properties (`public/private/protected/readonly` params) are emitted as class `fields`.
- Class members include modifiers/decorators when syntactically present.

### 3.3 Methods

Extracted as standalone methods:
- function declarations
- top-level arrow/function-valued declarations
- prototype/static assignment patterns

Method metadata:
- `name`, `returnType`, `visibility`, `modifiers`, `annotations`
- `isDeclarationOnly` (`true` when a declaration/signature has no body)
- `parameters` (`name`, `type`)
- `localVariables`
- `methodCalls`

`isDeclarationOnly` is omitted when `false`.

### 3.4 Fields and Calls at File Scope

- Module-level `const`/`let`/`var` become `fields`.
- `field.type` uses explicit type annotation when present; otherwise syntax-based inference.
- Top-level `methodCalls` include all `call_expression` nodes that are at file scope.
- Calls nested inside type/function/class declarations are excluded from file-level `methodCalls`.
- Calls inside callbacks nested under top-level statements/expressions are included.
- Call metadata and qualified-call handling follow `docs/EXTRACTION_CONTRACT.md` (§3).

---

## 4. Current Limitations

- No semantic resolution from TypeScript compiler services.
- Complex conditional/mapped type semantics are preserved as text, not modeled structurally.
- Runtime alias/module resolution is out of scope.

---

## 5. Test Baseline

Normative behavior is validated by:

`src/test/java/org/dxworks/codeframe/analyzer/typescript/TypeScriptAnalyzeApprovalTest.*`

Sample inputs:

`src/test/resources/samples/typescript/`
