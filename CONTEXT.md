# CodeFrame Context

## Mission

CodeFrame is a multi-language structural analyzer.

It emits deterministic, source-grounded structural facts as JSONL for downstream post-processing and LLM interpretation.

This file provides shared project vocabulary and context. Normative behavior lives in the canonical docs.

## Core domain terms

### Structural fact

A fact directly grounded in source syntax. This is the unit CodeFrame emits.

### Analysis

The structured output for a file or run.

### Extraction contract

The shared cross-language rules in `docs/EXTRACTION_CONTRACT.md`. This is the canonical source for behavior that should stay consistent across analyzers.

### Language spec

A normative document in `docs/specs/*_SPEC.md` that defines language-specific output shape and extraction behavior.

### Structural stub

The downstream representation built from CodeFrame output for later LLM interpretation. CodeFrame emits the structural facts used to build it, but does not build the interpretation itself.

## Canonical docs

Use `docs/INDEX.md` to find the source of truth for usage, architecture, extraction rules, contributor workflow, and language-specific contracts.

## ADRs

Durable architectural decisions belong in `docs/adr/`.
