# Java Specification (CodeFrame)

## Purpose

This document defines the Java analysis contract for CodeFrame.

It is normative for extracted output shape and semantics.

---

## 1. Language Scope

- File extensions: `.java`
- Parser model: tree-structured extraction only (no semantic type solving)

---

## 2. Output Contract

Java uses `FileAnalysis`.

Top-level fields:
- `filePath`
- `language` = `"java"`
- `packageName` (from `package` declaration, when present)
- `imports` (raw `import` declarations)
- `types` (classes/interfaces/enums/records)

Java does not emit file-level `fields` or file-level `methodCalls`.

---

## 3. Extraction Rules

### 3.1 Types

`types[].kind` can be:
- `class`
- `interface`
- `enum`
- `record`

Extracted type metadata:
- `name` (including generic parameters when present)
- `visibility` and `modifiers`
- `annotations`
- `extendsType`
- `implementsInterfaces` (for interfaces, this holds extended interfaces — see §3.5)
- `types` (nested classes)
- `fields`
- `methods`

### 3.2 Fields

Extracted for classes/enums/records:
- `name`
- `type`
- `visibility`
- `modifiers`
- `annotations`

### 3.3 Methods and Constructors

Extracted per type:
- `name`
- `returnType` (`null` for constructors)
- `isDeclarationOnly` (`true` when the declaration has no body)
- `visibility`
- `modifiers`
- `annotations`
- `parameters` (`name`, `type`)
- `localVariables`
- `methodCalls`

Constructors are represented as methods with the class/record name and no return type.

`isDeclarationOnly` is omitted when `false`.

### 3.5 Interface Extends Mapping

When an interface extends other interfaces, the parent interfaces are stored in `implementsInterfaces` (not `extendsType`). This is a deliberate reuse of the same field to keep the model uniform across type kinds.

### 3.4 Method Calls

Method call facts:
- `methodName`
- `objectName`
- `objectType` (when syntactically resolvable from locals/params/fields)
- `callCount`
- `parameterCount`

For chained invocations where receiver type is not syntactically known, `objectName`/`objectType` can be `null`.

Qualified-call handling follows `docs/EXTRACTION_CONTRACT.md` (§3.2).

---

## 4. Current Limitations

- No semantic resolution beyond local syntactic context.
- No file-level executable statement extraction.
- Anonymous/local class semantics are extraction-only and may be partial.
- Interface constant fields (`public static final` constants declared in interfaces) are not extracted.

---

## 5. Test Baseline

Normative behavior is validated by:

`src/test/java/org/dxworks/codeframe/analyzer/java/JavaAnalyzeApprovalTest.*`

Sample inputs:

`src/test/resources/samples/java/`
