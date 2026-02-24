package org.dxworks.codeframe.analyzer.sql;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.function.CreateFunction;
import net.sf.jsqlparser.statement.create.procedure.CreateProcedure;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.create.view.AlterView;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.execute.Execute;
import org.dxworks.codeframe.analyzer.LanguageAnalyzer;
import org.dxworks.codeframe.model.Analysis;
import org.dxworks.codeframe.model.sql.*;

import org.treesitter.TSNode;

public class SQLAnalyzer implements LanguageAnalyzer {

    private final RoutineBodyAnalyzer noopAnalyzer = new NoopRoutineBodyAnalyzer();
    private final RoutineBodyAnalyzer tsqlAnalyzer = AntlrRoutineBodyAnalyzer.forTSql();
    private final RoutineBodyAnalyzer sqlRoutineAnalyzer = new SqlRoutineBodyAnalyzer();
    private final RoutineAnalysisService routineAnalysisService =
            new RoutineAnalysisService(noopAnalyzer, tsqlAnalyzer, sqlRoutineAnalyzer);
    private final DdlStatementHandler ddlHandler = new DdlStatementHandler();
    private final TriggerRegexExtractor triggerExtractor = new TriggerRegexExtractor(sqlRoutineAnalyzer);

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
            triggerExtractor.extractTriggersFromSource(sourceCode, out);
            return out;
        }

        for (Statement st : statements.getStatements()) {
            if (st == null) continue;

            if (st instanceof CreateTable) {
                ddlHandler.handleCreateTable((CreateTable) st, out);
            } else if (st instanceof CreateView) {
                ddlHandler.handleCreateView((CreateView) st, out);
            } else if (st instanceof AlterView) {
                ddlHandler.handleAlterViewAst((AlterView) st, out);
            } else if (st instanceof CreateIndex) {
                ddlHandler.handleCreateIndex((CreateIndex) st, out);
            } else if (st instanceof Alter) {
                ddlHandler.handleAlter((Alter) st, out, sourceCode);
            } else if (st instanceof Drop) {
                ddlHandler.handleDrop((Drop) st, out);
            } else if (st instanceof CreateFunction) {
                routineAnalysisService.handleCreateFunction((CreateFunction) st, out, sourceCode);
            } else if (st instanceof CreateProcedure) {
                routineAnalysisService.handleCreateProcedure((CreateProcedure) st, out, sourceCode);
            } else {
                handleTopLevelStatement(st, out);
            }
        }

        // Fallback: Extract triggers using regex (JSqlParser doesn't support CREATE TRIGGER)
        triggerExtractor.extractTriggersFromSource(sourceCode, out);

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
        ExpressionAnalyzer.collectFunctions(st, out.topLevelCalls.functions);
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

}
