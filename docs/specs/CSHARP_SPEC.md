# C# Specification (CodeFrame)

## Purpose

This document defines the C# analysis contract for CodeFrame.

It is normative for extracted output shape and semantics.

---

## 1. Language Scope

- File extension: `.cs`
- Parser model: syntax extraction only (no Roslyn semantic model)

---

## 2. Output Contract

C# uses `FileAnalysis`.

Top-level fields:
- `filePath`
- `language` = `"csharp"`
- `packageName` (namespace, including file-scoped namespace)
- `imports` (`using` directives)
- `types` (classes/interfaces/enums/records)

`fields`, `properties`, and `methods` are emitted under `types`; `methodCalls` are emitted under `methods` and property `accessors`. File-level statement extraction is not part of V1.

---

## 3. Extraction Rules

### 3.1 Types

`types[].kind` can be:
- `class`
- `interface`
- `enum`
- `record`

Type metadata:
- `name` (including generic parameters)
- `visibility`
- `modifiers`
- `annotations` (attributes)
- `extendsType`
- `implementsInterfaces`
- `fields`
- `properties`
- `methods`
- `types` (nested classes)

### 3.2 Fields and Properties

Class/record fields:
- `name`, `type`, `visibility`, `modifiers`, `annotations`

Properties:
- `name`, `type`, `visibility`, `modifiers`, `annotations`
- `accessors` (`get`/`set`) with:
  - `kind`
  - optional `visibility`, `modifiers`, `annotations`
  - optional `localVariables` and `methodCalls`

Enum members are modeled as `fields` with `type` equal to the enum name.

Record primary-constructor parameters are modeled as `fields`.

### 3.3 Methods and Constructors

Methods include:
- regular methods
- constructors (as methods named by type, with `returnType = null`)
- expression-bodied methods (`=>`) are analyzed as method bodies

Properties with expression bodies (`=>`) are represented with a synthetic `get` accessor body.

Method metadata:
- `name`, `returnType`, `visibility`, `modifiers`, `annotations`
- `parameters`
- `localVariables`
- `methodCalls`

### 3.4 Method Calls

Method calls include:
- direct invocations
- property access modeled as synthetic calls:
  - getter: `get_PropertyName`
  - setter: `set_PropertyName`

Each call can include:
- `methodName`
- `objectName`
- `objectType` (syntactic heuristic)
- `callCount`
- `parameterCount` for invocation calls

Synthetic property calls (`get_*` / `set_*`) do not include `parameterCount`.

---

## 4. Current Limitations (V1)

- No semantic binding/type resolution beyond local syntax.
- Top-level statements/global function programs are not a primary extraction target.
- Extension-method receiver semantics are syntactic only.
- `struct` declarations are not extracted in V1.

---

## 5. Test Baseline

Normative behavior is validated by:

`src/test/java/org/dxworks/codeframe/analyzer/csharp/CSharpAnalyzeApprovalTest.*`

Sample inputs:

`src/test/resources/samples/csharp/`
