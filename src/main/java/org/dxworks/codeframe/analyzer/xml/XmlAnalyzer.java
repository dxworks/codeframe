package org.dxworks.codeframe.analyzer.xml;

import org.dxworks.codeframe.analyzer.LanguageAnalyzer;
import org.dxworks.codeframe.model.Analysis;
import org.dxworks.codeframe.model.xml.XmlElement;
import org.dxworks.codeframe.model.xml.XmlFileAnalysis;
import org.treesitter.TSNode;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * XML analyzer using Java's built-in StAX pull parser.
 *
 * See docs/specs/XML_SPEC.md for the output contract. This analyzer records
 * element names (qualified), attribute names (excluding xmlns*), namespace
 * declarations, nesting, and line spans. All value-level content (attribute
 * values, text, CDATA, comments, PIs, XML declaration, DOCTYPE) is dropped.
 *
 * The source is parsed directly first; if that fails (typically because the
 * file is a multi-root XML fragment), a second attempt wraps the source in a
 * synthetic root element and exposes the wrapper's direct children as
 * {@code roots}. If both attempts fail, {@code roots} is left empty.
 */
public class XmlAnalyzer implements LanguageAnalyzer {

    private static final String WRAPPER_OPEN = "<__codeframe_root__>";
    private static final String WRAPPER_CLOSE = "</__codeframe_root__>";
    private static final String WRAPPER_LOCAL_NAME = "__codeframe_root__";

    private final XMLInputFactory inputFactory;

    public XmlAnalyzer() {
        this.inputFactory = XMLInputFactory.newInstance();
        // Security hardening: no DTD processing, no external entity resolution.
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        // Keep the original XML declaration / prolog handling default; we ignore those events.
    }

    @Override
    public Analysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        XmlFileAnalysis analysis = new XmlFileAnalysis();
        analysis.filePath = filePath;

        List<XmlElement> roots = tryParse(sourceCode);
        if (roots == null) {
            roots = tryParse(wrapForFragmentParsing(sourceCode));
        }
        analysis.roots = (roots != null) ? roots : new ArrayList<>();

        return analysis;
    }

    private List<XmlElement> tryParse(String xml) {
        XMLStreamReader reader = null;
        try {
            reader = inputFactory.createXMLStreamReader(new StringReader(xml));
            return parseRoots(reader);
        } catch (XMLStreamException e) {
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                }
            }
        }
    }

    /**
     * Wraps the source in a synthetic single root so StAX can accept fragments
     * (multiple top-level elements). Any leading XML declaration is stripped
     * first because it is no longer at position 0 after wrapping; its newline
     * (if any) is kept, so line numbers for content on subsequent lines are
     * preserved.
     */
    private String wrapForFragmentParsing(String sourceCode) {
        String prepared = sourceCode.startsWith("<?xml")
                ? sourceCode.substring(sourceCode.indexOf("?>") + 2)
                : sourceCode;
        return WRAPPER_OPEN + prepared + WRAPPER_CLOSE;
    }

    private List<XmlElement> parseRoots(XMLStreamReader reader) throws XMLStreamException {
        List<XmlElement> roots = new ArrayList<>();
        Deque<XmlElement> stack = new ArrayDeque<>();
        Deque<Integer> startLines = new ArrayDeque<>();
        boolean wrapperSeen = false;

        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    if (!wrapperSeen && isWrapperElement(reader)) {
                        wrapperSeen = true;
                        continue;
                    }

                    XmlElement element = buildElement(reader);
                    int startLine = reader.getLocation().getLineNumber();

                    if (stack.isEmpty()) {
                        roots.add(element);
                    } else {
                        XmlElement parent = stack.peek();
                        if (parent.children == null) {
                            parent.children = new ArrayList<>();
                        }
                        parent.children.add(element);
                    }

                    stack.push(element);
                    startLines.push(startLine);
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if (wrapperSeen && isWrapperElement(reader) && stack.isEmpty()) {
                        // End of synthetic wrapper; ignore.
                        continue;
                    }

                    XmlElement element = stack.pop();
                    int startLine = startLines.pop();
                    int endLine = reader.getLocation().getLineNumber();
                    element.lines = Math.max(1, endLine - startLine + 1);
                }
                default -> {
                    // Ignore text, CDATA, comments, PIs, XML decl, DTD, etc.
                }
            }
        }

        return roots;
    }

    private boolean isWrapperElement(XMLStreamReader reader) {
        String prefix = reader.getPrefix();
        return (prefix == null || prefix.isEmpty())
                && WRAPPER_LOCAL_NAME.equals(reader.getLocalName());
    }

    private XmlElement buildElement(XMLStreamReader reader) {
        XmlElement element = new XmlElement();
        element.name = qualifiedName(reader.getPrefix(), reader.getLocalName());
        element.namespaces = readNamespaceDeclarations(reader);
        element.attributes = readAttributeNames(reader);
        return element;
    }

    private Map<String, String> readNamespaceDeclarations(XMLStreamReader reader) {
        int count = reader.getNamespaceCount();
        if (count == 0) {
            return null;
        }
        Map<String, String> namespaces = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String prefix = reader.getNamespacePrefix(i);
            String uri = reader.getNamespaceURI(i);
            // StAX reports default namespace prefix as null; spec uses "".
            String key = (prefix == null) ? "" : prefix;
            namespaces.put(key, uri == null ? "" : uri);
        }
        return namespaces;
    }

    private List<String> readAttributeNames(XMLStreamReader reader) {
        int count = reader.getAttributeCount();
        if (count == 0) {
            return null;
        }
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String prefix = reader.getAttributePrefix(i);
            String localName = reader.getAttributeLocalName(i);
            names.add(qualifiedName(prefix, localName));
        }
        return names;
    }

    private String qualifiedName(String prefix, String localName) {
        if (prefix == null || prefix.isEmpty()) {
            return localName;
        }
        return prefix + ":" + localName;
    }
}
