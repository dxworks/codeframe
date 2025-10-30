package org.dxworks.codeframe.model.sql;

import java.util.ArrayList;
import java.util.List;

public class CreateFunctionOperation {
    public String functionName;
    public String schema;  // optional
    public boolean orReplace;  // CREATE OR REPLACE FUNCTION
    public List<ParameterDefinition> parameters = new ArrayList<>();
    public String returnType;
    public List<String> referencedTables = new ArrayList<>();
}
