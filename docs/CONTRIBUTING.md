# Contributing to CodeFrame

## Adding a New Language

1. Add the Tree-sitter grammar dependency to `build.gradle`
2. Add the language to the `Language` enum with its file extensions
3. Create a new analyzer implementing `LanguageAnalyzer`
4. Add a case for the new language in `LanguageRegistry.createAnalyzer`
5. Create test samples and approval tests

## Analyzer Implementation Conventions

Follow these conventions to ensure consistency across all analyzers.

### Extraction Contract (All Parsers)

- Extract facts only. Do not infer capabilities or intent.
- Keep output deterministic.
- Do not write files as side effects.
- On parse errors, return partial results when possible.
- Keep analyzers simple and fast; enrichment belongs to post-processing tools.

### Using TreeSitterHelper

`TreeSitterHelper` provides shared utilities that all analyzers should use:

| Method | Purpose |
|--------|---------|
| `extractName(source, node, childType)` | Extract name from a child node by type |
| `collectMethodCall(...)` | Add method call with deduplication |
| `identifyNestedNodes(...)` | Identify nested class/type declarations |
| `renderObjectName(...)` | Build object name for chained expressions |
| `isValidIdentifier(name)` | Validate identifier syntax |
| `METHOD_CALL_COMPARATOR` | Sort method calls consistently |
| `findFirstChild`, `findAllDescendants` | Node traversal helpers |

Use these helpers to maintain consistency and reduce duplication across analyzers.

### Method Call Extraction

**Chained Method Calls:**
- For chained calls where the receiver is the result of another method call (e.g., `obj.method1().method2()`), set both `objectName` and `objectType` to `null`
- The type cannot be determined without semantic analysis
- Example for `db.query().debug().printTo(output())`:

| Call | `objectName` | `objectType` |
|------|--------------|--------------|
| `query` | `"db"` | `"DatabaseConnection"` |
| `debug` | `null` | `null` (chained) |
| `printTo` | `null` | `null` (chained) |
| `output` | `null` | `null` (standalone) |

**Property Access vs Method Calls:**
- **C#**: Record property accesses as `get_PropertyName` or `set_PropertyName` method calls
- **Ruby**: Only record calls with arguments or special suffixes (`?`, `!`); skip simple property accessors
- **Java**: Record explicit getter/setter method calls; field access is not a method call

**Object Type Resolution:**
- Resolve `objectType` from local variables, parameters, or fields when possible
- Use `null` when the type cannot be determined from syntax alone

### Nested Functions

Nested functions (functions defined inside other functions) are **NOT** extracted as separate top-level methods. Instead:

- The nested function's **method calls** are captured in the parent function's `methodCalls`
- The nested function's **local variables** are captured in the parent function's `localVariables`
- This is achieved by using `findAllDescendants` when analyzing method bodies, which traverses into nested scopes

**Rationale:**
- Prevents duplicate function names in output (e.g., multiple `wrapper` functions from different decorators)
- Maintains correct semantic grouping - nested functions are implementation details of their parent
- Consistent behavior across Python, JavaScript, and TypeScript

**Example:**
```python
def outer():
    def inner():       # NOT extracted as separate method
        helper()       # Captured in outer.methodCalls
    inner()            # Captured in outer.methodCalls
```

### File-Level Fields and Method Calls

All analyzers should extract file/module-level elements into the `FileAnalysis` object:

**`fields`** - Module-level constants and variables:
- **Python**: Top-level assignments with type annotations (e.g., `MAX_RETRIES: int = 3`)
- **JavaScript/TypeScript**: `const`, `let`, `var` declarations at module level
- **Ruby**: Module-level constants (e.g., `MAX_SIZE = 100`)
- **PHP**: Global variables and constants
- **Java/C#**: Static fields are captured at the class level (not file level)

**`methodCalls`** - Top-level function calls outside any class/function:
- Use `findAllDescendants` to capture ALL calls within top-level expression statements
- This includes calls inside callbacks (e.g., `describe()` containing `test()` and `expect()`)
- Calls inside named functions/methods are captured in that method's `methodCalls`, not at file level

**Example (Python):**
```python
MAX_RETRIES: int = 3          # → fields: [{name: "MAX_RETRIES", type: "int"}]
logger = logging.getLogger()  # → fields: [{name: "logger"}], methodCalls: [{methodName: "getLogger"}]
```

**Example (JavaScript):**
```javascript
const API_VERSION = "1.0";    // → fields: [{name: "API_VERSION", type: "String"}]
console.log("Starting...");   // → methodCalls: [{methodName: "log", objectName: "console"}]
```

### Field/Variable Visibility

- **Ruby**: Instance variables (`@var`) are private by default
- **Java/C#**: Use explicit modifiers
- **Python**: Leading underscore (`_var`) indicates private by convention

### Test Coverage

- Create at least 3 sample files covering:
  1. Basic class/method structure with method calls
  2. Modules/interfaces/mixins (if applicable)
  3. Inheritance and polymorphism
- Use ApprovalTests framework for output verification
- Include chained method calls to verify correct handling

## Testing

### Running Tests

```bash
# All tests
./gradlew test

# Single test
./gradlew test --tests "*JavaAnalyzeApprovalTest.analyze_Java_GenericsSample"
```

### ApprovalTests Workflow

1. Make a change
2. Run tests
3. Inspect `.received.txt` if test fails
4. Approve if expected: rename `.received.txt` to `.approved.txt`
5. Commit both code and updated `.approved.txt`
