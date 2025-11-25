# Contributing to CodeFrame

## Adding a New Language

1. Add the Tree-sitter grammar dependency to `build.gradle`
2. Add the language to the `Language` enum
3. Update `LanguageDetector` with file extension mapping
4. Create a new analyzer implementing `LanguageAnalyzer`
5. Register the language and analyzer in `App.java`
6. Create test samples and approval tests

## Analyzer Implementation Conventions

Follow these conventions to ensure consistency across all analyzers.

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
