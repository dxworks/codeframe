package org.dxworks.codeframe.analyzer.sql;

/**
 * Preprocesses SQL source code to make it more compatible with JSqlParser.
 * Handles dialect-specific constructs that JSqlParser doesn't understand natively.
 */
public final class SqlPreprocessor {

    private SqlPreprocessor() {
        // utility class
    }

    /**
     * Preprocess SQL to make it more compatible with JSqlParser.
     * - Removes T-SQL GO batch separators
     * - Removes MySQL DELIMITER directives
     * - Truncates MySQL routine bodies (they're analyzed separately)
     */
    public static String preprocess(String sql) {
        if (sql == null) return null;

        String dialect = DialectHeuristics.detectDialectFromSource(sql);
        boolean isMySql = "mysql".equalsIgnoreCase(dialect);

        String[] lines = sql.split("\r?\n");
        StringBuilder sb = new StringBuilder(sql.length());

        // MySQL routine body tracking
        boolean inMySqlRoutine = false;
        boolean skippingMySqlBody = false;

        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append(line).append(System.lineSeparator());
                continue;
            }

            String upper = trimmed.toUpperCase();

            // Remove T-SQL GO batch separators
            if (upper.equals("GO")) continue;

            // Remove MySQL DELIMITER directives
            if (upper.startsWith("DELIMITER")) continue;

            // MySQL-specific: Skip routine bodies (analyzed separately by SqlRoutineBodyAnalyzer)
            if (isMySql) {
                if (skippingMySqlBody) {
                    // Skip everything until we hit the routine terminator
                    if (upper.equals("END$$") || upper.equals("END $$")) {
                        skippingMySqlBody = false;
                        inMySqlRoutine = false;
                        sb.append("END;").append(System.lineSeparator());
                    }
                    continue;
                }

                // Track routine start
                if (upper.startsWith("CREATE FUNCTION") || upper.startsWith("CREATE PROCEDURE")) {
                    inMySqlRoutine = true;
                }

                // When we hit BEGIN, start skipping the body
                if (inMySqlRoutine && upper.startsWith("BEGIN")) {
                    ensurePreviousLineEndsWithSemicolon(sb);
                    skippingMySqlBody = true;
                    continue;
                }
                
                // Normalize standalone END$$ to END; (for non-routine contexts)
                if (upper.equals("END$$") || upper.equals("END $$")) {
                    sb.append("END;").append(System.lineSeparator());
                    continue;
                }
            }

            sb.append(line).append(System.lineSeparator());
        }

        return sb.toString();
    }

    /**
     * Ensures the last non-empty line in the StringBuilder ends with a semicolon.
     * Used to properly terminate MySQL routine signatures before skipping the body.
     */
    private static void ensurePreviousLineEndsWithSemicolon(StringBuilder sb) {
        int len = sb.length();
        if (len > 0) {
            String nl = System.lineSeparator();
            int lastNl = sb.lastIndexOf(nl);
            if (lastNl < 0) lastNl = len;
            int prevEnd = lastNl;
            int prevStart = sb.lastIndexOf(nl, prevEnd - 1);
            if (prevStart < 0) prevStart = 0; else prevStart += nl.length();
            int posBeforeNl = prevEnd - 1;
            if (posBeforeNl >= prevStart && sb.charAt(posBeforeNl) != ';') {
                sb.insert(prevEnd, ";");
            }
        }
    }
}
