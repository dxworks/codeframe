package org.dxworks.codeframe.analyzer.sql;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.function.CreateFunction;
import net.sf.jsqlparser.statement.create.procedure.CreateProcedure;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex;
import net.sf.jsqlparser.statement.create.table.Index;
import net.sf.jsqlparser.statement.create.table.Index.ColumnParams;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.create.view.AlterView;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.execute.Execute;
import org.dxworks.codeframe.analyzer.LanguageAnalyzer;
import org.dxworks.codeframe.model.Analysis;
import org.dxworks.codeframe.model.sql.*;

import org.treesitter.TSNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLAnalyzer implements LanguageAnalyzer {

    private final RoutineBodyAnalyzer noopAnalyzer = new NoopRoutineBodyAnalyzer();
    private final RoutineBodyAnalyzer tsqlAnalyzer = new TSqlRoutineBodyAnalyzer();
    private final RoutineBodyAnalyzer sqlRoutineAnalyzer = new SqlRoutineBodyAnalyzer();
    private final RoutineAnalysisService routineAnalysisService =
            new RoutineAnalysisService(noopAnalyzer, tsqlAnalyzer, sqlRoutineAnalyzer);

    @Override
    public Analysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        SQLFileAnalysis out = new SQLFileAnalysis();
        out.filePath = filePath;
        out.language = "sql";

        // If this looks like a PL/SQL file, use the ANTLR-based PL/SQL analyzer
        // directly (for both definitions and top-level statements), instead of
        // trying JSqlParser first.
        String detectedDialect = DialectHeuristics.detectDialectFromSource(sourceCode);
        if ("plsql".equals(detectedDialect)) {
            return parseWithAntlr(filePath, sourceCode, "plsql");
        }

        // Preprocess: Remove GO statements (T-SQL batch separator) that JSqlParser doesn't understand
        String preprocessed = SqlPreprocessor.preprocess(sourceCode);

        Statements statements;
        try {
            // Keep the config minimal to match older JSQLParser APIs
            statements = CCJSqlParserUtil.parseStatements(
                preprocessed,
                p -> p.withAllowComplexParsing(true)
                      .withSquareBracketQuotation(true)
            );
        } catch (Exception e) {
            String dialect = DialectHeuristics.detectDialectFromSource(sourceCode);
            if ("tsql".equals(dialect) || "plsql".equals(dialect)) {
                // Use ANTLR-based extractor for supported dialects
                return parseWithAntlr(filePath, sourceCode, dialect);
            }
            // For other dialects (PostgreSQL, MySQL), try regex-based trigger extraction
            extractTriggersFromSource(sourceCode, out);
            return out;
        }

        for (Statement st : statements.getStatements()) {
            if (st == null) continue;

            if (st instanceof CreateTable) {
                handleCreateTable((CreateTable) st, out);
            } else if (st instanceof CreateView) {
                handleCreateView((CreateView) st, out);
            } else if (st instanceof AlterView) {
                handleAlterViewAst((AlterView) st, out);
            } else if (st instanceof CreateIndex) {
                handleCreateIndex((CreateIndex) st, out);
            } else if (st instanceof Alter) {
                handleAlter((Alter) st, out, sourceCode);
            } else if (st instanceof Drop) {
                handleDrop((Drop) st, out);
            } else if (st instanceof CreateFunction) {
                routineAnalysisService.handleCreateFunction((CreateFunction) st, out, sourceCode);
            } else if (st instanceof CreateProcedure) {
                routineAnalysisService.handleCreateProcedure((CreateProcedure) st, out, sourceCode);
            } else {
                handleTopLevelStatement(st, out);
            }
        }

        // Fallback: Extract triggers using regex (JSqlParser doesn't support CREATE TRIGGER)
        extractTriggersFromSource(sourceCode, out);

        return out;
    }

    // ---- Top-level statements (references + calls) ----
    private void handleTopLevelStatement(Statement st, SQLFileAnalysis out) {
        if (st == null) return;
        // Extract table references
        TableReferenceExtractor.extractTableReferences(st, out.topLevelReferences.relations);

        // Procedure calls
        if (st instanceof Execute) {
            Execute exec = (Execute) st;
            if (exec.getName() != null) {
                out.topLevelCalls.procedures.add(RoutineSqlUtils.stripQuotes(exec.getName().toString()));
            }
        }

        // Function calls: traverse expressions
        collectFunctionsTopLevel(st, out.topLevelCalls.functions);
    }

    private void collectFunctionsTopLevel(Statement st, java.util.Collection<String> sink) {
        ExpressionAnalyzer.collectFunctions(st, sink);
    }

    private void handleDrop(Drop drop, SQLFileAnalysis out) {
        DropOperation op = new DropOperation();
        // Type
        String t = drop.getType();
        op.objectType = t != null ? t.toUpperCase() : null;
        // IF EXISTS
        op.ifExists = drop.isIfExists();
        // Name and schema (JSqlParser models names as Table-like in most cases)
        if (drop.getName() instanceof Table) {
            Table tbl = (Table) drop.getName();
            op.objectName = tbl.getName();
            op.schema = tbl.getSchemaName();
        } else if (drop.getName() != null) {
            // Fallback: use toString for name when not table-like
            op.objectName = drop.getName().toString();
        }
        if (op.objectName != null || op.objectType != null) out.dropOperations.add(op);
    }

    private void handleAlter(Alter alter, SQLFileAnalysis out, String sourceCode) {
        // Check if this is ALTER VIEW by examining the source text
        // JSqlParser 5.3 uses Alter for both ALTER TABLE and ALTER VIEW
        if (isAlterView(alter, sourceCode)) {
            handleAlterView(alter, out);
            return;
        }
        handleAlterTable(alter, out);
    }

    private boolean isAlterView(Alter alter, String sourceCode) {
        // If there's no table but the statement is ALTER, it might be ALTER VIEW
        // Check the source text around the statement
        if (alter.getTable() == null) {
            return false; // Could be ALTER VIEW, but we need more context
        }
        // For now, check if the toString contains VIEW keyword
        String alterStr = alter.toString().toUpperCase();
        boolean isView = alterStr.startsWith("ALTER VIEW") || alterStr.contains("ALTER VIEW");
        return isView;
    }

    private void handleAlterView(Alter alter, SQLFileAnalysis out) {
        // Legacy path: JSqlParser once modeled ALTER VIEW as Alter; keep minimal support
        AlterViewOperation op = new AlterViewOperation();
        extractViewData(alter.getTable(), null,
            (name) -> op.viewName = name,
            (schema) -> op.schema = schema,
            op.references.relations);
        out.alterViews.add(op);
    }

    private void handleAlterViewAst(AlterView av, SQLFileAnalysis out) {
        AlterViewOperation op = new AlterViewOperation();
        extractViewData(av.getView(), av.getSelect(),
            (name) -> op.viewName = name,
            (schema) -> op.schema = schema,
            op.references.relations);
        out.alterViews.add(op);
    }

    private void handleAlterTable(Alter alter, SQLFileAnalysis out) {
        AlterTableOperation op = new AlterTableOperation();
        if (alter.getTable() != null) {
            Table t = alter.getTable();
            op.tableName = t.getName();
            op.schema = t.getSchemaName();
        }

        List<AlterExpression> exprs = alter.getAlterExpressions();
        if (exprs != null) {
            for (AlterExpression ae : exprs) {
                processAlterExpression(ae, op);
            }
        }

        if (op.operationType != null || !op.addedColumns.isEmpty() || !op.droppedColumns.isEmpty() || !op.addedConstraints.isEmpty() || !op.droppedConstraints.isEmpty()) {
            out.alterTables.add(op);
        }
    }

    private void handleCreateTable(CreateTable ct, SQLFileAnalysis out) {
        CreateTableOperation op = new CreateTableOperation();
        Table t = ct.getTable();
        if (t != null) {
            op.tableName = t.getName();
            op.schema = t.getSchemaName();
        }
        // IF NOT EXISTS
        op.ifNotExists = ct.isIfNotExists();

        List<ColumnDefinition> cols = ct.getColumnDefinitions();
        if (cols != null) {
            for (ColumnDefinition cd : cols) {
                org.dxworks.codeframe.model.sql.ColumnDefinition m = new org.dxworks.codeframe.model.sql.ColumnDefinition();
                m.name = cd.getColumnName();
                m.type = toTypeString(cd.getColDataType());
                // constraints in columnSpecStrings
                List<String> specs = cd.getColumnSpecs();
                if (specs != null) {
                    List<String> norm = normalize(specs);
                    if (containsSequence(norm, "not", "null")) {
                        m.nullable = false;
                        m.constraints.add("NOT NULL");
                    }
                    if (norm.contains("unique")) {
                        m.constraints.add("UNIQUE");
                    }
                    if (containsSequence(norm, "primary", "key")) {
                        m.constraints.add("PRIMARY KEY");
                        if (!op.primaryKeys.contains(m.name)) op.primaryKeys.add(m.name);
                    }
                }
                op.columns.add(m);
            }
        }

        // Table-level indexes/constraints
        List<Index> indexes = ct.getIndexes();
        if (indexes != null) {
            for (Index idx : indexes) {
                String type = idx.getType();
                if (type != null && type.equalsIgnoreCase("primary key")) {
                    for (String col : RoutineSqlUtils.safeList(idx.getColumnsNames())) {
                        if (!op.primaryKeys.contains(col)) op.primaryKeys.add(col);
                    }
                }
                if (idx instanceof ForeignKeyIndex) {
                    ForeignKeyIndex fkIdx = (ForeignKeyIndex) idx;
                    ForeignKeyDefinition fk = new ForeignKeyDefinition();
                    fk.name = fkIdx.getName();
                    fk.columns.addAll(RoutineSqlUtils.safeList(fkIdx.getColumnsNames()));
                    Table ref = fkIdx.getTable();
                    if (ref != null) {
                        fk.referencedTable = RoutineSqlUtils.qualifyName(ref.getSchemaName(), ref.getName());
                    }
                    fk.referencedColumns.addAll(RoutineSqlUtils.safeList(fkIdx.getReferencedColumnNames()));
                    // onDelete/onUpdate not extracted; leave null intentionally
                    op.foreignKeys.add(fk);
                }
            }
        }

        out.createTables.add(op);
    }

    private void handleCreateView(CreateView cv, SQLFileAnalysis out) {
        boolean isAlter = cv.isOrReplace();
        
        if (isAlter) {
            AlterViewOperation op = new AlterViewOperation();
            extractViewData(cv.getView(), cv.getSelect(), 
                (name) -> op.viewName = name,
                (schema) -> op.schema = schema,
                op.references.relations);
            out.alterViews.add(op);
        } else {
            CreateViewOperation op = new CreateViewOperation();
            extractViewData(cv.getView(), cv.getSelect(),
                (name) -> op.viewName = name,
                (schema) -> op.schema = schema,
                op.references.relations);
            out.createViews.add(op);
        }
    }

    private void handleCreateIndex(CreateIndex ci, SQLFileAnalysis out) {
        CreateIndexOperation op = new CreateIndexOperation();
        if (ci.getIndex() != null) {
            op.indexName = ci.getIndex().getName();

            // Extract columns using the inner Index API (compatible with older versions)
            List<ColumnParams> params = ci.getIndex().getColumns();
            if (params != null) {
                for (ColumnParams cp : params) {
                    String name = cp.getColumnName();
                    if (name != null && !name.isEmpty()) op.columns.add(name);
                }
            }

            // Uniqueness might be encoded in the type string
            String t = ci.getIndex().getType();
            if (t != null && t.toUpperCase().contains("UNIQUE")) {
                op.unique = true;
            }
        }
        if (ci.getTable() != null) {
            Table t = ci.getTable();
            op.tableName = t.getName();
            op.schema = t.getSchemaName();
        }
        out.createIndexes.add(op);
    }

    // ---------- Helpers ----------

    /**
     * Fallback parser using ANTLR when JSqlParser fails.
     * Uses dialect-specific ANTLR grammars.
     */
    private Analysis parseWithAntlr(String filePath, String sourceCode, String dialect) {
        SQLFileAnalysis out = new SQLFileAnalysis();
        out.filePath = filePath;
        out.language = "sql";

        if ("tsql".equals(dialect)) {
            try {
                AntlrParserFactory.ParserWithTokens<org.dxworks.codeframe.analyzer.sql.generated.TSqlParser> parserWithTokens =
                    AntlrParserFactory.createTSqlParserWithTokens(sourceCode);
                
                org.antlr.v4.runtime.tree.ParseTree tree = parserWithTokens.getParser().tsql_file();
                
                TSqlDefinitionExtractor extractor = new TSqlDefinitionExtractor(sourceCode, parserWithTokens.getTokens());
                extractor.visit(tree);
                
                out.createProcedures.addAll(extractor.getProcedures());
                out.createFunctions.addAll(extractor.getFunctions());
                out.createTriggers.addAll(extractor.getTriggers());
                
            } catch (Exception e) {
                // If ANTLR also fails, return empty
            }
        } else if ("plsql".equals(dialect)) {
            try {
                org.dxworks.codeframe.analyzer.sql.generated.PlSqlParser parser =
                    AntlrParserFactory.createPlSqlParser(sourceCode);

                org.antlr.v4.runtime.tree.ParseTree tree = parser.sql_script();

                PlSqlDefinitionExtractor extractor = new PlSqlDefinitionExtractor();
                extractor.visit(tree);

                out.createProcedures.addAll(extractor.getProcedures());
                out.createFunctions.addAll(extractor.getFunctions());
                out.createTriggers.addAll(extractor.getTriggers());

                // Also collect top-level PL/SQL references and calls (anonymous blocks, CALL, DML)
                PlSqlTopLevelAnalyzer.analyzeTopLevel(sourceCode, out);

            } catch (Exception e) {
                // swallow
            }
        }
        // Add more dialects here (plpgsql, mysql, etc.)

        return out;
    }


    private static String toTypeString(ColDataType dt) {
        if (dt == null) return null;
        String base = dt.getDataType();
        List<String> args = dt.getArgumentsStringList();
        String result;
        if (args != null && !args.isEmpty()) {
            result = base + "(" + String.join(",", args) + ")";
        } else {
            result = base;
        }
        // Normalize to remove any extra spaces from JSqlParser output
        return RoutineSqlUtils.normalizeTypeFormat(result);
    }

    private static List<String> normalize(List<String> specs) {
        List<String> out = new ArrayList<>();
        for (String s : specs) out.add(s.toLowerCase());
        return out;
    }

    private static boolean containsSequence(List<String> items, String a, String b) {
        for (int i = 0; i < items.size() - 1; i++) {
            if (items.get(i).equals(a) && items.get(i + 1).equals(b)) return true;
        }
        return false;
    }


    // ---- View Extraction Helpers ----

    private void extractViewData(Table view, Object select, 
                                 java.util.function.Consumer<String> nameConsumer,
                                 java.util.function.Consumer<String> schemaConsumer,
                                 java.util.Collection<String> references) {
        if (view != null) {
            nameConsumer.accept(view.getName());
            schemaConsumer.accept(view.getSchemaName());
        }
        
        if (select != null) {
            TableReferenceExtractor.extractTableReferences((Statement) select, references);
        }
    }

    // ---- ALTER TABLE Expression Processing ----

    private void processAlterExpression(AlterExpression ae, AlterTableOperation op) {
        Object opObj = ae.getOperation();
        String opStr = opObj == null ? "" : opObj.toString();

        if (opStr.equals("ADD") || opStr.equals("ADD_COLUMN") || opStr.equals("ADD_COLUMN_IF_NOT_EXISTS")) {
            handleAddOperation(ae, op);
        } else if (opStr.equals("DROP") || opStr.equals("DROP_COLUMN") || opStr.equals("DROP_COLUMN_IF_EXISTS")) {
            handleDropColumnOperation(ae, op);
        } else if (opStr.equals("ADD_CONSTRAINT")) {
            handleAddConstraintOperation(ae, op);
        } else if (opStr.equals("DROP_CONSTRAINT")) {
            handleDropConstraintOperation(ae, op);
        }
        // Ignore other operations
    }

    private void handleAddOperation(AlterExpression ae, AlterTableOperation op) {
        // Check if this ADD is a constraint
        if (tryAddConstraint(ae, op)) {
            return;
        }

        // Otherwise, handle ADD COLUMN
        List<AlterExpression.ColumnDataType> cds = ae.getColDataTypeList();
        if (cds != null) {
            for (AlterExpression.ColumnDataType cdt : cds) {
                org.dxworks.codeframe.model.sql.ColumnDefinition cd = 
                    new org.dxworks.codeframe.model.sql.ColumnDefinition();
                cd.name = cdt.getColumnName();
                cd.type = toTypeString(cdt.getColDataType());
                op.addedColumns.add(cd);
            }
            if (op.operationType == null) {
                op.operationType = "ADD_COLUMN";
            }
        }
    }

    private boolean tryAddConstraint(AlterExpression ae, AlterTableOperation op) {
        try {
            Object idxObj = ae.getIndex();
            if (idxObj instanceof ForeignKeyIndex) {
                String constraint = ConstraintBuilder.buildForeignKeyConstraint((ForeignKeyIndex) idxObj);
                if (constraint != null) {
                    op.addedConstraints.add(constraint);
                    if (op.operationType == null) {
                        op.operationType = "ADD_CONSTRAINT";
                    }
                    return true;
                }
            } else if (idxObj instanceof Index) {
                Index idx = (Index) idxObj;
                if (ConstraintBuilder.isPrimaryKey(idx)) {
                    String constraint = ConstraintBuilder.buildPrimaryKeyConstraint(idx);
                    if (constraint != null) {
                        op.addedConstraints.add(constraint);
                        if (op.operationType == null) {
                            op.operationType = "ADD_CONSTRAINT";
                        }
                        return true;
                    }
                }
            }
        } catch (Exception ignore) {
            // Silently ignore parsing errors
        }
        return false;
    }

    private void handleAddConstraintOperation(AlterExpression ae, AlterTableOperation op) {
        tryAddConstraint(ae, op);
    }

    private void handleDropColumnOperation(AlterExpression ae, AlterTableOperation op) {
        String colName = extractColumnName(ae);
        if (colName != null && !colName.isEmpty()) {
            op.droppedColumns.add(RoutineSqlUtils.stripQuotes(colName));
            if (op.operationType == null) {
                op.operationType = "DROP_COLUMN";
            }
        }
    }

    private void handleDropConstraintOperation(AlterExpression ae, AlterTableOperation op) {
        String cname = ae.getConstraintName();
        if (cname != null && !cname.isEmpty()) {
            op.droppedConstraints.add(RoutineSqlUtils.stripQuotes(cname));
            if (op.operationType == null) {
                op.operationType = "DROP_CONSTRAINT";
            }
        }
    }

    private String extractColumnName(AlterExpression ae) {
        try {
            String colName = ae.getColumnName();
            if (colName != null) return colName;
        } catch (Exception ignored) {
            // Method might not exist or throw
        }
        
        try {
            return ae.getColOldName();
        } catch (Exception ignored) {
            return null;
        }
    }

    // ---- Trigger extraction (regex fallback for PostgreSQL/MySQL) ----
    // JSqlParser doesn't support CREATE TRIGGER, so we use regex-based extraction

    private static final String SQL_IDENTIFIER = "[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?";
    private static final String DML_EVENTS = "INSERT|UPDATE|DELETE";

    // PostgreSQL: CREATE [OR REPLACE] TRIGGER name timing events ON table ... EXECUTE FUNCTION/PROCEDURE
    // Requires EXECUTE FUNCTION/PROCEDURE to distinguish from MySQL
    private static final Pattern POSTGRES_TRIGGER_PATTERN = Pattern.compile(
        "CREATE\\s+(OR\\s+REPLACE\\s+)?TRIGGER\\s+(" + SQL_IDENTIFIER + ")\\s+" +
        "(BEFORE|AFTER|INSTEAD\\s+OF)\\s+" +
        "((?:" + DML_EVENTS + ")(?:\\s+OR\\s+(?:" + DML_EVENTS + "))*)\\s+" +
        "ON\\s+(" + SQL_IDENTIFIER + ")\\s+" +
        "(?:FOR\\s+EACH\\s+(?:ROW|STATEMENT)\\s+)?EXECUTE\\s+(?:FUNCTION|PROCEDURE)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // MySQL: CREATE TRIGGER name timing event ON table (single event, no OR REPLACE)
    private static final Pattern MYSQL_TRIGGER_PATTERN = Pattern.compile(
        "CREATE\\s+TRIGGER\\s+(" + SQL_IDENTIFIER + ")\\s+" +
        "(BEFORE|AFTER)\\s+(" + DML_EVENTS + ")\\s+" +
        "ON\\s+(" + SQL_IDENTIFIER + ")",
        Pattern.CASE_INSENSITIVE
    );

    // PostgreSQL EXECUTE FUNCTION/PROCEDURE clause
    private static final Pattern EXECUTE_PATTERN = Pattern.compile(
        "EXECUTE\\s+(FUNCTION|PROCEDURE)\\s+(" + SQL_IDENTIFIER + ")\\s*\\([^)]*\\)",
        Pattern.CASE_INSENSITIVE
    );

    // MySQL body patterns
    private static final Pattern MYSQL_END_DOLLAR_PATTERN = Pattern.compile(
        "\\bEND\\s*\\$\\$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MYSQL_BEGIN_END_SEMI_PATTERN = Pattern.compile(
        "BEGIN\\s+(.+?)\\bEND\\s*;\\s*(?=\\n|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MYSQL_SINGLE_STMT_PATTERN = Pattern.compile(
        "FOR\\s+EACH\\s+ROW\\s+(?!BEGIN)(.+?)(?:;|\\$\\$|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private void extractTriggersFromSource(String sourceCode, SQLFileAnalysis out) {
        if (sourceCode == null || sourceCode.isEmpty()) return;

        Set<Integer> matchedPositions = new HashSet<>();
        extractPostgresTriggers(sourceCode, out, matchedPositions);
        extractMySqlTriggers(sourceCode, out, matchedPositions);
    }

    private void extractPostgresTriggers(String sourceCode, SQLFileAnalysis out, Set<Integer> matchedPositions) {
        Matcher matcher = POSTGRES_TRIGGER_PATTERN.matcher(sourceCode);
        while (matcher.find()) {
            matchedPositions.add(matcher.start());

            CreateTriggerOperation op = new CreateTriggerOperation();
            op.orReplace = matcher.group(1) != null;
            setSchemaAndName(op, matcher.group(2));
            op.timing = normalizeTiming(matcher.group(3));
            extractEvents(matcher.group(4), op.events);
            op.tableName = matcher.group(5);

            extractPostgresExecuteCall(sourceCode.substring(matcher.start()), op);
            out.createTriggers.add(op);
        }
    }

    private void extractMySqlTriggers(String sourceCode, SQLFileAnalysis out, Set<Integer> matchedPositions) {
        Matcher matcher = MYSQL_TRIGGER_PATTERN.matcher(sourceCode);
        while (matcher.find()) {
            if (matchedPositions.contains(matcher.start())) continue;

            CreateTriggerOperation op = new CreateTriggerOperation();
            op.orReplace = false;
            setSchemaAndName(op, matcher.group(1));
            op.timing = matcher.group(2).toUpperCase();
            op.events.add(matcher.group(3).toUpperCase());
            op.tableName = matcher.group(4);

            analyzeMySqlTriggerBody(sourceCode.substring(matcher.start()), op);
            out.createTriggers.add(op);
        }
    }

    private void setSchemaAndName(CreateTriggerOperation op, String fullName) {
        String[] parts = RoutineSqlUtils.splitSchemaAndName(fullName);
        op.schema = parts[0];
        op.triggerName = parts[1];
    }

    private String normalizeTiming(String timing) {
        if (timing == null) return null;
        String normalized = timing.toUpperCase().replaceAll("\\s+", " ").trim();
        return normalized.contains("INSTEAD") ? "INSTEAD OF" : normalized;
    }

    private void extractEvents(String eventsClause, List<String> events) {
        if (eventsClause == null) return;
        Matcher matcher = Pattern.compile(DML_EVENTS, Pattern.CASE_INSENSITIVE).matcher(eventsClause);
        while (matcher.find()) {
            events.add(matcher.group().toUpperCase());
        }
    }

    private void extractPostgresExecuteCall(String triggerText, CreateTriggerOperation op) {
        Matcher matcher = EXECUTE_PATTERN.matcher(triggerText);
        if (matcher.find()) {
            String keyword = matcher.group(1).toUpperCase();
            String name = matcher.group(2);
            if ("FUNCTION".equals(keyword)) {
                op.calls.functions.add(name);
            } else {
                op.calls.procedures.add(name);
            }
        }
    }

    private void analyzeMySqlTriggerBody(String triggerText, CreateTriggerOperation op) {
        String body = extractMySqlTriggerBody(triggerText);
        if (body == null || body.isEmpty()) return;

        RoutineBodyAnalyzer.Result result = sqlRoutineAnalyzer.analyze(body, "mysql");
        if (result != null) {
            if (result.relations != null) op.references.relations.addAll(result.relations);
            if (result.functionCalls != null) op.calls.functions.addAll(result.functionCalls);
            if (result.procedureCalls != null) op.calls.procedures.addAll(result.procedureCalls);
        }
    }

    private String extractMySqlTriggerBody(String triggerText) {
        // Try BEGIN...END$$ (custom delimiter)
        String body = extractBeginEndDollarBody(triggerText);
        if (body != null) return body;

        // Try BEGIN...END; (standard delimiter)
        Matcher semiMatcher = MYSQL_BEGIN_END_SEMI_PATTERN.matcher(triggerText);
        if (semiMatcher.find()) return semiMatcher.group(1);

        // Try single statement (no BEGIN...END)
        Matcher singleMatcher = MYSQL_SINGLE_STMT_PATTERN.matcher(triggerText);
        if (singleMatcher.find()) return singleMatcher.group(1);

        return null;
    }

    private String extractBeginEndDollarBody(String text) {
        int beginIdx = text.toUpperCase().indexOf("BEGIN");
        if (beginIdx < 0) return null;

        Matcher endMatcher = MYSQL_END_DOLLAR_PATTERN.matcher(text);
        if (!endMatcher.find(beginIdx)) return null;

        int bodyStart = beginIdx + "BEGIN".length();
        int bodyEnd = endMatcher.start();
        return bodyEnd > bodyStart ? text.substring(bodyStart, bodyEnd).trim() : null;
    }
}
