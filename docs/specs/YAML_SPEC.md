# YAML Specification (CodeFrame)

## Purpose

This document defines YAML analysis behavior for CodeFrame.

It describes:
- YAML structural facts extracted,
- OpenAPI YAML detection and scalar value retention,
- output shape,
- current limitations.

This is a normative spec for YAML behavior and output shape. It is not an implementation plan.

---

## 1. YAML Scope

### 1.1 Supported files

- File extensions: `.yaml`, `.yml`
- CodeFrame language key: `yaml`

`.yml` is an extension only. Analyzer names, config keys, and output language use `yaml`.

### 1.2 Parser contract

YAML analysis follows the global extraction contract in `../EXTRACTION_CONTRACT.md`: extract source-grounded structural facts only, keep output deterministic, and preserve partial structure when possible.

The analyzer does not write side-effect files.

---

## 2. Output Contract

YAML analysis output is represented by `YamlFileAnalysis`.

```text
YamlFileAnalysis {
    filePath: String
    language: "yaml"
    documents: List<YamlDocument>?  // omitted when empty by serializer
}

YamlDocument {
    lines: int
    dialect: String?        // omitted for generic YAML; "openapi" for OpenAPI documents
    root: YamlNode?
}

YamlNode {
    kind: "mapping" | "sequence" | "scalar" | "alias"
    lines: int
    value: String?          // omitted by default; present only for allowed OpenAPI structural scalars
    anchor: String?
    alias: String?
    merge: boolean?
    entries: List<YamlMappingEntry>?  // mapping only
    items: List<YamlNode>?            // sequence only
}

YamlMappingEntry {
    key: String
    value: YamlNode
}
```

Optional fields may be omitted or null when empty or not applicable.

`lines` on `YamlDocument` and `YamlNode` is the inclusive source span count derived from the parser-reported start and end marks (`endLine - startLine + 1`). These spans follow parser node boundaries as exposed by the YAML parser; the analyzer does not renormalize nested collection item spans to exclude an enclosing sequence entry line.

`documents` may be omitted when analysis yields no non-empty documents and the output serializer suppresses empty collections.

---

## 3. Generic YAML Rules

Generic YAML analysis preserves document structure and line spans.

Mapping keys are emitted as structural identifiers.

Scalar values are not emitted for generic YAML. Scalar style metadata, scalar tags, and inferred scalar types are not emitted in this spec version.

Sequence nodes are emitted as structure. Sequence item values are not emitted when items are scalars.

Multi-document YAML streams are preserved for non-empty documents. Documents are emitted in source order, each with its own root node and line span. Explicit empty documents are skipped.

Anchors, aliases, and merge key presence may be emitted as source structure. Alias and merge values are not expanded.

When YAML cannot be fully parsed, the analyzer fails the file analysis instead of returning a normal YAML analysis object. A missing or omitted `documents` field indicates a successfully parsed stream with no non-empty documents after empty-document skipping, not a malformed YAML file.

---

## 4. OpenAPI YAML Rules

### 4.1 Detection

OpenAPI is a YAML document dialect detected by the YAML analyzer.

OpenAPI support is special handling within YAML analysis, not a separate CodeFrame language.

Detection supports:
- OpenAPI 3.x documents with a top-level `openapi` declaration.
- Swagger 2.0 documents with a top-level `swagger` declaration.

Detection is performed only against each YAML document's root mapping. Nested `openapi` or `swagger` keys do not mark a document as OpenAPI.

Dialect detection is per document, not per file. A multi-document YAML stream may contain both generic YAML documents and OpenAPI documents.

### 4.2 OpenAPI output model

OpenAPI documents use the same generic YAML node tree as other YAML documents.

The document sets `dialect: "openapi"` and retains selected scalar values that are structural API-surface identifiers.

The analyzer does not build a separate named OpenAPI object model and does not emit a second analysis object or second document for the same source document.

OpenAPI trees are not pruned. All mapping keys remain visible with line spans, including prose-heavy keys such as `description`, `summary`, `example`, `examples`, and `externalDocs`. Values for those fields are omitted unless explicitly allowed by this spec.

### 4.3 Scalar value retention

OpenAPI scalar value retention is context-based. A scalar value is emitted only when its path is a known OpenAPI structural position allowed by this spec. Matching by key name alone is not sufficient.

Allowed OpenAPI scalar values include:
- top-level `openapi` / `swagger` version values
- `info.title`
- operation method keys are mapping keys; operation `operationId` values
- tag values in OpenAPI tag arrays
- response status code keys
- request and response content media type keys
- parameter `name`, `in`, `required`
- schema `type`, `format`, `$ref`
- required property names
- enum labels
- security scheme names, via mapping keys
- security scheme `type`
- HTTP `scheme` for `http` security schemes
- location (`in`) for `apiKey` security schemes
- referenced security scheme names in security requirements
- server variable names, via mapping keys
- callback names and callback path expression keys, via mapping keys
- local link `operationRef` values
- link `operationId` values
- link parameter names, via mapping keys

Values are not emitted for:
- descriptions
- summaries
- examples / example
- defaults
- validation constraints such as `minLength`, `pattern`, and `minimum`
- raw server URLs
- Swagger 2.0 `host`
- Swagger 2.0 full `basePath`
- server variable enum values and defaults
- API key names
- OAuth URLs
- OpenID Connect URLs
- OAuth scopes
- tag descriptions
- external documentation URLs
- response headers
- request body encoding details
- callback expression evaluation results
- link parameter values and expressions
- link requestBody values
- external URL `operationRef` values
- vendor extension (`x-*`) scalar values

Raw server URLs are not emitted because they may expose internal environments or deployment topology.

Vendor extension keys (`x-*`) are retained as mapping keys with line spans. Vendor extension scalar values are never emitted by default.

OpenAPI enum labels may be emitted, consistent with the global enum extraction rule. Assigned values, examples, and defaults are not emitted.

---

## 5. Current Limitations

- Generic YAML scalar values are not emitted.
- OpenAPI scalar values are emitted only for allowed structural positions.
- OpenAPI trees keep skipped keys and line spans, but omit skipped values.
- Explicit empty YAML documents are skipped.
- Alias and merge values are not expanded.
- Scalar style metadata, scalar tags, and inferred scalar types are not emitted.
- Raw server URLs, external docs URLs, OAuth URLs, OpenID Connect URLs, and external `operationRef` URLs are not emitted.
