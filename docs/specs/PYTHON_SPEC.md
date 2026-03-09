# Python Specification (CodeFrame)

## Purpose

This document defines the Python analysis contract for CodeFrame.

It is normative for extracted output shape and semantics.

---

## 1. Language Scope

- File extension: `.py`
- Parser model: syntax extraction only (no runtime execution/type solving)

---

## 2. Output Contract

Python uses `FileAnalysis`.

Top-level fields:
- `filePath`
- `language` = `"python"`
- `imports` (`import`, `from ... import`, `from __future__ import`)
- `types` (classes and type aliases)
- `methods` (standalone top-level functions)
- `fields` (module-level assignments)
- `methodCalls` (top-level calls from expression statements)

`packageName` is not used.

---

## 3. Extraction Rules

### 3.1 Visibility Convention

Visibility is inferred from naming convention:
- `__name` (not dunder): `private`
- `_name`: `protected`
- otherwise: `public`

### 3.2 Types

`types[].kind` can be:
- `class`
- `type_alias`

Class metadata:
- `name`
- `visibility`
- `annotations` (decorators)
- `extendsType` (first base class)
- `implementsInterfaces` (additional bases)
- `fields` (class attributes)
- `methods`
- `types` (nested classes)

Type aliases are extracted from:
- old-style `TypeAlias` assignments

PEP 695 `type` statements are not currently extracted due to
tree-sitter-python grammar support limitations.

### 3.3 Methods and Functions

For class methods and module-scope functions (including functions declared
inside module-level control blocks such as `if __name__ == "__main__":`):
- `name`
- `returnType` (annotation when present)
- `visibility`
- `annotations` (decorators)
- `modifiers` (`async`, `staticmethod`, `classmethod`, `property` when applicable)
- `parameters` (`name`, `type`)
- `localVariables`
- `methodCalls`

Parameter extraction:
- excludes `self` and `cls`
- keeps variadic prefixes (`*args`, `**kwargs`)

Nested functions are not emitted as separate methods; their calls/locals are included in the parent method/function context.

### 3.4 Fields and File-Level Calls

- Module-level assignments become `fields` (except type-alias declarations).
- Class-level assignments/type annotations become class `fields`.
- Top-level expression calls become file `methodCalls`.

---

## 4. Current Limitations (V1)

- No data-flow or import graph resolution.
- Type inference is limited to simple syntax patterns.
- Complex destructuring/assignment patterns may be partially represented.
- PEP 695 `type` statements are not extracted; only `TypeAlias` assignment style is supported.

---

## 5. Test Baseline

Normative behavior is validated by:

`src/test/java/org/dxworks/codeframe/analyzer/python/PythonAnalyzeApprovalTest.*`

Sample inputs:

`src/test/resources/samples/python/`
