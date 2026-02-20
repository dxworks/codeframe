# COBOL Specification (CodeFrame)

## Purpose

This document defines the **COBOL analysis specification** for CodeFrame.

It describes:
- what COBOL facts are extracted,
- why these facts are part of the model,
- what is in scope vs out of scope,
- the explicit COBOL limitations for the current version.

This is a **normative spec** for COBOL behavior and output shape. It is not an implementation plan.

---

## 1. COBOL Language Scope

### 1.1 Supported COBOL dialect/version

- Target language: **COBOL85**.

### 1.2 Source format

- Source format is **fixed format only** (80-column COBOL).
- Format auto-detection is out of scope.

### 1.3 File extensions in scope

- `.cbl`
- `.cob`
- `.cobol`

### 1.4 Copybook files (`.cpy`)

- `.cpy` files are not analyzed as standalone COBOL structural artifacts in this spec version.
- Copybook content is expected to be embedded where `COPY` statements are used (see Section 4).

---

## 2. COBOL Output Contract

COBOL analysis output is represented by `COBOLFileAnalysis`.

### 2.1 Top-level fields

- `filePath`: analyzed file path.
- `language`: always `"cobol"`.
- `programId`: COBOL `PROGRAM-ID`.
- `fileControls`: logical file definitions from `FILE-CONTROL SELECT` clauses.
- `dataItems`: data hierarchy extracted from DATA DIVISION sections.
- `fileDefinitions`: FD-based file record definitions.
- `copyStatements`: copybook names referenced via `COPY`.
- `procedureParameters`: names from `PROCEDURE DIVISION USING`.
- `sections`: named procedure sections with nested paragraphs.
- `paragraphs`: top-level procedure paragraphs (including synthetic prologue when applicable).
- `hasExecSql`: whether EXEC SQL appears.
- `hasExecCics`: whether EXEC CICS appears.
- `hasExecSqlIms`: whether EXEC SQLIMS appears.

### 2.2 COBOL-specific model intent

The model is designed around COBOL compilation-unit semantics (program/divisions/paragraphs/data hierarchy), not class/method semantics.

---

## 3. Semantic Extraction Rules

### 3.1 IDENTIFICATION DIVISION

- Extract `PROGRAM-ID` into `programId`.
- Other identification entries are not primary contract fields.

### 3.2 ENVIRONMENT DIVISION: FILE-CONTROL

Each `SELECT` contributes a `COBOLFileControl` with:
- `name`
- `organization` (when present)
- `accessMode` (when present)
- `hasKey` (true when `RECORD KEY` is present)

Physical assignment targets are not part of the current contract.

### 3.3 DATA DIVISION: data hierarchy

`dataItems` capture hierarchical COBOL data declarations from:
- WORKING-STORAGE SECTION
- LOCAL-STORAGE SECTION
- LINKAGE SECTION
- FILE SECTION (as applicable)

Per item semantics:
- `name`, `level`, `picture`, `section`
- optional `usage`, `redefines`, `occurs`
- `children` preserves nesting by level numbers (01/05/10/77/88, etc.)

#### 88-level condition names

