# XML Specification (CodeFrame)

## Purpose

This document defines the **XML analysis specification** for CodeFrame.

It describes:
- what XML structural facts are extracted,
- why these facts are part of the model,
- what is in scope vs out of scope,
- the explicit XML limitations for the current version.

This is a **normative spec** for XML behavior and output shape. It is not an implementation plan.

---

## 1. XML Language Scope

### 1.1 Supported XML specification

- Target specification: **XML 1.0** (well-formed documents).
- Namespace handling follows **Namespaces in XML 1.0**.
- External entities, DTDs, and schemas are **not** resolved. The analyzer does not fetch or load external resources.

### 1.2 File extensions in scope

- `.xml`

Other XML-based formats (for example `.xsd`, `.xsl`, `.xslt`, `.wsdl`, `.svg`, `.pom`, `.csproj`, `.config`, `.plist`) are **out of scope** for this spec version. They may be added later, either by extending this spec or by dedicated specs that capture their semantics.

### 1.3 Out of scope

- HTML (`.html`, `.htm`) — not generally well-formed XML; deserves its own spec.
- XML-based DSLs (XSD, XSLT, WSDL, SVG, Ant, Maven POM, MSBuild, etc.) — not treated as XML in this spec version.
- DTD, XSD, and RelaxNG schema validation.
- Entity expansion and external entity resolution.
- XInclude processing.

---

## 2. XML Output Contract

XML analysis output is represented by `XmlFileAnalysis`.

### 2.1 Top-level fields

- `filePath`: analyzed file path.
- `language`: always `"xml"`.
- `roots`: list of top-level elements. A well-formed XML document has exactly one; a fragment or parse-recovered document may have zero or more.

```text
XmlFileAnalysis {
    filePath: String
    language: "xml"
    roots: List<XmlElement>   // optional; may be omitted or null when empty
}
```

When the file cannot be parsed at all, `roots` is empty. It may be serialized as an empty list or omitted/null; consumers must treat absence as equivalent to an empty list.

### 2.2 XML-specific model intent

The model captures **document structure only**: element names, their attribute names, and their nesting. Attribute values, text, comments, processing instructions, and the XML declaration are intentionally not represented.

This reflects the project's philosophy of extracting deterministic structural facts for downstream post-processing. Value-level content (attribute values, text, CDATA) is not structural and may contain sensitive data; it is therefore omitted. Attribute *names*, by contrast, describe the shape of each element and are retained.

`XmlFileAnalysis` is a dedicated model because XML documents do not have code-centric constructs such as classes, methods, fields, imports, or call graphs.

---

## 3. Semantic Extraction Rules

### 3.1 Elements

- Every XML element is recorded as an `XmlElement` preserving parent/child nesting.
- Element order among siblings is preserved as it appears in the source.
- Empty elements (`<foo/>` and `<foo></foo>`) are recorded identically; self-closing syntax is not distinguished.

### 3.2 Element names

- The element `name` is the **qualified name** as written in the source, including any namespace prefix.
  - Example: `<soap:Envelope>` → `name: "soap:Envelope"`.
  - Example: `<Envelope>` → `name: "Envelope"`.
- Prefixes are preserved verbatim; no prefix resolution or rewriting is performed.

### 3.3 Attributes

- Attribute **names** on each element are recorded as an ordered list `attributes`.
- Attribute **values** are always dropped.
- The order of `attributes` matches the order of attributes in the source.
- Qualified attribute names are kept verbatim, including any prefix (for example `xlink:href`). No prefix resolution is performed.
- Namespace declarations (`xmlns` and `xmlns:prefix`) are **not** included in `attributes`; they are recorded separately under `namespaces` (see 3.4).
- When an element has no non-namespace attributes, `attributes` may be omitted or null.

### 3.4 Namespace declarations

- Namespace declarations (`xmlns` and `xmlns:prefix` attributes) are recorded separately from ordinary attributes.
- They are attached to the element on which they appear, as a `namespaces` map from prefix to URI.
  - The default namespace (`xmlns="..."`) uses an empty string `""` as its key.
- The map reflects **declarations at this element only**, not the inherited in-scope set. Downstream consumers can compute effective scope by walking ancestors.
- The iteration order of `namespaces` is **unspecified**. Serializers may emit entries in source-declaration order, alphabetical order, or any other deterministic order. Consumers must not rely on a specific order.
- When an element declares no namespaces, `namespaces` may be omitted or null.

