package org.dxworks.codeframe.model.sql;

import java.util.ArrayList;
import java.util.List;

public class ForeignKeyDefinition {
    public String name;  // FK constraint name (optional)
    public List<String> columns = new ArrayList<>();  // Columns in this table
    public String referencedTable;
    public List<String> referencedColumns = new ArrayList<>();
}
