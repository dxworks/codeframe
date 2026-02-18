package org.dxworks.codeframe.model.cobol;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class COBOLParagraph {
    public String name;
    public List<COBOLPerformCall> performCalls = new ArrayList<>();
    public List<COBOLExternalCall> externalCalls = new ArrayList<>();
    public List<COBOLFileOperation> fileOperations = new ArrayList<>();
    public List<COBOLControlFlowStatement> controlFlowStatements = new ArrayList<>();
    public Set<String> dataReferences = new LinkedHashSet<>();
}
