package org.dxworks.codeframe.model.markdown;

import org.dxworks.codeframe.model.Analysis;

import java.util.ArrayList;
import java.util.List;

public class MarkdownFileAnalysis implements Analysis {
    public String filePath;
    public String language = "markdown";
    public MarkdownSection preamble; // nullable
    public List<MarkdownSection> sections = new ArrayList<>();

    @Override
    public String getFilePath() {
        return filePath;
    }

    @Override
    public String getLanguage() {
        return language;
    }
}
