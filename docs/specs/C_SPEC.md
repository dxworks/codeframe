# C Specification (CodeFrame)

## Purpose

This document defines the C analysis contract for CodeFrame.

It is normative for C-specific behavior and output semantics.

Shared rules are defined in **[C/C++ Common Specification](C_CPP_SPEC.md)** and are normative for C unless explicitly overridden here.

---

## 1. Language Scope

- File extensions: `.c`, `.h` (family scope)
- Parser: Tree-sitter C (`CAnalyzer`)
- Parser model: syntax extraction only

Header ownership for `.h` follows the shared deterministic policy in `C_CPP_SPEC.md`.
In practice, `.h` may be analyzed by `CAnalyzer` or `CppAnalyzer` depending on parse-quality fallback.

---

## 2. Output Contract (C Deltas)

C uses `FileAnalysis` as defined in the shared spec.

C-specific type kinds:
- `struct`
- `union`
- `enum`
- `typedef`

`language` is always `"c"`.

---

## 3. C-Specific Extraction Rules

### 3.1 File-scope functions

- Function declarations/definitions at file scope are extracted as `methods`.
- Method `returnType` and parameter `type` preserve C declarator syntax where present (for example `const char *`, `void *restrict`, `int (*cb)(int)`).

### 3.2 Type declarations

- `struct`/`union` fields are extracted as `types[].fields`.
- `enum` declarations are extracted as `types` with enum members represented as fields.

### 3.3 Typedef aliases

- Typedef aliases are represented as `types` with:
  - `kind: "typedef"`
  - alias target preserved as text in `extendsType`

### 3.4 Function pointer types

- Function pointer declarations are preserved as source-like text, not decomposed into a deep semantic model.
- Example: `int (*cmp)(const void*, const void*)` is kept as textual type information (for example in `extendsType`), rather than split into pointer-layer/type-system objects.
- File-scope field `type` follows the same source-like preservation principle.
- Out of scope in V1: typedef-chain resolution, compatibility inference, and calling-convention semantics.

### 3.5 Storage-class and qualifier modifiers

C functions and file-scope fields capture `modifiers[]` per the shared spec (§4.2, §4.5).

C-relevant modifier keywords:
- Functions: `static`, `extern`, `inline`
- Fields: `static`, `extern`, `const`, `volatile`

C has no `visibility` field — all file-scope declarations have implicit external or internal linkage determined by `static`/`extern`, but no access-specifier concept.

Example — given:

```c
static int internal_helper(int x) { return x; }
extern void public_api(const char *msg);
```

Extracted methods:

```json
{"name": "internal_helper", "returnType": "int", "modifiers": ["static"]}
{"name": "public_api", "returnType": "void", "modifiers": ["extern"]}
```

### 3.6 Bitfields

- Struct bitfield declarations are extracted as fields.
- The `type` preserves the base type; the bit-width (e.g., `: 1`) is not captured.

Example — given:

```c
struct Flags {
    unsigned int readable : 1;
    unsigned int writable : 1;
};
```

Extracted type:

```json
{"kind": "struct", "name": "Flags",
  "fields": [{"name": "readable", "type": "unsigned int"}, {"name": "writable", "type": "unsigned int"}]}
```

### 3.7 Forward declarations

- Bodyless forward declarations (`struct Foo;`) are skipped — no type is emitted.
- This follows the shared rule in `C_CPP_SPEC.md` §4.4.

---

## 4. Current Limitations (C V1)

In addition to shared limitations from `C_CPP_SPEC.md`:

- No semantic typing for pointer alias chains.
- No ABI/calling-convention interpretation.
- Bitfield width is not captured (§3.6).

---

## 5. Test Baseline

Normative behavior is validated by:

- `src/test/java/org/dxworks/codeframe/analyzer/c/CAnalyzeApprovalTest.*`

Sample inputs:

- `src/test/resources/samples/c/`
