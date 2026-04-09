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
- `enum class`
- `namespace`
- `typedef` (for `using` aliases and old-style `typedef` declarations)

`language` is always `"cpp"`.

---

## 3. C++-Specific Extraction Rules

### 3.1 Classes and inheritance

- Extract classes with:
  - `name`, `kind`, `fields`, `methods`, nested `types`
  - inheritance list in `extendsType` (raw base-specifier text, including multiple bases)

Example — given:

```cpp
class Widget : public Drawable, public Serializable {
public:
    void render();
};
```

Extracted type:

```json
{ "kind": "class", "name": "Widget",
  "extendsType": ": public Drawable, public Serializable",
  "methods": [{"name": "render", "returnType": "void", "visibility": "public", "modifiers": ["public"]}] }
```

### 3.2 Free functions

- Extract free functions at namespace/file scope as `methods`.
- Declaration-only entries (no function body) are emitted with `isDeclarationOnly: true`.
- Per `C_CPP_SPEC.md` §4.2, `isDeclarationOnly` is true-only output: definitions omit the field (no explicit `false`).
- In C++, declaration-only includes plain prototypes, pure-virtual declarations (`= 0`), deleted declarations (`= delete`), and defaulted declarations (`= default`).
- Preserve `returnType` and parameter `type` text in source-like form when syntactically clear (for example `const char *`, `HeaderConfig *`, `Widget &`, `int (*cb)(int)`).
- Unnamed parameters are extracted per `C_CPP_SPEC.md` §4.2 (`name: ""` with preserved `type`).

### 3.3 Constructors and destructors

- Constructors/destructors are represented as methods with `returnType = null`.

### 3.4 Operator overloads

- Operator overloads are represented as methods with textual names (for example `operator+`).
- Assignment overloads are represented as textual operator names as well (for example `operator=`).

### 3.5 Templates

- Template declarations are captured with template parameter text attached to names.
- The `template` keyword is captured in `modifiers[]`, while keeping template-parameter text attached to the name.
- No template instantiation semantics are performed.

Example — given:

```cpp
template <typename T>
T identity(T input) { return input; }
```

Extracted method:

```json
{"name": "identity<typename T>", "returnType": "T", "modifiers": ["template"],
 "parameters": [{"name": "input", "type": "T"}]}
```

### 3.6 File-scope declarations

- File-scope variable/constant `fields[].type` preserves source-like declarator text where present.
- `modifiers` are captured per the shared spec (§4.5).

### 3.7 Access specifiers (visibility)

C++ class member access specifiers map to `visibility` and are included in `modifiers`, consistent with Java/C#/TypeScript:

- `public`, `private`, `protected` → `visibility` field + entry in `modifiers[]`

Example — given:

```cpp
class Foo {
private:
    int secret;
public:
    void show();
};
```

Extracted:

```json
{ "kind": "class", "name": "Foo",
  "fields": [{"name": "secret", "type": "int", "visibility": "private", "modifiers": ["private"]}],
  "methods": [{"name": "show", "returnType": "void", "visibility": "public", "modifiers": ["public"]}] }
```

### 3.8 C++ method/function modifiers

The following C++ keywords are captured as `modifiers[]` when syntactically present on a method or function:

- `virtual`, `override`, `final`
- `static`, `inline`, `constexpr`, `consteval`
- `explicit` (constructors)
- `const` (trailing member-function qualifier)
- `noexcept`
- `friend` for friend function declarations extracted as methods
- `deleted` for declarations that use `= delete`
- `defaulted` for declarations that use `= default`

Note: `pure` (for pure-virtual declarations like `= 0`) is currently not extracted.
Tree-sitter C++ does not consistently expose a stable `pure_virtual_clause` node shape for all declarations we analyze,
so this analyzer intentionally does not emit a `pure` modifier.

Example — given:

```cpp
class Base {
public:
    virtual void draw() const;
    static int count();
};
class Derived : public Base {
public:
    void draw() const override;
};
```

Extracted methods on `Base`:

```json
{"name": "draw", "returnType": "void", "visibility": "public", "modifiers": ["public", "virtual", "const"]}
{"name": "count", "returnType": "int", "visibility": "public", "modifiers": ["public", "static"]}
```

