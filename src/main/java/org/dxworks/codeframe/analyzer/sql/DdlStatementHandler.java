package org.dxworks.codeframe.analyzer.sql;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex;
import net.sf.jsqlparser.statement.create.table.Index;
import net.sf.jsqlparser.statement.create.table.Index.ColumnParams;
import net.sf.jsqlparser.statement.create.view.AlterView;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.drop.Drop;
import org.dxworks.codeframe.model.sql.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles DDL statements parsed by JSqlParser:
 * CREATE TABLE, CREATE VIEW, CREATE INDEX, ALTER TABLE, ALTER VIEW, DROP.
 */
final class DdlStatementHandler {

    DdlStatementHandler() {
    }

    void handleCreateTable(CreateTable ct, SQLFileAnalysis out) {
        CreateTableOperation op = new CreateTableOperation();
        Table t = ct.getTable();
        if (t != null) {
            op.tableName = t.getName();
            op.schema = t.getSchemaName();
        }
        // IF NOT EXISTS
        op.ifNotExists = ct.isIfNotExists();

        List<ColumnDefinition> cols = ct.getColumnDefinitions();
        if (cols != null) {
            for (ColumnDefinition cd : cols) {
                org.dxworks.codeframe.model.sql.ColumnDefinition m = new org.dxworks.codeframe.model.sql.ColumnDefinition();
                m.name = cd.getColumnName();
                m.type = toTypeString(cd.getColDataType());
                // constraints in columnSpecStrings
                List<String> specs = cd.getColumnSpecs();
                if (specs != null) {
                    List<String> norm = normalize(specs);
                    if (containsSequence(norm, "not", "null")) {
                        m.nullable = false;
                        m.constraints.add("NOT NULL");
                    }
                    if (norm.contains("unique")) {
                        m.constraints.add("UNIQUE");
                    }
                    if (containsSequence(norm, "primary", "key")) {
                        m.constraints.add("PRIMARY KEY");
                        if (!op.primaryKeys.contains(m.name)) op.primaryKeys.add(m.name);
                    }
                }
                op.columns.add(m);
            }
        }

        // Table-level indexes/constraints
        List<Index> indexes = ct.getIndexes();
        if (indexes != null) {
            for (Index idx : indexes) {
                String type = idx.getType();
                if (type != null && type.equalsIgnoreCase("primary key")) {
                    for (String col : RoutineSqlUtils.safeList(idx.getColumnsNames())) {
                        if (!op.primaryKeys.contains(col)) op.primaryKeys.add(col);
                    }
                }
                if (idx instanceof ForeignKeyIndex) {
                    ForeignKeyIndex fkIdx = (ForeignKeyIndex) idx;
                    ForeignKeyDefinition fk = new ForeignKeyDefinition();
                    fk.name = fkIdx.getName();
                    fk.columns.addAll(RoutineSqlUtils.safeList(fkIdx.getColumnsNames()));
                    Table ref = fkIdx.getTable();
                    if (ref != null) {
                        fk.referencedTable = RoutineSqlUtils.qualifyName(ref.getSchemaName(), ref.getName());
                    }
                    fk.referencedColumns.addAll(RoutineSqlUtils.safeList(fkIdx.getReferencedColumnNames()));
                    // onDelete/onUpdate not extracted; leave null intentionally
                    op.foreignKeys.add(fk);
                }
            }
        }

        out.createTables.add(op);
    }

    void handleCreateView(CreateView cv, SQLFileAnalysis out) {
        boolean isAlter = cv.isOrReplace();
        
        if (isAlter) {
            AlterViewOperation op = new AlterViewOperation();
            extractViewData(cv.getView(), cv.getSelect(), 
                (name) -> op.viewName = name,
                (schema) -> op.schema = schema,
                op.references.relations);
            out.alterViews.add(op);
        } else {
            CreateViewOperation op = new CreateViewOperation();
            extractViewData(cv.getView(), cv.getSelect(),
                (name) -> op.viewName = name,
                (schema) -> op.schema = schema,
                op.references.relations);
            out.createViews.add(op);
        }
    }

    void handleAlterViewAst(AlterView av, SQLFileAnalysis out) {
        AlterViewOperation op = new AlterViewOperation();
        extractViewData(av.getView(), av.getSelect(),
            (name) -> op.viewName = name,
            (schema) -> op.schema = schema,
            op.references.relations);
        out.alterViews.add(op);
    }

