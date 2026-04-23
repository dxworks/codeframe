---
name: check-spec
description: Validate whether analyzer output conforms to the repository specs in docs/specs for either (1) code snippet + output or (2) input file + output file.
---

# Check Spec Skill

Use this skill to verify output against the canonical specs in `docs/specs`.

## Inputs

Mode A: `snippet` + `output` (+ optional `language`, optional `expected_spec_file`)

Mode B: `input_file_path` + `output_file_path` (+ optional `language`, optional `expected_spec_file`)

## Language to spec mapping (use first)

Normalize language input to lowercase and strip spaces/hyphens/underscores when matching aliases.

| Language / aliases | Spec set (check all) |
| --- | --- |
| `c` | `docs/specs/C_CPP_SPEC.md` + `docs/specs/C_SPEC.md` |
| `cpp`, `c++`, `cc`, `cxx` | `docs/specs/C_CPP_SPEC.md` + `docs/specs/CPP_SPEC.md` |
| `cobol` | `docs/specs/COBOL_SPEC.md` |
| `csharp`, `c#`, `cs` | `docs/specs/CSHARP_SPEC.md` |
| `java` | `docs/specs/JAVA_SPEC.md` |
| `python`, `py` | `docs/specs/PYTHON_SPEC.md` |
| `javascript`, `js` | `docs/specs/JAVASCRIPT_SPEC.md` |
| `typescript`, `ts` | `docs/specs/TYPESCRIPT_SPEC.md` |
| `php` | `docs/specs/PHP_SPEC.md` |
| `ruby`, `rb` | `docs/specs/RUBY_SPEC.md` |
| `rust`, `rs` | `docs/specs/RUST_SPEC.md` |
| `sql`, `tsql`, `t-sql` | `docs/specs/SQL_SPEC.md` |
| `markdown`, `md` | `docs/specs/MARKDOWN_SPEC.md` |
| `xml` | `docs/specs/XML_SPEC.md` |

If any mapped spec file does not exist, return `FAIL`.

If language is missing/unmapped, return `FAIL` (do not attempt discovery).

If multiple mappings could apply for the same provided language input, return `FAIL`.

## Procedure

1. Resolve the spec set from language using the mapping table; if language is missing/unmapped/ambiguous, or any mapped file is missing, return `FAIL`.
2. Read each resolved spec file in full.
3. Read `docs/EXTRACTION_CONTRACT.md` in full.
4. Compare output against requirements from the full resolved spec set and extraction contract.
5. Report compliant items, violations, and ambiguities.
6. For each violation, cite the exact spec file and section heading.
7. If possible, suggest the minimal fix to output generation logic.

## Thoroughness (mandatory)

Check the output in **both directions**, not just one:

- **Sample → output (missing items).** Walk the input sample end-to-end and confirm every construct the spec says should be extracted actually appears in the output. Flag anything present in the sample but missing from the output.
- **Output → sample (extra / duplicated items).** Walk the output end-to-end and confirm every entry traces back to a concrete construct in the sample. Flag items that are not supported by the sample, items with incorrect parent/container placement, and items that appear more than once when the spec requires them to be recorded once.
- **Counts and ordering.** Verify the number of occurrences and the source order match the sample (e.g., siblings, parameters, attributes, children lists).
- **Optional/omitted fields.** Verify that fields the spec says are omitted when empty are actually absent (not serialized as empty arrays/objects), and that required fields are present.

Do not stop at the first violation; enumerate all of them.

## Output format

Return results in this structure:

```markdown
# Spec Check Result
- Verdict: PASS | FAIL | PARTIAL
- Spec Used: docs/specs/<SPEC_FILE>.md (and sections)

## Failures
1. [FAIL|PARTIAL] <short rule name>
   - Evidence: <exact output fragment>
   - Spec: <spec file + section heading>
   - Fix: <minimal actionable fix>

## Gaps / Ambiguities
- <items that could not be fully validated>

## Next Action
- <single most useful next step>
```

## Guardrails

- Do not invent spec rules not present in `docs/specs`.
- Do not silently assume missing output is valid; mark as FAIL or AMBIGUOUS.
- Do not include PASS evidence; include details only for FAIL/PARTIAL and ambiguities.
- Keep feedback concrete and implementation-oriented.
- Prefer minimal, root-cause fixes over broad refactors.
