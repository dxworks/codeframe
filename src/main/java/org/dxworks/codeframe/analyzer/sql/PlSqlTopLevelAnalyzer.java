package org.dxworks.codeframe.analyzer.sql;

import org.antlr.v4.runtime.tree.ParseTree;
import org.dxworks.codeframe.analyzer.sql.generated.PlSqlParser;
import org.dxworks.codeframe.model.sql.SQLFileAnalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ANTLR-based analyzer for PL/SQL top-level statements.
 * <p>
 * Populates {@link SQLFileAnalysis#topLevelReferences} and {@link SQLFileAnalysis#topLevelCalls}
 * for PL/SQL files when using the ANTLR fallback path.
 * <p>
 * Top-level statements include:
 * <ul>
 *   <li>Anonymous blocks (BEGIN...END)</li>
 *   <li>Standalone CALL statements</li>
 *   <li>EXECUTE statements</li>
 *   <li>Top-level DML (SELECT, INSERT, UPDATE, DELETE)</li>
 * </ul>
 */
public final class PlSqlTopLevelAnalyzer {

    /** Pattern to match EXECUTE-style procedure calls: EXECUTE schema.proc_name */
    private static final Pattern EXECUTE_PATTERN = 
            Pattern.compile("(?im)^\\s*EXECUTE\\s+([A-Za-z0-9_\".]+)");

    private PlSqlTopLevelAnalyzer() {
        // utility class
    }

    /**
     * Analyzes top-level PL/SQL statements and populates the output with references and calls.
     *
     * @param source the PL/SQL source code
     * @param out    the analysis result to populate
     */
    public static void analyzeTopLevel(String source, SQLFileAnalysis out) {
        if (source == null || out == null) return;

        try {
            collectFromParseTree(source, out);
            collectExecuteStatements(source, out);
        } catch (Exception ignore) {
            // Graceful degradation: leave top-level data empty on hard parse errors
        }
    }

    /**
     * Parses the source and collects references/calls from unit_statement nodes.
     */
    private static void collectFromParseTree(String source, SQLFileAnalysis out) {
        PlSqlParser parser = AntlrParserFactory.createPlSqlParser(source);
        PlSqlParser.Sql_scriptContext script = parser.sql_script();

        List<PlSqlParser.Unit_statementContext> units = findAllUnitStatements(script);
        PlSqlTopLevelCollector collector = new PlSqlTopLevelCollector();
        
        for (PlSqlParser.Unit_statementContext unit : units) {
            collector.visitUnit_statement(unit);
        }

        out.topLevelReferences.relations.addAll(collector.getTableReferences());
        out.topLevelCalls.procedures.addAll(collector.getProcedureCalls());
        out.topLevelCalls.functions.addAll(collector.getFunctionCalls());
    }

    /**
     * Best-effort capture of EXECUTE-style procedure calls not modeled in the grammar.
     * Example: EXECUTE HR.REPORT_CUSTOMER_ORDERS(789);
     */
    private static void collectExecuteStatements(String source, SQLFileAnalysis out) {
        Matcher matcher = EXECUTE_PATTERN.matcher(source);
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (raw == null) continue;
            
            String name = RoutineSqlUtils.stripQuotes(raw.trim());
            if (name == null || name.isEmpty()) continue;
            
            out.topLevelCalls.procedures.add(name);
            // Prefer procedure classification over function if both matched
            out.topLevelCalls.functions.remove(name);
        }
    }

    /**
     * Recursively finds all {@link PlSqlParser.Unit_statementContext} nodes in the parse tree.
     * <p>
     * The PL/SQL grammar nests unit_statements inside intermediate nodes, so a simple
     * visitor pattern may not reach them. This method traverses the entire tree to find
     * all unit_statement nodes regardless of nesting depth.
     *
     * @param root the root of the parse tree to search
     * @return list of all unit_statement contexts found
     */
    private static List<PlSqlParser.Unit_statementContext> findAllUnitStatements(ParseTree root) {
        List<PlSqlParser.Unit_statementContext> result = new ArrayList<>();
        collectUnitStatements(root, result);
        return result;
    }

    private static void collectUnitStatements(ParseTree node, List<PlSqlParser.Unit_statementContext> result) {
        if (node == null) return;
        
        if (node instanceof PlSqlParser.Unit_statementContext) {
            result.add((PlSqlParser.Unit_statementContext) node);
            return; // Don't recurse into unit_statement children
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            collectUnitStatements(node.getChild(i), result);
        }
    }
}
