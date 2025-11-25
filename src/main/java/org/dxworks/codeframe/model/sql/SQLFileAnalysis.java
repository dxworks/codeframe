package org.dxworks.codeframe.model.sql;

import org.dxworks.codeframe.model.Analysis;

import java.util.ArrayList;
import java.util.List;

public class SQLFileAnalysis implements Analysis {
    public String filePath;
    public String language = "sql";
    // Top-level standalone statements (outside definitions)
    public SqlReferences topLevelReferences = new SqlReferences();
    public SqlInvocations topLevelCalls = new SqlInvocations();
    
    // DDL Operations (definitions)
    public List<CreateTableOperation> createTables = new ArrayList<>();
    public List<AlterTableOperation> alterTables = new ArrayList<>();
    public List<CreateViewOperation> createViews = new ArrayList<>();
    public List<CreateIndexOperation> createIndexes = new ArrayList<>();
    public List<CreateProcedureOperation> createProcedures = new ArrayList<>();
    public List<CreateFunctionOperation> createFunctions = new ArrayList<>();
    public List<CreateTriggerOperation> createTriggers = new ArrayList<>();
    public List<AlterViewOperation> alterViews = new ArrayList<>();
    public List<AlterFunctionOperation> alterFunctions = new ArrayList<>();
    public List<AlterProcedureOperation> alterProcedures = new ArrayList<>();
    
    // Drop operations
    public List<DropOperation> dropOperations = new ArrayList<>();
    
    @Override
    public String getFilePath() {
        return filePath;
    }
    
    @Override
    public String getLanguage() {
        return language;
    }
}
