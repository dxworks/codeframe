package org.dxworks.codeframe.model.sql;

import java.util.ArrayList;
import java.util.List;

public class CreateTriggerOperation {
    public String triggerName;
    public String tableName;
    public String schema;  // optional
    public boolean orReplace;  // CREATE OR REPLACE TRIGGER
    public String timing;  // BEFORE, AFTER, INSTEAD OF
    public List<String> events = new ArrayList<>();  // INSERT, UPDATE, DELETE (can be multiple)
    public SqlReferences references = new SqlReferences();  // tables/views accessed in body
    public SqlInvocations calls = new SqlInvocations();  // functions/procedures called in body
}
