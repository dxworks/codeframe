# PHP Specification (CodeFrame)

## Purpose

This document defines the PHP analysis contract for CodeFrame.

It is normative for extracted output shape and semantics.

---

## 1. Language Scope

- File extension: `.php`
- Parser model: syntax extraction only

---

## 2. Output Contract

PHP uses `FileAnalysis`.

Top-level fields:
- `filePath`
- `language` = `"php"`
- `imports` (namespace declaration and `use` declarations)
- `types` (classes/interfaces/traits/enums)
- `methods` (standalone functions)
- `fields` (file-level constants)
- `methodCalls` (top-level calls from expression statements)

`packageName` is not used.

---

## 3. Extraction Rules

### 3.1 Types

`types[].kind` can be:
- `class`
- `interface`
- `trait`
- `enum`

Type metadata:
- `name`
- `visibility`
- `modifiers`
- `annotations` (PHP 8 attributes where applicable)
- `extendsType`
- `implementsInterfaces`
- `mixins` (trait `use` inclusion)
- `fields`
- `methods`
- `types` (nested classes, when present)

Enum behavior:
- enum cases are emitted as `fields` with `modifiers: ["case"]`
- backed enum type is stored in `extendsType`

### 3.2 Fields

Class/trait fields from property declarations:
- `name` (without `$`)
- `type`
- `visibility`
- `modifiers`

File-level `const` declarations are emitted in root `fields`.

### 3.3 Methods and Functions

Type methods (class/trait/enum/interface) include:
- `name`
- `returnType`
- `visibility`
- `modifiers`
- `annotations`
- `parameters`
- `localVariables`
- `methodCalls`

Standalone functions include:
- `name`
- `returnType`
- `annotations`
- `parameters`
- `localVariables`
- `methodCalls`

`visibility` and `modifiers` are not emitted for standalone functions.

Variadic parameters are represented with `...` prefix in `parameters[].name`.

### 3.4 Method Calls

Captured call kinds:
- function call: `func()`
- member call: `$obj->method()`
- scoped call: `Class::method()`

Each call can include:
- `methodName`
- `objectName`
- `objectType`
- `callCount`
- `parameterCount`

### 3.5 Visibility Defaults

Visibility is emitted only when explicitly present in source declarations.

Implicit language defaults (for example, PHP's default method/property visibility) are not emitted.

---

## 4. Current Limitations (V1)

- No DocBlock semantic extraction.
- No runtime/include/require resolution.
- Global variable extraction is not part of V1 (top-level constants only).

---

## 5. Test Baseline

Normative behavior is validated by:

`src/test/java/org/dxworks/codeframe/analyzer/php/PhpAnalyzeApprovalTest.*`

Sample inputs:

`src/test/resources/samples/php/`
