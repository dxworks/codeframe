package org.dxworks.codeframe.analyzer.sql;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.execute.Execute;

import java.util.Locale;

public class SqlRoutineBodyAnalyzer implements RoutineBodyAnalyzer {

    @Override
    public Result analyze(String body, String dialectHint) {
        Result out = new Result();
        if (body == null) return out;
        String src = body.trim();
        if (src.isEmpty()) return out;

        String pre = stripBeginEnd(src);
        String hint = dialectHint == null ? "" : dialectHint.toLowerCase(Locale.ROOT);
        boolean isPlpgsql = "plpgsql".equals(hint) || ("unknown".equals(hint) && looksLikePlpgsql(src));
        boolean isMySql = "mysql".equals(hint);

        if (isPlpgsql) {
            pre = preprocessPostgreSQL(pre);
        } else if (isMySql) {
            pre = preprocessMySql(pre);
        }

        try {
            Statements statements = CCJSqlParserUtil.parseStatements(pre);

            for (Statement st : statements.getStatements()) {
                if (st == null) continue;
                TableReferenceExtractor.extractTableReferences(st, out.relations);
                ExpressionAnalyzer.collectFunctions(st, out.functionCalls);
                String call = extractCallName(st);
                if (call != null && !call.isEmpty()) out.procedureCalls.add(call);
            }
        } catch (Exception e) {
            // best-effort
        }

        return out;
    }


    private static String extractCallName(Statement st) {
        if (st instanceof Execute) {
            Execute exec = (Execute) st;
            if (exec.getName() != null) {
                String name = exec.getName().toString();
                return RoutineSqlUtils.stripQuotes(name);
            }
        }
        return null;
    }

    /**
     * Aggressive simplification for PostgreSQL routine bodies.
     * Goal: Strip all procedural logic, keep only data access statements (SELECT/INSERT/UPDATE/DELETE/CALL).
     * This allows JSqlParser to extract table references and function/procedure calls.
     * Package-private for use by UnifiedTSqlRoutineBodyAnalyzer.
     */
    static String preprocessPostgreSQL(String body) {
        if (body == null) return null;

        // PASS 1: Line-by-line filtering
        String[] lines = body.split("\n");
        StringBuilder pass1 = new StringBuilder(body.length());

        boolean inDeclare = false;
        for (String rawLine : lines) {
            if (rawLine == null) continue;
            String line = rawLine;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String upper = trimmed.toUpperCase(Locale.ROOT);

            // Skip DECLARE blocks
            if (!inDeclare && upper.startsWith("DECLARE")) {
                inDeclare = true;
                continue;
            }
            if (inDeclare) {
                if (upper.startsWith("BEGIN")) inDeclare = false;
                continue;
            }

            // Handle IF EXISTS with embedded SELECT - extract inner query for table reference extraction
            if (upper.startsWith("IF ") && upper.contains("EXISTS")) {
                String extracted = extractSelectFromExists(trimmed, upper);
                if (extracted != null) {
                    pass1.append(extracted).append('\n');
                }
                continue;
            }

            // Skip control flow keywords
            if (isControlFlowKeyword(upper)) {
                continue;
            }

            // Handle RETURN statements
            if (upper.startsWith("RETURN QUERY") && upper.contains("SELECT")) {
                // Extract SELECT from RETURN QUERY SELECT
                int selIdx = upper.indexOf("SELECT");
                if (selIdx >= 0 && selIdx < line.length()) {
                    line = line.substring(selIdx).trim();
                }
            } else if (upper.startsWith("RETURN")) {
                continue; // Skip other RETURN statements
            }

            // Transform PERFORM to SELECT (PostgreSQL-specific)
            line = line.replaceAll("(?i)\\bPERFORM\\b", "SELECT");

            // Transform assignments to SELECT to preserve function calls in RHS
            if (trimmed.matches("[A-Za-z_][A-Za-z0-9_]*\\s*:=.*")) {
                int idx = line.indexOf(":=");
                if (idx >= 0) {
                    String rhs = line.substring(idx + 2).trim();
                    if (rhs.endsWith(";")) rhs = rhs.substring(0, rhs.length() - 1).trim();
                    if (!rhs.isEmpty()) {
                        line = "SELECT " + rhs + ";";
                    } else {
                        continue;
                    }
                }
            }

            pass1.append(line).append('\n');
        }

        // PASS 2: Full-text normalization (handles multi-line constructs)
        String normalized = pass1.toString();
        normalized = removePostgreSQLTypeCasts(normalized);
        normalized = removeIntoVariables(normalized);
        normalized = normalizeCallToExec(normalized);
        return normalized;
    }

    /**
     * Aggressive simplification for MySQL routine bodies.
     * Goal: Strip all procedural logic, keep only data access statements.
     * Package-private for use by UnifiedTSqlRoutineBodyAnalyzer.
     */
    static String preprocessMySql(String body) {
        if (body == null) return null;

        // PASS 1: Line-by-line filtering
        String[] lines = body.split("\n");
        StringBuilder pass1 = new StringBuilder(body.length());

        for (String rawLine : lines) {
            if (rawLine == null) continue;
            String line = rawLine;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String upper = trimmed.toUpperCase(Locale.ROOT);

            // Skip declarations
            if (upper.startsWith("DECLARE ")) continue;

            // Skip control flow keywords
            if (isMySQLControlFlowKeyword(upper)) continue;

            // Transform standalone SET assignments to SELECT (to preserve function calls)
            // Keep UPDATE SET clauses unchanged (they don't end with ;)
            if (upper.startsWith("SET ") && trimmed.endsWith(";")) {
                line = transformSetToSelect(line);
                if (line == null) continue;
            }

            // Transform RETURN to SELECT (to preserve function calls)
            if (upper.startsWith("RETURN ")) {
                line = transformReturnToSelect(trimmed);
                if (line == null) continue;
            } else if (upper.equals("RETURN") || upper.equals("RETURN;")) {
                continue; // Skip bare RETURN
            }

            pass1.append(line).append('\n');
        }

        // PASS 2: Full-text normalization
        String normalized = pass1.toString();
        normalized = removeIntoVariables(normalized);
        normalized = normalizeCallToExec(normalized);
        return normalized;
    }

