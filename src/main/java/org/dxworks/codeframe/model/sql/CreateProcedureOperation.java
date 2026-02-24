package org.dxworks.codeframe.model.sql;

public class CreateProcedureOperation implements HasReferencesAndCalls {
    public String procedureName;
    public String schema;  // optional
    public boolean orReplace;  // CREATE OR REPLACE PROCEDURE
    public java.util.List<ParameterDefinition> parameters = new java.util.ArrayList<>();
    public SqlReferences references = new SqlReferences();
    public SqlInvocations calls = new SqlInvocations();

    @Override public SqlReferences getReferences() { return references; }
    @Override public SqlInvocations getCalls() { return calls; }
}
