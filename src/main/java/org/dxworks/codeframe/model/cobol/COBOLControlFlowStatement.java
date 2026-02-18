package org.dxworks.codeframe.model.cobol;

public class COBOLControlFlowStatement {
    public String type;  // "GOBACK", "STOP_RUN", "EXIT_PROGRAM", "RETURN"
    public String target; // nullable string for statements that carry a target
}
