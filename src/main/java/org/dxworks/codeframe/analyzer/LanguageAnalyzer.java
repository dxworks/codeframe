package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.Analysis;
import org.treesitter.TSNode;

public interface LanguageAnalyzer {
    Analysis analyze(String filePath, String sourceCode, TSNode rootNode);
}
