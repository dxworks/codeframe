# Markdown Specification (CodeFrame)

## Purpose

This document defines the **Markdown analysis specification** for CodeFrame.

It describes:
- what Markdown structural facts are extracted,
- why these facts are part of the model,
- what is in scope vs out of scope,
- the explicit Markdown limitations for the current version.

This is a **normative spec** for Markdown behavior and output shape. It is not an implementation plan.

---

## 1. Markdown Language Scope

### 1.1 Supported Markdown specification

- Target specification: **CommonMark** with **GitHub Flavored Markdown (GFM)** extensions.
- Extension modeled explicitly in output: Tables.
- Strikethrough and task-list markers may appear in source but are treated as inline/list content in V1 (no dedicated element types).

### 1.2 File extensions in scope

- `.md`
- `.markdown`
- `.mkd`
- `.mkdn`
- `.mdwn`
- `.mdown`

### 1.3 Out of scope

- `.mdx` (JSX-in-Markdown) — different parsing domain
- Custom Markdown processors or exotic dialects beyond CommonMark + GFM

---

## 2. Markdown Output Contract

Markdown analysis output is represented by `MarkdownFileAnalysis`.

### 2.1 Top-level fields

- `filePath`: analyzed file path.
- `language`: always `"markdown"`.
- `preamble`: Content before the first heading (null if absent).
- `sections`: Hierarchical sections defined by headers (H1-H6).

```text
MarkdownFileAnalysis {
    filePath: String
    language: "markdown"
    preamble: MarkdownSection | null
    sections: List<MarkdownSection>
}
```

### 2.2 Markdown-specific model intent

The model is designed around **document structure semantics** (sections and elements), not code semantics. It captures the outline and content composition of documentation.

`MarkdownFileAnalysis` is a dedicated model because Markdown documents do not have code-centric constructs such as classes, methods, fields, imports, or call graphs.

---

## 3. Semantic Extraction Rules

### 3.1 Document Structure: Headers and Sections

- Headers (H1-H6) define a hierarchical section tree.
- Section nesting follows header level (H1 → H2 → H3).
- Sections with no content (no elements, no subsections) are preserved to keep full document outline fidelity.
- Header text is extracted as the section `heading`.

### 3.2 `MarkdownSection`

```text
MarkdownSection {
    heading: String | null
    level: int
    elements: List<MarkdownElement>
    subsections: List<MarkdownSection>
}
```

### 3.3 Preamble

- Content before the first heading is captured as `preamble` with `level: 0`.
- Preamble is `null` when there is no content before the first heading.

### 3.4 Block Elements

Each section contains an ordered list of block elements preserving document flow:

#### 3.4.1 Paragraphs

- Extracted as `"paragraph"` elements with line count.
- Preserves interleaving order with other elements (code blocks, tables, lists).
- No inline formatting is captured (bold, italic, code spans).

#### 3.4.2 Code Blocks

