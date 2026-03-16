# CodeFrame Agent Guide

This file is the primary guide for AI contributors working in this repository.

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
- Language output contracts: `docs/specs/*_SPEC.md`
