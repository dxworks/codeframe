package org.dxworks.codeframe.model.sql;

public class DropOperation {
    public String objectType;  // TABLE, VIEW, INDEX, PROCEDURE, FUNCTION, TRIGGER
    public String objectName;
    public String schema;  // optional
    public boolean ifExists;  // DROP ... IF EXISTS
}
