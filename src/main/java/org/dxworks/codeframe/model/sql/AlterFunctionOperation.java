package org.dxworks.codeframe.model.sql;

public class AlterFunctionOperation {
    public String functionName;
    public String schema;
    public java.util.List<ParameterDefinition> parameters = new java.util.ArrayList<>();
    public String returnType;
    public SqlReferences references = new SqlReferences();
    public SqlInvocations calls = new SqlInvocations();
}
