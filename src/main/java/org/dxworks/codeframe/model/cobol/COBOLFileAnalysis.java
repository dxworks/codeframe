package org.dxworks.codeframe.model.cobol;

import org.dxworks.codeframe.model.Analysis;

import java.util.ArrayList;
import java.util.List;

public class COBOLFileAnalysis implements Analysis {
    public String filePath;
    public String language = "cobol";
    public String programId;

    public List<COBOLFileControl> fileControls = new ArrayList<>();
    public List<COBOLDataItem> dataItems = new ArrayList<>();
    public List<COBOLFileDefinition> fileDefinitions = new ArrayList<>();
    public List<String> copyStatements = new ArrayList<>();
    public List<String> procedureParameters = new ArrayList<>();

    public List<COBOLSection> sections = new ArrayList<>();
    public List<COBOLParagraph> paragraphs = new ArrayList<>();

    public boolean hasExecSql;
    public boolean hasExecCics;
    public boolean hasExecSqlIms;

    @Override
    public String getFilePath() {
        return filePath;
    }

    @Override
    public String getLanguage() {
        return language;
    }
}
