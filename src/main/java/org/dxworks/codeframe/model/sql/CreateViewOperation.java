package org.dxworks.codeframe.model.sql;

import java.util.ArrayList;
import java.util.List;

public class CreateViewOperation {
    public String viewName;
    public String schema;  // optional
    public boolean orReplace;  // CREATE OR REPLACE VIEW
    public List<String> referencedTables = new ArrayList<>();
}
