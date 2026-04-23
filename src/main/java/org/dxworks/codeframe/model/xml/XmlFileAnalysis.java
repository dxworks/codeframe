package org.dxworks.codeframe.model.xml;

import org.dxworks.codeframe.model.Analysis;

import java.util.ArrayList;
import java.util.List;

public class XmlFileAnalysis implements Analysis {
    public String filePath;
    public String language = "xml";
    public List<XmlElement> roots = new ArrayList<>();

    @Override
    public String getFilePath() {
        return filePath;
    }

    @Override
    public String getLanguage() {
        return language;
    }
}
