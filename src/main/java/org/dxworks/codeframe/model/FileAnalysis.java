package org.dxworks.codeframe.model;

import java.util.ArrayList;
import java.util.List;

public class FileAnalysis implements Analysis {
    public String filePath;
    public String language;
    public String packageName;
    public List<TypeInfo> types = new ArrayList<>();
    public List<MethodInfo> methods = new ArrayList<>();
    public List<String> imports = new ArrayList<>();
    
    @Override
    public String getFilePath() {
        return filePath;
    }
    
    @Override
    public String getLanguage() {
        return language;
    }
}