    void handleCreateIndex(CreateIndex ci, SQLFileAnalysis out) {
        CreateIndexOperation op = new CreateIndexOperation();
        if (ci.getIndex() != null) {
            op.indexName = ci.getIndex().getName();

            // Extract columns using the inner Index API (compatible with older versions)
            List<ColumnParams> params = ci.getIndex().getColumns();
            if (params != null) {
                for (ColumnParams cp : params) {
                    String name = cp.getColumnName();
                    if (name != null && !name.isEmpty()) op.columns.add(name);
                }
            }

            // Uniqueness might be encoded in the type string
            String t = ci.getIndex().getType();
            if (t != null && t.toUpperCase().contains("UNIQUE")) {
                op.unique = true;
            }
        }
        if (ci.getTable() != null) {
            Table t = ci.getTable();
            op.tableName = t.getName();
            op.schema = t.getSchemaName();
        }
        out.createIndexes.add(op);
    }

    void handleAlter(Alter alter, SQLFileAnalysis out, String sourceCode) {
        // Check if this is ALTER VIEW by examining the source text
        // JSqlParser 5.3 uses Alter for both ALTER TABLE and ALTER VIEW
        if (isAlterView(alter, sourceCode)) {
            handleAlterView(alter, out);
            return;
        }
        handleAlterTable(alter, out);
    }

    void handleDrop(Drop drop, SQLFileAnalysis out) {
        DropOperation op = new DropOperation();
        // Type
        String t = drop.getType();
        op.objectType = t != null ? t.toUpperCase() : null;
        // IF EXISTS
        op.ifExists = drop.isIfExists();
        // Name and schema (JSqlParser models names as Table-like in most cases)
        if (drop.getName() instanceof Table) {
            Table tbl = (Table) drop.getName();
            op.objectName = tbl.getName();
            op.schema = tbl.getSchemaName();
        } else if (drop.getName() != null) {
            // Fallback: use toString for name when not table-like
            op.objectName = drop.getName().toString();
        }
        if (op.objectName != null || op.objectType != null) out.dropOperations.add(op);
    }

    // ---- View Extraction Helpers ----

    private void extractViewData(Table view, Object select, 
                                 java.util.function.Consumer<String> nameConsumer,
                                 java.util.function.Consumer<String> schemaConsumer,
                                 java.util.Collection<String> references) {
        if (view != null) {
            nameConsumer.accept(view.getName());
            schemaConsumer.accept(view.getSchemaName());
        }
        
        if (select != null) {
            TableReferenceExtractor.extractTableReferences((Statement) select, references);
        }
    }

    // ---- ALTER helpers ----

    private boolean isAlterView(Alter alter, String sourceCode) {
        // If there's no table but the statement is ALTER, it might be ALTER VIEW
        // Check the source text around the statement
        if (alter.getTable() == null) {
            return false; // Could be ALTER VIEW, but we need more context
        }
        // For now, check if the toString contains VIEW keyword
        String alterStr = alter.toString().toUpperCase();
        boolean isView = alterStr.startsWith("ALTER VIEW") || alterStr.contains("ALTER VIEW");
        return isView;
    }

    private void handleAlterView(Alter alter, SQLFileAnalysis out) {
        // Legacy path: JSqlParser once modeled ALTER VIEW as Alter; keep minimal support
        AlterViewOperation op = new AlterViewOperation();
        extractViewData(alter.getTable(), null,
            (name) -> op.viewName = name,
            (schema) -> op.schema = schema,
            op.references.relations);
        out.alterViews.add(op);
    }

    private void handleAlterTable(Alter alter, SQLFileAnalysis out) {
        AlterTableOperation op = new AlterTableOperation();
        if (alter.getTable() != null) {
            Table t = alter.getTable();
            op.tableName = t.getName();
            op.schema = t.getSchemaName();
        }

        List<AlterExpression> exprs = alter.getAlterExpressions();
        if (exprs != null) {
            for (AlterExpression ae : exprs) {
                processAlterExpression(ae, op);
            }
        }

        if (op.operationType != null || !op.addedColumns.isEmpty() || !op.droppedColumns.isEmpty() || !op.addedConstraints.isEmpty() || !op.droppedConstraints.isEmpty()) {
            out.alterTables.add(op);
        }
    }

    // ---- ALTER TABLE Expression Processing ----

