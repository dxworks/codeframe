package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.Analysis;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;

public abstract class TreeSitterAnalyzer implements LanguageAnalyzer {
    private final TSLanguage tsLanguage;

    protected TreeSitterAnalyzer(TSLanguage tsLanguage) {
        this.tsLanguage = tsLanguage;
    }

    @Override
    public Analysis analyze(String filePath, String sourceCode) {
        return analyze(filePath, sourceCode, parse(sourceCode));
    }

    protected abstract Analysis analyze(String filePath, String sourceCode, TSNode rootNode);

    protected final TSNode parse(String sourceCode) {
        TSParser parser = new TSParser();
        parser.setLanguage(tsLanguage);
        TSTree tree = parser.parseString(null, sourceCode);
        return tree.getRootNode();
    }

    protected static int countErrorNodes(TSNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        int errors = ("ERROR".equals(node.getType()) || "MISSING".equals(node.getType())) ? 1 : 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            errors += countErrorNodes(node.getChild(i));
        }
        return errors;
    }
}
