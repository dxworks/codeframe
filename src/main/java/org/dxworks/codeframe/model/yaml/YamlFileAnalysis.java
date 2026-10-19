package org.dxworks.codeframe.model.yaml;

import org.dxworks.codeframe.model.Analysis;

import java.util.ArrayList;
import java.util.List;

public class YamlFileAnalysis implements Analysis {
    public String filePath;
    public String language = "yaml";
    public List<YamlDocument> documents = new ArrayList<>();

    @Override
    public String getFilePath() {
        return filePath;
    }

    @Override
    public String getLanguage() {
        return language;
    }
}
