package org.dxworks.codeframe.analyzer.sql;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex;
import net.sf.jsqlparser.statement.create.table.Index;

import java.util.List;

/**
 * Utility for building constraint string representations.
 */
public final class ConstraintBuilder {

    private ConstraintBuilder() {
        // utility class
    }

    /**
     * Builds a foreign key constraint string from a ForeignKeyIndex.
     */
    public static String buildForeignKeyConstraint(ForeignKeyIndex fkIdx) {
        if (fkIdx == null) return null;

        StringBuilder sb = new StringBuilder();
        String name = fkIdx.getName();
        if (name != null && !name.isEmpty()) {
            sb.append("CONSTRAINT ").append(name).append(" ");
        }
        sb.append("FOREIGN KEY (").append(String.join(", ", RoutineSqlUtils.safeList(fkIdx.getColumnsNames()))).append(")");
        
        Table ref = fkIdx.getTable();
        if (ref != null) {
            sb.append(" REFERENCES ").append(RoutineSqlUtils.qualifyName(ref.getSchemaName(), ref.getName()));
        }
        
        List<String> refCols = RoutineSqlUtils.safeList(fkIdx.getReferencedColumnNames());
        if (!refCols.isEmpty()) {
            sb.append("(").append(String.join(", ", refCols)).append(")");
        }
        
        // Try to extract ON DELETE/UPDATE actions via reflection (for compatibility)
        try {
            Object onDelete = ForeignKeyIndex.class.getMethod("getOnDelete").invoke(fkIdx);
            if (onDelete != null) {
                sb.append(" ON DELETE ").append(onDelete.toString());
            }
        } catch (Exception ignore) {
            // Method might not exist in older versions
        }
        
        try {
            Object onUpdate = ForeignKeyIndex.class.getMethod("getOnUpdate").invoke(fkIdx);
            if (onUpdate != null) {
                sb.append(" ON UPDATE ").append(onUpdate.toString());
            }
        } catch (Exception ignore) {
            // Method might not exist in older versions
        }
        
        return sb.toString();
    }

    /**
     * Builds a primary key constraint string from an Index.
     */
    public static String buildPrimaryKeyConstraint(Index idx) {
        if (idx == null) return null;
        StringBuilder sb = new StringBuilder();
        String name = idx.getName();
        if (name != null && !name.isEmpty()) {
            sb.append("CONSTRAINT ").append(name).append(" ");
        }
        sb.append("PRIMARY KEY (").append(String.join(", ", RoutineSqlUtils.safeList(idx.getColumnsNames()))).append(")");
        return sb.toString();
    }

    /**
     * Checks if an index represents a primary key constraint.
     */
    public static boolean isPrimaryKey(Index idx) {
        if (idx == null) return false;
        String type = idx.getType();
        return type != null && type.toUpperCase().contains("PRIMARY KEY");
    }
}