Level-88 condition names are included as children of their parent data item. They carry `name`, `level: 88`, and `section` (inherited from parent). The `VALUE` clause is **not** captured (consistent with Decision #12 in the research doc — no VALUE extraction for any level).

### 3.4 FILE SECTION: fileDefinitions

`fileDefinitions` represent FD ownership:
- `name`: FD file name
- `records`: record layouts belonging to that FD

Record ownership is FD-scoped (records belong to the corresponding FD, not shifted across FDs).

### 3.5 PROCEDURE DIVISION USING

- Names in `PROCEDURE DIVISION USING ...` are extracted into `procedureParameters`.
- Stored as names only.

### 3.6 Sections and paragraphs

- Named sections are emitted in `sections`.
- Paragraphs inside sections are emitted in `sections[].paragraphs`.
- Paragraphs outside sections are emitted in top-level `paragraphs`.

### 3.7 Procedure prologue behavior

When executable statements exist in PROCEDURE DIVISION before the first named paragraph, they are captured in a synthetic paragraph:
- `__PROCEDURE_DIVISION_PROLOGUE__`

This paragraph is part of top-level `paragraphs`.

### 3.8 CALL statements

Paragraph-level `externalCalls` capture:
- `programName`
- `isDynamic`
- `parameterCount` (when determinable)

Calls are represented at paragraph level (including prologue paragraph where applicable).

### 3.9 PERFORM statements

Paragraph-level `performCalls` capture:
- `targetParagraph`
- optional `thruParagraph`

### 3.10 File operations

Paragraph-level `fileOperations` capture operation + target for:
- `READ`
- `WRITE`
- `OPEN`
- `CLOSE`
- `REWRITE`
- `DELETE`
- `START`

Each file operation has:
- `verb`: the operation keyword.
- `target`: the operand of the verb. For `OPEN`/`CLOSE`, this is the COBOL file name (e.g., `STMT-FILE`). For `WRITE`, this is the record name (e.g., `FD-STMTFILE-REC`). For `READ`, this is the file name. The field is named `target` (not `fileName`) because different verbs operate on different entity types.

### 3.11 Control-flow statements

Paragraph-level `controlFlowStatements` capture explicit control flow:
- `GOBACK`
- `STOP RUN`
- `EXIT PROGRAM`
- `RETURN`

`RETURN` may include a `target` when syntactically present.

**Note**: A bare `EXIT` statement (without `PROGRAM`) is a COBOL no-op placeholder and is **not** captured as a control-flow statement.

### 3.12 Data references

Paragraph-level `dataReferences` capture identifier references in executable statements, including:
- assignment/movement statements (`MOVE`),
- arithmetic statements (`ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE`, `COMPUTE`),
- `SET` statements (including condition names),
- call-argument identifiers (`CALL USING`),
- `STRING`/`UNSTRING` operands and targets,
- `EVALUATE` subject identifiers,
- `INITIALIZE` target identifiers,
- `DISPLAY` identifier operands.

Identifiers are captured as names. Literal constants are excluded.

Subscripted references preserve their subscript expressions (e.g., `WS-TRAN-NUM(CR-JMP, TR-JMP)`).

### 3.13 EXEC block presence flags

The analysis records presence flags for:
- EXEC SQL
- EXEC CICS
- EXEC SQLIMS

The inner content of EXEC blocks is not modeled in this spec.

---

## 4. Copybook Semantics

### 4.1 Required copybook output

- `copyStatements` must contain referenced copybook names from `COPY` statements.

### 4.2 Expansion expectation

- COPY content is expected to be expanded into parser-visible source when copybooks are resolvable.
- `copyStatements` still records referenced copybook names from source files.
- Parsing note: copybooks are often grammar fragments rather than full compilation units; expansion therefore depends on a parsing-compatibility strategy for copybook fragments.

### 4.3 Duplicate copybook names

Decision: when multiple copybooks share the same name in different directories, resolution uses the current first-match rule.

- Matching is by copybook base name (case-insensitive).
- If multiple candidates match, the first candidate in the provided copybook list is selected.
- This behavior is deterministic for a stable input ordering and must be validated by approval output.

---

## 5. Current Limitations (V1)

- COBOL scope is limited to COBOL85.
- Fixed-format source only (no variable/free-format mode).
- No semantic interpretation of EXEC block content.
- No standalone structural extraction output for `.cpy` files.
- COPY expansion applies only to resolvable copybooks; unresolved copybooks are skipped (non-fatal).
- `dataReferences` are paragraph-level merged references; statement-level attribution is not modeled.
---

## 6. Out of Scope (for this spec version)

The following are outside the current COBOL specification baseline:
- COBOL 2002+ language features.
- Semantic/data-flow analysis beyond structured extraction fields.
- Cross-program behavioral inference.
- Non-COBOL artifacts (e.g., JCL, BMS, assembler) in this COBOL model.

---

## 7. Future COBOL Enhancements (Non-normative)

Potential future expansions include:
- richer copybook resolution contracts,
- broader COBOL version coverage,
- deeper semantic extraction where explicitly required by a revised specification.
