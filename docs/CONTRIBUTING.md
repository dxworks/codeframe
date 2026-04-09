# Contributing to CodeFrame

## Adding a New Language

1. Add the Tree-sitter grammar dependency to `build.gradle`
2. Add the language to the `Language` enum with its file extensions
3. Create a new analyzer implementing `LanguageAnalyzer`
4. Add a case for the new language in `LanguageRegistry.createAnalyzer`
5. Add the language key to `codeframe-config.yml` under `analyzers` with default `true`
6. Update README analyzer configuration docs (`Available analyzer keys`) to include the new key
7. Create test samples and approval tests

## Analyzer Implementation Conventions

Follow these conventions to ensure consistency across all analyzers.

### Spec Authoring Rule

- Language specs in `docs/specs/*_SPEC.md` are normative for language-specific behavior.
- Shared cross-language extraction rules should live in canonical docs (for example `docs/EXTRACTION_CONTRACT.md`) and be referenced from specs to avoid duplication.
- Document the final extraction contract in normative docs; avoid temporary option labels or decision-history wording.
- When analyzer behavior changes, update the relevant spec and approval baselines in the same change.

### Extraction Contract (Canonical)

Analyzer extraction behavior is defined in:

- `docs/EXTRACTION_CONTRACT.md`

Use this as the single source of truth for shared extraction rules (method calls, nested functions, file-level extraction, helper conventions, visibility conventions).

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