### 3.5 What is dropped

The following XML constructs are **not** recorded in the output:

- Attribute values (only names are recorded; see 3.3).
- Text content (character data between tags).
- CDATA sections.
- Comments (`<!-- ... -->`).
- Processing instructions (`<?target ...?>`), including `xml-stylesheet`.
- The XML declaration (`<?xml version="1.0" encoding="..."?>`).
- DOCTYPE declarations and any internal subset they contain.
- Entity references (character entities in text are not recorded because text itself is not recorded).

### 3.6 `XmlElement`

```text
XmlElement {
    name: String                           // qualified name as written, e.g. "soap:Envelope"
    lines: int                             // number of source lines this element spans
    attributes: List<String>               // optional; attribute names in source order (excludes xmlns*)
    namespaces: Map<String, String>        // optional; prefix -> URI; "" for default namespace
    children: List<XmlElement>             // optional; nested elements in source order
}
```

`attributes`, `namespaces`, and `children` are optional and may be omitted or null when empty.

### 3.7 Element size calculation

- `lines` is the number of source lines the element spans, from the line of its start tag through the line of its end tag (inclusive). For self-closing elements, `lines` is the number of lines the single tag spans (typically `1`).
- Line counts are derived from source position information reported by the parser.

### 3.8 Multiple roots and malformed files

- A conformant XML document has exactly one top-level element; `roots` will contain one entry.
- If the file contains an XML fragment with multiple top-level elements (for example a logged XML stream), each top-level element is recorded as an entry in `roots`, in source order.
- If the file cannot be parsed as XML (not well-formed and not recoverable), `roots` is an empty list. No partial tree is emitted.

### 3.9 Normative JSON Example (shape)

Source:

```xml
<?xml version="1.0"?>
<!-- SOAP request -->
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:m="http://example.com/stock">
  <soap:Body>
    <m:GetPrice currency="USD">
      <m:StockName>IBM</m:StockName>
    </m:GetPrice>
  </soap:Body>
</soap:Envelope>
```

Output:

```json
{
  "filePath": "request.xml",
  "language": "xml",
  "roots": [
    {
      "name": "soap:Envelope",
      "lines": 8,
      "namespaces": {
        "soap": "http://schemas.xmlsoap.org/soap/envelope/",
        "m": "http://example.com/stock"
      },
      "children": [
        {
          "name": "soap:Body",
          "lines": 5,
          "children": [
            {
              "name": "m:GetPrice",
              "lines": 3,
              "attributes": ["currency"],
              "children": [
                { "name": "m:StockName", "lines": 1 }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

In this contract, optional fields (for example `attributes`, `namespaces`, `children`) may be omitted or null when not applicable.

---

## 4. Current Limitations

- Attribute values are not captured; only attribute names are recorded.
- Text content, CDATA, comments, processing instructions, the XML declaration, and DOCTYPE are not captured.
- Namespace URIs are recorded verbatim as declared in the source. They may include internal hostnames or identifiers (for example `http://billing.internal.acme.corp/v2`); downstream consumers should treat `namespaces` values as potentially sensitive.
- Namespace resolution is not performed: two files that use different prefixes for the same URI will produce different `name` values.
- No schema, DTD, or RelaxNG validation is performed.
- No entity expansion or external resource loading is performed.
- No depth or element-count limits are enforced; very large documents produce very large trees.

---

## 5. Out of Scope (for this spec version)

The following are outside the current XML specification baseline:

- Attribute value extraction.
- Text, CDATA, comment, or PI extraction.
- DOCTYPE and internal subset extraction.
- Schema-aware analysis (XSD, RelaxNG, Schematron).
- XSLT, XPath, or XQuery semantics.
- XML-based configuration or build-file semantics (Maven POM, MSBuild, Ant, Spring, etc.).
- SVG graphic semantics.
- HTML analysis.
- Cross-document reference resolution (XInclude, entity references, schemaLocation).

---

## 6. Future XML Enhancements (Non-normative)

Potential future expansions include:

- Selective attribute *value* extraction for well-known, non-sensitive attributes (for example `class`, `type`, `ref`) in configuration-style XML.
- Dedicated specs for XML dialects (XSD, XSLT, WSDL, POM, etc.) that layer dialect semantics on top of the generic XML tree.
- Namespace resolution producing `(localName, namespaceUri)` per element.
- Aggregate metrics (max depth, element counts per tag name).
- Size-limit safeguards for pathological documents.
