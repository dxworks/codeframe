package org.dxworks.codeframe.model.sql;

public class CreateViewOperation {
    public String viewName;
    public String schema;  // optional
    public boolean orReplace;  // CREATE OR REPLACE VIEW
    public SqlReferences references = new SqlReferences();
}
