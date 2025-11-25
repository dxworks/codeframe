package org.dxworks.codeframe.model.sql;

public class AlterProcedureOperation {
    public String procedureName;
    public String schema;
    public java.util.List<ParameterDefinition> parameters = new java.util.ArrayList<>();
    public SqlReferences references = new SqlReferences();
    public SqlInvocations calls = new SqlInvocations();
}
