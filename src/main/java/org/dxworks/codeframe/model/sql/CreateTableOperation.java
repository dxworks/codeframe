package org.dxworks.codeframe.model.sql;

import java.util.ArrayList;
import java.util.List;

public class CreateTableOperation {
    public String tableName;
    public String schema;  // optional
    public boolean ifNotExists;  // CREATE TABLE IF NOT EXISTS
    public List<ColumnDefinition> columns = new ArrayList<>();
    public List<String> primaryKeys = new ArrayList<>();
    public List<ForeignKeyDefinition> foreignKeys = new ArrayList<>();
}