    // ==================== Helper Methods ====================

    private static boolean isControlFlowKeyword(String upper) {
        return upper.startsWith("IF ") || upper.startsWith("ELSIF ") || upper.equals("ELSE") ||
               upper.startsWith("END IF") || upper.startsWith("WHILE ") || upper.startsWith("LOOP") ||
               upper.startsWith("END LOOP") || upper.startsWith("FOR ") || upper.equals("END") ||
               upper.equals("END;") || upper.startsWith("BEGIN") || upper.startsWith("EXCEPTION") ||
               upper.startsWith("WHEN ") || upper.startsWith("RAISE ") || upper.startsWith("CONTINUE") ||
               upper.startsWith("EXIT") || upper.startsWith("THEN");
    }

    private static boolean isMySQLControlFlowKeyword(String upper) {
        return upper.startsWith("IF ") || upper.startsWith("ELSEIF ") || upper.equals("ELSE") ||
               upper.startsWith("END IF") || upper.startsWith("WHILE ") || upper.startsWith("LOOP") ||
               upper.startsWith("END LOOP") || upper.startsWith("REPEAT") || upper.startsWith("UNTIL") ||
               upper.equals("END") || upper.equals("END;") || upper.startsWith("BEGIN") ||
               upper.startsWith("CASE") || upper.startsWith("WHEN ") || upper.startsWith("LEAVE") ||
               upper.startsWith("ITERATE") || upper.startsWith("THEN");
    }

    private static String transformSetToSelect(String line) {
        int idx = line.indexOf('=');
        if (idx >= 0) {
            String rhs = line.substring(idx + 1).trim();
            if (rhs.endsWith(";")) rhs = rhs.substring(0, rhs.length() - 1).trim();
            if (!rhs.isEmpty()) {
                return "SELECT " + rhs + ";";
            }
        }
        return null;
    }

    private static String transformReturnToSelect(String trimmed) {
        String expr = trimmed.substring("RETURN".length()).trim();
        if (expr.endsWith(";")) expr = expr.substring(0, expr.length() - 1).trim();
        if (!expr.isEmpty()) {
            return "SELECT " + expr + ";";
        }
        return null;
    }

    private static String removePostgreSQLTypeCasts(String sql) {
        sql = sql.replaceAll("::DECIMAL\\s*\\(([^)]+)\\)", "");
        sql = sql.replaceAll("::([A-Za-z_][A-Za-z0-9_]*)", "");
        return sql;
    }

    private static String removeIntoVariables(String sql) {
        sql = sql.replaceAll("(?i)\\bINTO\\s+[A-Za-z_][A-Za-z0-9_\\s,]*\\s+FROM", " FROM");
        sql = sql.replaceAll("(?i)\\bINTO\\s+[A-Za-z_][A-Za-z0-9_\\s,]*\\s*\n", "\n");
        return sql;
    }

    private static String normalizeCallToExec(String sql) {
        return sql.replaceAll("(?im)^\\s*CALL\\s+", "EXEC ");
    }

    private static boolean looksLikePlpgsql(String body) {
        if (body == null) return false;
        String s = body.toLowerCase(Locale.ROOT);
        if (s.contains("language plpgsql")) return true;
        if (s.contains("$$")) return true;
        if (s.contains("declare") && s.contains("begin")) return true;
        return false;
    }

    /**
     * Extracts a SELECT statement from an IF EXISTS(...) clause.
     * Returns the SELECT with a trailing semicolon, or null if extraction fails.
     */
    private static String extractSelectFromExists(String trimmed, String upper) {
        int selectIdx = upper.indexOf("SELECT");
        if (selectIdx < 0) return null;
        
        int existsIdx = upper.indexOf("EXISTS");
        if (existsIdx < 0) return null;
        
        // Find closing paren of EXISTS clause (start searching after "EXISTS")
        int endIdx = findMatchingParen(trimmed, existsIdx + "EXISTS".length());
        if (endIdx <= selectIdx) return null;
        
        String innerSelect = trimmed.substring(selectIdx, endIdx).trim();
        return innerSelect.endsWith(";") ? innerSelect : innerSelect + ";";
    }

    /**
     * Finds the position of the closing parenthesis matching the opening paren after startIdx.
     * Returns -1 if not found.
     */
    private static int findMatchingParen(String s, int startIdx) {
        if (startIdx < 0 || startIdx >= s.length()) return -1;
        int openIdx = s.indexOf('(', startIdx);
        if (openIdx < 0) return -1;
        
        int depth = 1;
        for (int i = openIdx + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static String stripBeginEnd(String s) {
        String t = s.trim();
        String tl = t.toLowerCase();
        int b = tl.indexOf("begin");
        int e = tl.lastIndexOf("end");
        if (b >= 0 && e > b) {
            int start = t.indexOf('\n', b);
            if (start < 0) start = b + 5;
            String inner = t.substring(Math.min(start + 1, t.length()), e).trim();
            return inner;
        }
        return s;
    }

}