Extracted methods on `Derived`:

```json
{"name": "draw", "returnType": "void", "visibility": "public", "modifiers": ["public", "const", "override"]}
```

### 3.8.1 C++ field modifiers

- Class/struct fields capture source-level specifier modifiers when syntactically present.
- This includes `mutable` on class fields.

### 3.9 Namespaces

- Namespaces are extracted as **container types** with `kind: "namespace"`, consistent with Rust `mod` handling.
- Types, methods, and fields declared inside a namespace are nested under that namespace type.
- Nested namespaces produce nested container types.
- Anonymous namespaces use an empty or synthetic name.

Example — given:

```cpp
namespace math {
    class Vector {
    public:
        int magnitude();
    };
    int sum(int a, int b) { return a + b; }
}
```

Extracted:

```json
{"types": [{
    "kind": "namespace", "name": "math",
    "types": [{"kind": "class", "name": "Vector",
        "methods": [{"name": "magnitude", "returnType": "int", "visibility": "public", "modifiers": ["public"]}]}],
    "methods": [{"name": "sum", "returnType": "int"}]
}]}
```

### 3.10 Scoped enums (`enum class`)

- `enum class` declarations are extracted as types with `kind: "enum class"`.
- The name is the unqualified enum name.
- Enums with explicit underlying type (for example `enum Priority : int`) are extracted as normal enum/enum class types.
- Underlying enum base type is not captured separately.

Example — given:

```cpp
enum class Color { RED, GREEN, BLUE };
```

Extracted type:

```json
{"kind": "enum class", "name": "Color", "fields": [{"name": "RED"}, {"name": "GREEN"}, {"name": "BLUE"}]}
```

### 3.11 `using` type aliases

- `using` type aliases are extracted as types with `kind: "typedef"` and alias target in `extendsType`.
- Consistent with C typedef representation.
- Old-style C++ `typedef` declarations are also extracted as `kind: "typedef"`, with alias target preserved in `extendsType`.
- Alias target text preservation follows the shared rule in `C_CPP_SPEC.md` §4.4 (including compact targets for named inline aggregate typedefs such as `typedef struct X { ... } X;` → `extendsType: "struct X"`).

Example — given:

```cpp
using IntVec = std::vector<int>;
```

Extracted type:

```json
{"kind": "typedef", "name": "IntVec", "extendsType": "std::vector<int>"}
```

### 3.12 Lambdas

- Lambda expressions are **not** extracted as standalone types or methods.
- Calls made inside a lambda body are captured as calls within the enclosing function.

### 3.13 `auto` type

- When `auto` is the source-level return type or variable type, it is preserved as `"auto"` in type text.
- No type deduction is performed.

### 3.14 Default/deleted functions

- `= default` and `= delete` suffixed functions are extracted as normal methods.
- `= default`/`= delete` are captured via method modifiers:
  - `= default` -> `"defaulted"`
  - `= delete` -> `"deleted"`
- No additional dedicated field is emitted for these suffixes.

### 3.15 Multiple inheritance

- The full base-specifier list is preserved as-is in `extendsType`.
- See §3.1 example.

### 3.16 Friend declarations

- Friend function declarations/definitions inside classes are extracted as methods on the containing class.
- Extracted friend methods include `"friend"` in `modifiers[]`.
- Friend class declarations are not extracted as methods.

---

## 4. Current Limitations

In addition to shared limitations from `C_CPP_SPEC.md`:

- No overload resolution.
- No namespace semantic binding (qualified lookup, ADL) — extraction is structural only (§3.9).
- Qualified-call handling follows `docs/EXTRACTION_CONTRACT.md` (§3.2); for example, `util::f()` records `methodName: "f"`.
- Pure virtual marker extraction (`= 0` → `pure`) is not supported due to Tree-sitter C++ AST shape inconsistency.
- No C++20 module semantic analysis.
- Lambda expressions are not standalone entities (§3.12).
- No `auto` type deduction (§3.13).

---

## 5. Test Baseline

Normative behavior is validated by:

- `src/test/java/org/dxworks/codeframe/analyzer/cpp/CppAnalyzeApprovalTest.*`

Sample inputs:

- `src/test/resources/samples/cpp/`