- **Fenced code blocks**: Extracted as `"code_block"` with `properties.language` when present.
- **Indented code blocks**: Extracted as `"code_block"` without language property.
- Language comes from the fenced code info string (e.g., ```java → `properties.language: "java"`).

#### 3.4.3 Tables

- Extracted as `"table"` elements with line count.
- Only size is captured; no cell/row/column details.
- Requires GFM tables extension for detection.

#### 3.4.4 Lists

- **Bullet lists**: Extracted as `"bullet_list"` elements.
- **Ordered lists**: Extracted as `"ordered_list"` elements.
- List containers include `children` with `"list_item"` elements.
- Each `"list_item"` includes its own `children` preserving block-level content order (for example: paragraph, nested list, code block).
- List item paragraphs are nested under `"list_item"`, not emitted as sibling top-level section elements.
- Task-list items are represented as regular list structures (`"bullet_list"`/`"ordered_list"` and `"list_item"`); checkbox state is not extracted in V1.

#### 3.4.5 Block Quotes

- Extracted as `"block_quote"` elements with line count.
- Nested block quotes are treated as a single element.

#### 3.4.6 Other Block Elements

- **Thematic breaks** (`---`, `***`): Extracted as `"thematic_break"`.
- **HTML blocks**: Extracted as `"html_block"` with line count only.
- **Images**: Extracted as `"image"` with `properties: { "altText": "..." }` (destination URL is not extracted).

**Note**: Links are not extracted because they can include sensitive URLs or internal endpoints, and they are not required for V1 structural analysis.

**Note**: YAML front matter (content between `---` delimiters at file start) is not processed as it's not part of the CommonMark standard.

#### Element types

| Markdown construct | `type` | `properties` | Notes |
|---|---|---|---|
| Fenced code block | `"code_block"` | `{ "language": "java" }` | Language from fenced code info string |
| Indented code block | `"code_block"` | `{}` | No language info available |
| Paragraph | `"paragraph"` | `{}` | Preserves interleaving order with other elements |
| Table (GFM) | `"table"` | `{}` | Size only, no cell/row details |
| Bullet list | `"bullet_list"` | `{}` | `children` contains `list_item` elements |
| Ordered list | `"ordered_list"` | `{}` | `children` contains `list_item` elements |
| List item | `"list_item"` | `{}` | Nested under list containers; `children` preserves item block content order |
| Block quote | `"block_quote"` | `{}` | |
| Thematic break (`---`) | `"thematic_break"` | `{}` | |
| HTML block | `"html_block"` | `{}` | Size only, no content extraction |
| Image | `"image"` | `{ "altText": "..." }` | Destination URL is not extracted |

### 3.5 `MarkdownElement`

```
MarkdownElement {
    type: String                             // Element type (see table below)
    lines: int                               // Number of lines this element spans
    properties: Map<String, Object>          // Optional properties (e.g., language for code blocks, altText for images)
    children: List<MarkdownElement>          // Optional nested elements (used for list hierarchy)
}
```

`properties` and `children` are optional and may be omitted/null when not applicable.

### 3.6 Element Size Calculation

- Element `lines` count includes all lines the element spans in the source.
- Line counts are derived from source position information.
- Empty lines between elements are not counted as part of elements.

### 3.7 Normative JSON Example (shape)

```json
{
  "filePath": "README.md",
  "language": "markdown",
  "preamble": null,
  "sections": [
    {
      "heading": "Usage",
      "level": 1,
      "elements": [
        {
          "type": "bullet_list",
          "lines": 3,
          "children": [
            {
              "type": "list_item",
              "lines": 1,
              "children": [
                { "type": "paragraph", "lines": 1 }
              ]
            }
          ]
        },
        {
          "type": "code_block",
          "lines": 4,
          "properties": { "language": "bash" }
        }
      ],
      "subsections": []
    }
  ]
}
```

In this contract, optional fields (for example `properties`, `children`) may be omitted or null when not applicable.

---

## 4. Current Limitations (V1)

- Inline formatting (bold, italic, code spans) is not captured.
- Table cell/row/column structure is not analyzed (size only).
- List hierarchy is captured, but no aggregate list metrics are computed (item count, max depth).
- No semantic analysis of code block content.
- No cross-reference resolution between documents.

---

## 5. Out of Scope (for this spec version)

The following are outside the current Markdown specification baseline:
- Inline element hierarchy and formatting details
- Table content analysis beyond size detection
- Aggregate list metrics and analytics
- Code block language validation or syntax analysis
- Image dimension or metadata extraction
- Cross-document link validation
- Markdown rendering or conversion features

---

## 6. Future Markdown Enhancements (Non-normative)

Potential future expansions include:
- Inline element analysis (emphasis, code spans)
- Table structure extraction (headers, row count)
- List aggregate statistics (item count, max depth, flat summaries)
- Rich front matter parsing (arrays, objects)
- Code block size statistics per language
- Image metadata extraction
- Cross-document reference analysis
