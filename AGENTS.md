# CodeFrame Agent Guide

This file is the primary guide for Codex and contributors working in this repository.

## Design Guidelines

- Use as little regex as possible.
- Do not add complexity only for backward compatibility unless explicitly requested.
- Do not hack toward a solution; prefer grammar/tree understanding and a proper fix.
- For parser/analyzer behavior, follow the global Extraction Contract in `docs/CONTRIBUTING.md`.

## Canonical Reading Order

1. `README.md` for usage, CLI behavior, and configuration.
2. `docs/ARCHITECTURE.md` for component-level design.
3. `docs/CONTRIBUTING.md` for analyzer extraction contract and testing rules.
4. `docs/specs/*_SPEC.md` for language-specific output contracts.

## Sources of Truth

- General behavior: `README.md`
- Analyzer extraction conventions: `docs/CONTRIBUTING.md`
- Language architecture: `docs/ARCHITECTURE.md`
- SQL contract: `docs/specs/SQL_SPEC.md`
- COBOL contract: `docs/specs/COBOL_SPEC.md`
- Markdown contract: `docs/specs/MARKDOWN_SPEC.md`

## Do/Don't for Repo Automation

- Prefer `rg`/`rg --files` for file and text search.
- Keep parser changes deterministic and extraction-only.
- Keep approval tests as the verification baseline for analyzers.
- Treat `src/test/resources/**` as sample fixtures, not project documentation.
- Do not run markdown link/lint checks against fixture files under `src/test/resources/**`.