    private void processAlterExpression(AlterExpression ae, AlterTableOperation op) {
        Object opObj = ae.getOperation();
        String opStr = opObj == null ? "" : opObj.toString();

        if (opStr.equals("ADD") || opStr.equals("ADD_COLUMN") || opStr.equals("ADD_COLUMN_IF_NOT_EXISTS")) {
            handleAddOperation(ae, op);
        } else if (opStr.equals("DROP") || opStr.equals("DROP_COLUMN") || opStr.equals("DROP_COLUMN_IF_EXISTS")) {
            handleDropColumnOperation(ae, op);
        } else if (opStr.equals("ADD_CONSTRAINT")) {
            handleAddConstraintOperation(ae, op);
        } else if (opStr.equals("DROP_CONSTRAINT")) {
            handleDropConstraintOperation(ae, op);
        }
        // Ignore other operations
    }

    private void handleAddOperation(AlterExpression ae, AlterTableOperation op) {
        // Check if this ADD is a constraint
        if (tryAddConstraint(ae, op)) {
            return;
        }

        // Otherwise, handle ADD COLUMN
        List<AlterExpression.ColumnDataType> cds = ae.getColDataTypeList();
        if (cds != null) {
            for (AlterExpression.ColumnDataType cdt : cds) {
                org.dxworks.codeframe.model.sql.ColumnDefinition cd = 
                    new org.dxworks.codeframe.model.sql.ColumnDefinition();
                cd.name = cdt.getColumnName();
                cd.type = toTypeString(cdt.getColDataType());
                op.addedColumns.add(cd);
            }
            if (op.operationType == null) {
                op.operationType = "ADD_COLUMN";
            }
        }
    }

    private boolean tryAddConstraint(AlterExpression ae, AlterTableOperation op) {
        try {
            Object idxObj = ae.getIndex();
            if (idxObj instanceof ForeignKeyIndex) {
                String constraint = ConstraintBuilder.buildForeignKeyConstraint((ForeignKeyIndex) idxObj);
                if (constraint != null) {
                    op.addedConstraints.add(constraint);
                    if (op.operationType == null) {
                        op.operationType = "ADD_CONSTRAINT";
                    }
                    return true;
                }
            } else if (idxObj instanceof Index) {
                Index idx = (Index) idxObj;
                if (ConstraintBuilder.isPrimaryKey(idx)) {
                    String constraint = ConstraintBuilder.buildPrimaryKeyConstraint(idx);
                    if (constraint != null) {
                        op.addedConstraints.add(constraint);
                        if (op.operationType == null) {
                            op.operationType = "ADD_CONSTRAINT";
                        }
                        return true;
                    }
                }
            }
        } catch (Exception ignore) {
            // Silently ignore parsing errors
        }
        return false;
    }

    private void handleAddConstraintOperation(AlterExpression ae, AlterTableOperation op) {
        tryAddConstraint(ae, op);
    }

    private void handleDropColumnOperation(AlterExpression ae, AlterTableOperation op) {
        String colName = extractColumnName(ae);
        if (colName != null && !colName.isEmpty()) {
            op.droppedColumns.add(RoutineSqlUtils.stripQuotes(colName));
            if (op.operationType == null) {
                op.operationType = "DROP_COLUMN";
            }
        }
    }

    private void handleDropConstraintOperation(AlterExpression ae, AlterTableOperation op) {
        String cname = ae.getConstraintName();
        if (cname != null && !cname.isEmpty()) {
            op.droppedConstraints.add(RoutineSqlUtils.stripQuotes(cname));
            if (op.operationType == null) {
                op.operationType = "DROP_CONSTRAINT";
            }
        }
    }

    private String extractColumnName(AlterExpression ae) {
        try {
            String colName = ae.getColumnName();
            if (colName != null) return colName;
        } catch (Exception ignored) {
            // Method might not exist or throw
        }
        
        try {
            return ae.getColOldName();
        } catch (Exception ignored) {
            return null;
        }
    }

    // ---- Type/constraint helpers ----

    static String toTypeString(ColDataType dt) {
        if (dt == null) return null;
        String base = dt.getDataType();
        List<String> args = dt.getArgumentsStringList();
        String result;
        if (args != null && !args.isEmpty()) {
            result = base + "(" + String.join(",", args) + ")";
        } else {
            result = base;
        }
        // Normalize to remove any extra spaces from JSqlParser output
        return RoutineSqlUtils.normalizeTypeFormat(result);
    }

    private static List<String> normalize(List<String> specs) {
        List<String> out = new ArrayList<>();
        for (String s : specs) out.add(s.toLowerCase());
        return out;
    }

    private static boolean containsSequence(List<String> items, String a, String b) {
        for (int i = 0; i < items.size() - 1; i++) {
            if (items.get(i).equals(a) && items.get(i + 1).equals(b)) return true;
        }
        return false;
    }
}
