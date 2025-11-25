package org.dxworks.codeframe.model.sql;

public class AlterViewOperation {
    public String viewName;
    public String schema;
    // For ALTER VIEW ... AS SELECT ... we can capture referenced relations
    public SqlReferences references = new SqlReferences();
}
