package org.dxworks.codeframe.model.sql;

import java.util.ArrayList;
import java.util.List;

public class CreateProcedureOperation {
    public String procedureName;
    public String schema;  // optional
    public boolean orReplace;  // CREATE OR REPLACE PROCEDURE
    public List<ParameterDefinition> parameters = new ArrayList<>();
    public List<String> referencedTables = new ArrayList<>();
    public List<String> calledProcedures = new ArrayList<>();
}
