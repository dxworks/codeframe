package org.dxworks.codeframe.analyzer.sql;

import org.dxworks.codeframe.model.sql.CreateTriggerOperation;
import org.dxworks.codeframe.model.sql.SQLFileAnalysis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based trigger extraction for PostgreSQL and MySQL.
 * JSqlParser doesn't support CREATE TRIGGER, so we use regex as a fallback.
 */
final class TriggerRegexExtractor {

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

    private final RoutineBodyAnalyzer bodyAnalyzer;

    TriggerRegexExtractor(RoutineBodyAnalyzer bodyAnalyzer) {
        this.bodyAnalyzer = bodyAnalyzer;
    }

    void extractTriggersFromSource(String sourceCode, SQLFileAnalysis out) {
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

        RoutineBodyAnalyzer.Result result = bodyAnalyzer.analyze(body, "mysql");
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
