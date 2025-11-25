package org.dxworks.codeframe.analyzer.sql;

public final class RoutineSqlUtils {

    private RoutineSqlUtils() {
        // utility class
    }

    public static String stripQuotes(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') ||
                (first == '`' && last == '`') ||
                (first == '[' && last == ']')) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }
        return trimmed;
    }

    public static boolean isSqlTypeName(String name) {
        if (name == null) {
            return false;
        }
        String upper = name.toUpperCase();
        return upper.equals("DECIMAL") || upper.equals("NUMERIC") || upper.equals("INT") || upper.equals("INTEGER") ||
               upper.equals("SMALLINT") || upper.equals("BIGINT") || upper.equals("DOUBLE") || upper.equals("FLOAT") ||
               upper.equals("REAL") || upper.equals("DATE") || upper.equals("TIME") || upper.equals("TIMESTAMP") ||
               upper.equals("VARCHAR") || upper.equals("CHAR") || upper.equals("NVARCHAR") || upper.equals("TEXT") ||
               upper.equals("BOOLEAN") || upper.equals("BOOL");
    }

    public static String[] splitSchemaAndName(String combined) {
        if (combined == null) {
            return new String[]{null, null};
        }
        String value = combined.trim();
        int dot = value.lastIndexOf('.');
        if (dot >= 0) {
            return new String[]{stripQuotes(value.substring(0, dot)), stripQuotes(value.substring(dot + 1))};
        }
        return new String[]{null, stripQuotes(value)};
    }

    /**
     * Returns a safe non-null list.
     */
    public static <T> java.util.List<T> safeList(java.util.List<T> list) {
        return list == null ? java.util.List.of() : list;
    }

    /**
     * Qualifies a name with schema if present.
     */
    public static String qualifyName(String schema, String name) {
        return (schema != null && !schema.isEmpty()) ? schema + "." + name : name;
    }

    /**
     * Normalizes a dotted identifier chain (e.g., "schema.table" or "pkg.func").
     * Splits by dots, strips quotes from each part, and rejoins.
     * Returns null if the result is empty.
     */
    public static String normalizeIdentifierChain(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        String[] parts = s.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null) continue;
            String cleaned = stripQuotes(part.trim());
            if (cleaned == null || cleaned.isEmpty()) continue;
            if (sb.length() > 0) sb.append('.');
            sb.append(cleaned);
        }
        String out = sb.toString();
        return out.isEmpty() ? null : out;
    }

    /**
     * Normalizes SQL type formatting by removing extra spaces around parentheses and commas.
     * E.g., "DECIMAL ( 12 , 2 )" becomes "DECIMAL(12,2)"
     */
    public static String normalizeTypeFormat(String type) {
        if (type == null) return null;
        String s = type.trim();
        if (s.isEmpty()) return null;
        // Remove spaces before '('
        s = s.replaceAll("\\s+\\(", "(");
        // Remove spaces after '('
        s = s.replaceAll("\\(\\s+", "(");
        // Remove spaces before ')'
        s = s.replaceAll("\\s+\\)", ")");
        // Remove spaces before ','
        s = s.replaceAll("\\s+,", ",");
        // Remove spaces after ','
        s = s.replaceAll(",\\s+", ",");
        return s;
    }

    /**
     * Normalizes a function name chain, also stripping '@' suffixes (for db links).
     */
    public static String normalizeFunctionNameChain(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        String[] parts = s.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null) continue;
            String p = part.trim();
            int at = p.indexOf('@');
            if (at >= 0) {
                p = p.substring(0, at);
            }
            String cleaned = stripQuotes(p);
            if (cleaned == null || cleaned.isEmpty()) continue;
            if (sb.length() > 0) sb.append('.');
            sb.append(cleaned);
        }
        String out = sb.toString();
        return out.isEmpty() ? null : out;
    }
}
