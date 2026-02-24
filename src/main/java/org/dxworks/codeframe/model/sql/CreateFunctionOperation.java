package org.dxworks.codeframe.model.sql;

public class CreateFunctionOperation implements HasReferencesAndCalls {
    public String functionName;
    public String schema;  // optional
    public boolean orReplace;  // CREATE OR REPLACE FUNCTION
    public java.util.List<ParameterDefinition> parameters = new java.util.ArrayList<>();
    public String returnType;
    public SqlReferences references = new SqlReferences();
    public SqlInvocations calls = new SqlInvocations();

    @Override public SqlReferences getReferences() { return references; }
    @Override public SqlInvocations getCalls() { return calls; }
}
