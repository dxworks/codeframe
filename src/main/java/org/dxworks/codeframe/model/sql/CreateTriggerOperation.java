package org.dxworks.codeframe.model.sql;

import java.util.ArrayList;
import java.util.List;

public class CreateTriggerOperation {
    public String triggerName;
    public String tableName;
    public String schema;  // optional
    public String timing;  // BEFORE, AFTER, INSTEAD OF
    public String event;  // INSERT, UPDATE, DELETE
    public List<String> referencedTables = new ArrayList<>();  // from trigger body
}
