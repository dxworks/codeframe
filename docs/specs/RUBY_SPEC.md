# Ruby Specification (CodeFrame)

## Purpose

This document defines the Ruby analysis contract for CodeFrame.

It is normative for extracted output shape and semantics.

---

## 1. Language Scope

- File extension: `.rb`
- Parser model: syntax extraction only

---

## 2. Output Contract

Ruby uses `FileAnalysis`.

Top-level fields:
- `filePath`
- `language` = `"ruby"`
- `imports` (bare `require` / `require_relative` calls)
- `types` (classes/modules)
- `methods` (standalone methods)
- `fields` (file-level constants)
- `methodCalls` (top-level calls)

`packageName` is not used.

---

## 3. Extraction Rules

### 3.1 Types

`types[].kind` can be:
- `class`
- `module`

Type metadata:
- `name`
- `visibility`
- `modifiers`
- `annotations` (including extracted Rails DSL markers)
- `extendsType`
- `mixins` (`include`, `extend`, `prepend`)
- `fields`
- `properties`
- `methods`
- `types` (nested classes/modules)

### 3.2 Fields and Properties

Type-level fields include:
- instance variables (e.g., `@id`) as private fields
- class/module constants as `const` fields

Ruby accessor macros are mapped to properties:
- `attr_reader` -> `get`
- `attr_writer` -> `set`
- `attr_accessor` -> `get` + `set`

### 3.3 Methods

Method metadata:
- `name` (supports identifiers/setters/operators)
- `visibility`
- `parameters` (includes `&block` form)
- `localVariables`
- `methodCalls`

Visibility sources:
- visibility sections (`private`, `protected`, `public`)
- inline visibility declarations
- naming convention fallback

Visibility behavior applies to methods declared in both classes and modules.

### 3.4 Method Calls

Calls are extracted from `call` and `method_call` nodes (at file-level and method-level) and include:
- `methodName`
- optional `objectName`
- `callCount`
- `parameterCount`

Qualified-call handling follows `docs/EXTRACTION_CONTRACT.md` (§3.2).

At file level, bare `require`/`require_relative` are captured in `imports` and excluded from file-level `methodCalls`.

Receiver-qualified calls (for example `Bundler.require(...)`) are not treated as imports.

### 3.5 Rails DSL Annotations

Recognized Rails-style DSL calls (for example associations, validations, callbacks, scopes) are added to `type.annotations` in normalized form.

### 3.6 Alias Annotations

Ruby alias declarations are captured in `type.annotations`:
- `alias new_name old_name` -> `@alias(new_name=old_name)`
- `alias_method :new_name, :old_name` -> `@alias_method(new_name=old_name)`

---

## 4. Current Limitations

- No runtime metaprogramming evaluation (`define_method`, dynamic constants, etc.).
- No module load-path resolution for `require`.
- Type inference is intentionally minimal.

---

## 5. Test Baseline

Normative behavior is validated by:

`src/test/java/org/dxworks/codeframe/analyzer/ruby/RubyAnalyzeApprovalTest.*`

Sample inputs:

`src/test/resources/samples/ruby/`
