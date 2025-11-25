package org.dxworks.codeframe.analyzer.sql;

import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility for extracting table references from SQL statements.
 */
public final class TableReferenceExtractor {

    private TableReferenceExtractor() {
        // utility class
    }

    /**
     * Extracts unique table references from a statement.
     * 
     * @param statement the SQL statement to analyze
     * @param sink collection to add table names to
     */
    public static void extractTableReferences(Statement statement, Collection<String> sink) {
        if (statement == null) return;
        
        try {
            TablesNamesFinder finder = new TablesNamesFinder();
            List<String> tables = finder.getTableList(statement);
            if (tables != null) {
                // Use a set to deduplicate
                Set<String> seen = new HashSet<>();
                for (String table : tables) {
                    if (table != null && !table.isEmpty() && seen.add(table)) {
                        sink.add(table);
                    }
                }
            }
        } catch (UnsupportedOperationException e) {
            // Some statements (e.g., Execute) are not supported by TablesNamesFinder
            // Silently ignore
        }
    }

    /**
     * Extracts unique table references from a statement without deduplication.
     * 
     * @param statement the SQL statement to analyze
     * @return list of table names (may contain duplicates)
     */
    public static List<String> extractTableReferences(Statement statement) {
        if (statement == null) return List.of();
        
        try {
            TablesNamesFinder finder = new TablesNamesFinder();
            List<String> tables = finder.getTableList(statement);
            return tables != null ? tables : List.of();
        } catch (UnsupportedOperationException e) {
            return List.of();
        }
    }
}
