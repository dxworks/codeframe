package org.dxworks.codeframe.analyzer.yaml;

import org.dxworks.codeframe.analyzer.LanguageAnalyzer;
import org.dxworks.codeframe.model.Analysis;
import org.dxworks.codeframe.model.yaml.YamlDocument;
import org.dxworks.codeframe.model.yaml.YamlFileAnalysis;
import org.dxworks.codeframe.model.yaml.YamlMappingEntry;
import org.dxworks.codeframe.model.yaml.YamlNode;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.AnchorNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class YamlAnalyzer implements LanguageAnalyzer {

    private static final String MERGE_KEY = "<<";
    private static final String DIALECT_OPENAPI = "openapi";
    private static final String DIALECT_ASYNCAPI = "asyncapi";

    private final Yaml yaml;

    public YamlAnalyzer() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(true);
        loaderOptions.setAllowRecursiveKeys(false);
        loaderOptions.setMaxAliasesForCollections(50);
        this.yaml = new Yaml(loaderOptions);
    }

    @Override
    public Analysis analyze(String filePath, String sourceCode) {
        YamlFileAnalysis analysis = new YamlFileAnalysis();
        analysis.filePath = filePath;

        try {
            Iterable<Node> nodeStream = yaml.composeAll(new StringReader(sourceCode));

            for (Node docNode : nodeStream) {
                if (docNode == null || isEmptyDocumentNode(docNode, sourceCode)) {
                    continue;
                }

                YamlDocument doc = new YamlDocument();
                int startLine = docNode.getStartMark().getLine() + 1;
                int endLine = docNode.getEndMark().getLine() + 1;
                doc.lines = Math.max(1, endLine - startLine + 1);

                String dialect = detectDialect(docNode);
                if (dialect != null) {
                    doc.dialect = dialect;
                }

                doc.root = convertNode(docNode, new NodePath(), dialect, new IdentityHashMap<>());
                analysis.documents.add(doc);
            }

            return analysis;
        } catch (YAMLException e) {
            throw new IllegalArgumentException("Failed to parse YAML", e);
        }
    }

    private String detectDialect(Node docNode) {
        if (!(docNode instanceof MappingNode mapping)) {
            return null;
        }
        for (NodeTuple tuple : mapping.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode keyScalar) {
                String key = keyScalar.getValue();
                if ("openapi".equals(key) || "swagger".equals(key)) {
                    return DIALECT_OPENAPI;
                }
                if (DIALECT_ASYNCAPI.equals(key)) {
                    return DIALECT_ASYNCAPI;
                }
            }
        }
        return null;
    }

    private YamlNode convertNode(
            Node node,
            NodePath path,
            String dialect,
            Map<Node, Boolean> emittedAnchoredNodes) {
        if (node == null) {
            return null;
        }
        if (node instanceof AnchorNode anchorNode) {
            return convertAnchorNode(anchorNode);
        }
        if (isRepeatedAnchorReference(node, emittedAnchoredNodes)) {
            return createAliasNode(node, lineSpan(node));
        }
        if (node.getAnchor() != null) {
            emittedAnchoredNodes.put(node, Boolean.TRUE);
        }
        if (node instanceof MappingNode mappingNode) {
            return convertMappingNode(mappingNode, path, dialect, emittedAnchoredNodes);
        }
        if (node instanceof SequenceNode sequenceNode) {
            return convertSequenceNode(sequenceNode, path, dialect, emittedAnchoredNodes);
        }
        if (node instanceof ScalarNode scalarNode) {
            return convertScalarNode(scalarNode, path, dialect);
        }
        return null;
    }

    private YamlNode convertAnchorNode(AnchorNode anchorNode) {
        Node realNode = anchorNode.getRealNode();
        String alias = realNode != null ? realNode.getAnchor() : null;
        YamlNode node = new YamlNode();
        node.kind = "alias";
        node.lines = 1;
        node.alias = alias;
        return node;
    }

    private YamlNode convertMappingNode(
            MappingNode mappingNode,
            NodePath path,
            String dialect,
            Map<Node, Boolean> emittedAnchoredNodes) {
        YamlNode node = new YamlNode();
        node.kind = "mapping";
        node.lines = lineSpan(mappingNode);
        setAnchor(node, mappingNode);

        List<YamlMappingEntry> entries = new ArrayList<>();
        for (NodeTuple tuple : mappingNode.getValue()) {
            Node keyNode = tuple.getKeyNode();
            Node valueNode = tuple.getValueNode();

            if (!(keyNode instanceof ScalarNode keyScalar)) {
                continue;
            }

            String key = keyScalar.getValue();

            if (MERGE_KEY.equals(key)) {
                YamlNode mergeValueNode = convertNode(valueNode, path.append(key), dialect, emittedAnchoredNodes);
                if (mergeValueNode != null) {
                    mergeValueNode.merge = Boolean.TRUE;
                    YamlMappingEntry entry = new YamlMappingEntry();
                    entry.key = key;
                    entry.value = mergeValueNode;
                    entries.add(entry);
                }
                continue;
            }

            NodePath childPath = path.append(key);
            YamlNode valueYamlNode = convertNode(valueNode, childPath, dialect, emittedAnchoredNodes);
            if (valueYamlNode != null) {
                YamlMappingEntry entry = new YamlMappingEntry();
                entry.key = key;
                entry.value = valueYamlNode;
                entries.add(entry);
            }
        }

        if (!entries.isEmpty()) {
            node.entries = entries;
        }
        return node;
    }

    private YamlNode convertSequenceNode(
            SequenceNode sequenceNode,
            NodePath path,
            String dialect,
            Map<Node, Boolean> emittedAnchoredNodes) {
        YamlNode node = new YamlNode();
        node.kind = "sequence";
        node.lines = lineSpan(sequenceNode);
        setAnchor(node, sequenceNode);

        List<YamlNode> items = new ArrayList<>();
        for (Node itemNode : sequenceNode.getValue()) {
            YamlNode item = convertNode(itemNode, path.arrayItem(), dialect, emittedAnchoredNodes);
            if (item != null) {
                items.add(item);
            }
        }

        if (!items.isEmpty()) {
            node.items = items;
        }
        return node;
    }

    private YamlNode convertScalarNode(ScalarNode scalarNode, NodePath path, String dialect) {
        YamlNode node = new YamlNode();
        node.kind = "scalar";
        node.lines = lineSpan(scalarNode);
        setAnchor(node, scalarNode);

        if (isAllowedDialectScalar(path, scalarNode.getValue(), dialect)) {
            String raw = scalarNode.getValue();
            if (raw != null && !raw.isEmpty()) {
                node.value = raw;
            }
        }

        return node;
    }

    // -------------------------------------------------------------------------
    // OpenAPI scalar value retention
    // -------------------------------------------------------------------------

    private boolean isAllowedDialectScalar(NodePath path, String scalarValue, String dialect) {
        if (DIALECT_OPENAPI.equals(dialect)) {
            return OpenApiScalarPolicy.isAllowed(path, scalarValue);
        }
        if (DIALECT_ASYNCAPI.equals(dialect)) {
            return AsyncApiScalarPolicy.isAllowed(path, scalarValue);
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int lineSpan(Node node) {
        int start = node.getStartMark().getLine();
        int end = node.getEndMark().getLine();
        return Math.max(1, end - start + 1);
    }

    private void setAnchor(YamlNode out, Node in) {
        if (in.getAnchor() != null) {
            out.anchor = in.getAnchor();
        }
    }

    private boolean isRepeatedAnchorReference(Node node, Map<Node, Boolean> emittedAnchoredNodes) {
        return node.getAnchor() != null && emittedAnchoredNodes.containsKey(node);
    }

    private YamlNode createAliasNode(Node node, int lines) {
        YamlNode aliasNode = new YamlNode();
        aliasNode.kind = "alias";
        aliasNode.lines = 1;
        aliasNode.alias = node.getAnchor();
        return aliasNode;
    }

    private boolean isEmptyDocumentNode(Node node, String sourceCode) {
        if (!(node instanceof ScalarNode scalarNode) || !scalarNode.getValue().isEmpty()) {
            return false;
        }

        if ("null".equalsIgnoreCase(scalarNode.getTag().getValue())) {
            return true;
        }

        String[] lines = sourceCode.split("\n", -1);
        int startLine = node.getStartMark().getLine();
        int endLine = node.getEndMark().getLine();

        for (int i = Math.max(0, startLine - 1); i <= Math.min(lines.length - 1, endLine); i++) {
            if (isEmptyDocumentLine(lines[i])) {
                return true;
            }
        }

        return false;
    }

    private boolean isEmptyDocumentLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return true;
        }

        if (trimmed.startsWith("---")) {
            return trimmed.length() == 3 || trimmed.substring(3).trim().startsWith("#");
        }

        if (trimmed.startsWith("...")) {
            return trimmed.length() == 3 || trimmed.substring(3).trim().startsWith("#");
        }

        return false;
    }
}
