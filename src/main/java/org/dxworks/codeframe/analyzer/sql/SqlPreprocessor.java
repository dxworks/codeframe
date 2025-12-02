package org.dxworks.codeframe.analyzer.sql;

import java.util.Locale;

/**
 * Preprocesses SQL source code to make it more compatible with JSqlParser.
 * Handles dialect-specific constructs that JSqlParser doesn't understand natively:
 * - Removes T-SQL GO batch separators
 * - Removes MySQL DELIMITER directives
 * - Skips MySQL routine/trigger bodies (analyzed separately by SqlRoutineBodyAnalyzer)
 */
public final class SqlPreprocessor {

    private static final String NL = System.lineSeparator();

    private SqlPreprocessor() {}

    public static String preprocess(String sql) {
        if (sql == null) return null;

        boolean isMySql = "mysql".equalsIgnoreCase(DialectHeuristics.detectDialectFromSource(sql));
        String[] lines = sql.split("\r?\n");
        StringBuilder sb = new StringBuilder(sql.length());

        MySqlState state = new MySqlState();

        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            String upper = trimmed.toUpperCase(Locale.ROOT);

            if (trimmed.isEmpty()) {
                sb.append(line).append(NL);
                continue;
            }

            // Remove T-SQL GO batch separators
            if ("GO".equals(upper)) continue;

            // Remove MySQL DELIMITER directives
            if (upper.startsWith("DELIMITER")) continue;

            // MySQL-specific processing
            if (isMySql && processMySqlLine(upper, sb, state)) continue;

            sb.append(line).append(NL);
        }

        return sb.toString();
    }

    /**
     * Processes MySQL-specific line handling.
     * @return true if line was consumed (should skip normal append), false otherwise
     */
    private static boolean processMySqlLine(String upper, StringBuilder sb, MySqlState state) {
        // When skipping routine body, wait for END$$
        if (state.skippingBody) {
            if (isEndDollar(upper)) {
                state.reset();
                sb.append("END;").append(NL);
            }
            return true;
        }

        // Track routine/trigger start
        if (upper.startsWith("CREATE FUNCTION") || upper.startsWith("CREATE PROCEDURE") 
                || upper.startsWith("CREATE TRIGGER")) {
            state.inRoutine = true;
        }

        // When we hit BEGIN inside a routine, start skipping the body
        if (state.inRoutine && upper.startsWith("BEGIN")) {
            appendSemicolonIfNeeded(sb);
            state.skippingBody = true;
            return true;
        }

        // Normalize standalone END$$ to END;
        if (isEndDollar(upper)) {
            sb.append("END;").append(NL);
            return true;
        }

        return false;
    }

    private static boolean isEndDollar(String upper) {
        return "END$$".equals(upper) || "END $$".equals(upper);
    }

    /**
     * Appends a semicolon before the last newline if the previous line doesn't end with one.
     */
    private static void appendSemicolonIfNeeded(StringBuilder sb) {
        int len = sb.length();
        if (len == 0) return;

        // Find the last non-newline character
        int pos = len - 1;
        while (pos >= 0 && (sb.charAt(pos) == '\n' || sb.charAt(pos) == '\r')) {
            pos--;
        }
        if (pos >= 0 && sb.charAt(pos) != ';') {
            sb.insert(pos + 1, ';');
        }
    }

    /** Tracks MySQL routine body parsing state */
    private static class MySqlState {
        boolean inRoutine = false;
        boolean skippingBody = false;

        void reset() {
            inRoutine = false;
            skippingBody = false;
        }
    }
}
