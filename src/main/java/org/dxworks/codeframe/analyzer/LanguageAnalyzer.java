package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.FileAnalysis;
import org.treesitter.TSNode;

public interface LanguageAnalyzer {
    FileAnalysis analyze(String filePath, String sourceCode, TSNode rootNode);
}
