package org.dxworks.codeframe.model.sql;

import java.util.ArrayList;
import java.util.List;

public class AlterTableOperation {
    public String tableName;
    public String schema;  // optional
    public String operationType;  // ADD_COLUMN, DROP_COLUMN, MODIFY_COLUMN, ADD_CONSTRAINT, etc.
    public List<ColumnDefinition> addedColumns = new ArrayList<>();
    public List<String> droppedColumns = new ArrayList<>();
    public List<String> modifiedColumns = new ArrayList<>();
    public List<String> addedConstraints = new ArrayList<>();
    public List<String> droppedConstraints = new ArrayList<>();
}
