package org.dxworks.codeframe.model.sql;

import java.util.ArrayList;
import java.util.List;

public class ColumnDefinition {
    public String name;
    public String type;
    public boolean nullable = true;
    public List<String> constraints = new ArrayList<>();  // Only structural: PRIMARY KEY, FOREIGN KEY, UNIQUE, NOT NULL
}
