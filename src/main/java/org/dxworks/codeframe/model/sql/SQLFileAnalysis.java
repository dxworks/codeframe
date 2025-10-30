package org.dxworks.codeframe.model.sql;

import org.dxworks.codeframe.model.Analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SQLFileAnalysis implements Analysis {
    public String filePath;
    public String language = "sql";
    
    // DDL Operations (definitions)
    public List<CreateTableOperation> createTables = new ArrayList<>();
    public List<AlterTableOperation> alterTables = new ArrayList<>();
    public List<CreateViewOperation> createViews = new ArrayList<>();
    public List<CreateIndexOperation> createIndexes = new ArrayList<>();
    public List<CreateProcedureOperation> createProcedures = new ArrayList<>();
    public List<CreateFunctionOperation> createFunctions = new ArrayList<>();
    public List<CreateTriggerOperation> createTriggers = new ArrayList<>();
    
    // Drop operations
    public List<DropOperation> dropOperations = new ArrayList<>();
    
    // Summary of referenced objects (for quick dependency analysis)
    public Set<String> referencedTables = new HashSet<>();
    public Set<String> referencedViews = new HashSet<>();
    public Set<String> referencedProcedures = new HashSet<>();
    
    @Override
    public String getFilePath() {
        return filePath;
    }
    
    @Override
    public String getLanguage() {
        return language;
    }
}
