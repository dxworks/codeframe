package org.dxworks.codeframe.model.sql;

import java.util.ArrayList;
import java.util.List;

public class CreateIndexOperation {
    public String indexName;
    public String tableName;
    public String schema;  // optional
    public boolean unique;
    public List<String> columns = new ArrayList<>();
    public String indexType;  // BTREE, HASH, etc. (dialect-specific)
}
