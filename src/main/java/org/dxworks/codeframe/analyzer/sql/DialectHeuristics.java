package org.dxworks.codeframe.analyzer.sql;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic detection of SQL dialects from source text.
 *
 * <h3>Dialect detection priority ({@link #detectDialectFromSource}):</h3>
 * <ol>
 *   <li><b>tsql</b> — {@code CREATE OR ALTER PROCEDURE/FUNCTION} (T-SQL specific syntax)</li>
 *   <li><b>tsql</b> — standalone {@code GO} batch separator on its own line</li>
 *   <li><b>mysql</b> — {@code DELIMITER $$} or {@code ENGINE=InnoDB}</li>
 *   <li><b>plsql</b> — {@code CREATE OR REPLACE} without PL/pgSQL or MySQL markers</li>
 *   <li><b>plsql</b> — {@code BEGIN} with trailing {@code /} line (Oracle convention)</li>
 *   <li><b>plpgsql</b> — {@code LANGUAGE plpgsql} or non-MySQL {@code $$} quoting</li>
 *   <li><b>unknown</b> — fallback</li>
 * </ol>
 *
 * <h3>Body dialect hint priority ({@link #detectDialectHint}):</h3>
 * <ol>
 *   <li><b>plpgsql</b> — {@code LANGUAGE plpgsql}</li>
 *   <li><b>mysql</b> — MySQL keywords ({@code DETERMINISTIC}, {@code READS SQL DATA}, etc.)</li>
 *   <li><b>plpgsql</b> — {@code $$} quoting</li>
 *   <li><b>tsql</b> — {@code AS BEGIN}, {@code EXEC}, {@code EXECUTE}, {@code GO}</li>
 *   <li><b>mysql</b> — {@code BEGIN} without T-SQL markers</li>
 *   <li><b>unknown</b> — fallback</li>
 * </ol>
 */
public final class DialectHeuristics {

    private DialectHeuristics() {
        // utility class
    }

    public static String detectDialectFromSource(String source) {
        if (source == null) return "unknown";

        String lower = source.toLowerCase();

        if (lower.contains("create or alter procedure") ||
            lower.contains("create or alter function")) {
            return "tsql";
        }

        if (Pattern.compile("(?m)^\\s*go\\s*$").matcher(lower).find()) {
            return "tsql";
        }

        if (lower.contains("delimiter $$") || lower.contains("engine=innodb")) {
            return "mysql";
        }

        if ((lower.contains("create or replace procedure") || lower.contains("create or replace function"))
                && !lower.contains("language plpgsql")
                && !lower.contains("delimiter $$")) {
            return "plsql";
        }

        // Heuristic: anonymous PL/SQL blocks (BEGIN...END with trailing slash line)
        // that are not marked as PL/pgSQL or MySQL
        if (!lower.contains("language plpgsql")
                && !lower.contains("delimiter $$")) {
            boolean hasBegin = lower.contains("begin");
            boolean hasTrailingSlash = java.util.regex.Pattern
                    .compile("(?m)^\\s*/\\s*$")
                    .matcher(lower)
                    .find();
            if (hasBegin && hasTrailingSlash) {
                return "plsql";
            }
        }

        if (lower.contains("language plpgsql") ||
            (lower.contains("$$") && !lower.contains("delimiter $$"))) {
            return "plpgsql";
        }

        return "unknown";
    }

    public static String[] extractRoutineBodyAndDialect(String source,
                                                        String routineName,
                                                        boolean isFunction) {
        if (source == null) return new String[]{null, "unknown"};
        String fullBody = extractRoutineBodyFromSource(source, routineName, isFunction);
        if (fullBody == null) return new String[]{null, "unknown"};
        String hint = detectDialectHint(fullBody);
        Pattern dollarQuote = Pattern.compile("(?s)\\$\\$(.*?)\\$\\$");
        Matcher dollarMatch = dollarQuote.matcher(fullBody);
        if (dollarMatch.find()) {
            String innerBody = dollarMatch.group(1).trim();
            return new String[]{innerBody, hint};
        }
        return new String[]{fullBody, hint};
    }

    public static String detectDialectHint(String body) {
        if (body == null) return "unknown";
        String s = body.toLowerCase();
        if (s.contains("language plpgsql")) return "plpgsql";
        if (s.contains("deterministic") || s.contains("reads sql data") ||
            s.contains("declare ") || s.contains(" call ") ||
            s.matches("(?s).*select\\s+.+\\s+into\\s+.+;.*") ||
            s.contains("end$$") || s.contains("delimiter $$")) {
            return "mysql";
        }
        if (s.contains("$$")) return "plpgsql";
        if ((s.contains(" as ") && s.contains("begin")) || s.contains(" exec ") || s.contains(" execute ") || s.contains(" go\n")) {
            return "tsql";
        }
        if (s.contains("begin") && !(s.contains(" as ") || s.contains(" exec ") || s.contains(" execute ") || s.contains(" go\n"))) {
            return "mysql";
        }
        return "unknown";
    }

    private static String extractRoutineBodyFromSource(String src, String routineName, boolean isFunction) {
        if (src == null || routineName == null) return null;
        String escapedName = Pattern.quote(routineName);
        Pattern headPat = Pattern.compile("(?is)\\b(?:create\\s+(?:or\\s+replace\\s+)?|alter\\s+)(" +
                (isFunction ? "function" : "procedure") + ")\\s+" + escapedName + "\\s*\\(");
        Matcher matcher = headPat.matcher(src);
        if (!matcher.find()) {
            String simpleName = routineName.contains(".") ? routineName.substring(routineName.lastIndexOf('.') + 1) : routineName;
            String escapedSimple = Pattern.quote(simpleName);
            headPat = Pattern.compile("(?is)\\b(?:create\\s+(?:or\\s+replace\\s+)?|alter\\s+)(" +
                    (isFunction ? "function" : "procedure") + ")\\s+(?:[a-z_][a-z0-9_]*\\.)?" + escapedSimple + "\\s*\\(");
            matcher = headPat.matcher(src);
            if (!matcher.find()) {
                return null;
            }
        }
        int from = matcher.end() - 1;
        int close = SqlRoutineTextUtils.findMatchingParen(src, from);
        if (close > from) {
            from = close + 1;
        }

        int to = -1;
        Pattern endDelimiter = Pattern.compile("(?i)\\bEND\\s*\\$\\$");
        Matcher endMatch = endDelimiter.matcher(src);
        if (endMatch.find(from)) {
            to = endMatch.end();
        }
        if (to < 0 || to <= from) {
            to = findNextCreateIndex(src, from);
        }
        if (to < 0 || to <= from) to = src.length();
        return src.substring(from, to).trim();
    }

    private static int findNextCreateIndex(String src, int from) {
        if (src == null) return -1;
        Matcher next = Pattern.compile("(?is)\\bcreate\\s+(table|view|index|function|procedure)\\b").matcher(src);
        if (next.find(from)) return next.start();
        return -1;
    }
}
