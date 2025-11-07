package org.dxworks.codeframe.model.sql;

public class CreateProcedureOperation {
    public String procedureName;
    public String schema;  // optional
    public boolean orReplace;  // CREATE OR REPLACE PROCEDURE
    public java.util.List<ParameterDefinition> parameters = new java.util.ArrayList<>();
    public SqlReferences references = new SqlReferences();
    public SqlInvocations calls = new SqlInvocations();
}
