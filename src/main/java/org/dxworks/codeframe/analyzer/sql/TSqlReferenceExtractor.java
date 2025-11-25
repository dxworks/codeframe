package org.dxworks.codeframe.analyzer.sql;

import org.dxworks.codeframe.analyzer.sql.generated.TSqlParserBaseVisitor;
import org.dxworks.codeframe.analyzer.sql.generated.TSqlParser;

/**
 * Visitor that extracts table references and procedure/function calls from T-SQL parse trees.
 */
public class TSqlReferenceExtractor extends TSqlParserBaseVisitor<Void> {

    private final BaseReferenceExtractor state = new BaseReferenceExtractor() {};

    public java.util.Set<String> getTableReferences() {
        return state.getTableReferences();
    }

    public java.util.Set<String> getProcedureCalls() {
        return state.getProcedureCalls();
    }

    public java.util.Set<String> getFunctionCalls() {
        return state.getFunctionCalls();
    }

    @Override
    public Void visitTable_source_item(TSqlParser.Table_source_itemContext ctx) {
        // FROM/JOIN sources: use full_table_name; other alternatives (functions, derived, etc.) are ignored for refs
        TSqlParser.Full_table_nameContext ft = ctx.full_table_name();
        if (ft != null) {
            state.addTableReference(extractFullTableName(ft));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitSCALAR_FUNCTION(TSqlParser.SCALAR_FUNCTIONContext ctx) {
        // Capture scalar function calls in expressions: scalar_function_name '(' ... ')'
        if (ctx.scalar_function_name() != null) {
            TSqlParser.Scalar_function_nameContext fn = ctx.scalar_function_name();
            // Only record user/system defined Names via func_proc_name_server_database_schema; skip bare RIGHT/LEFT tokens
            TSqlParser.Func_proc_name_server_database_schemaContext qn = fn.func_proc_name_server_database_schema();
            if (qn != null) {
                state.addFunctionCall(extractProcFuncName(qn));
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitQuery_specification(TSqlParser.Query_specificationContext ctx) {
        // SELECT ... INTO table_name ... FROM table_sources ...
        if (ctx.into != null) {
            state.addTableReference(extractTableName(ctx.into));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitUpdate_statement(TSqlParser.Update_statementContext ctx) {
        // UPDATE target (ddl_object or rowset function)
        if (ctx.ddl_object() != null) {
            state.addTableReference(extractDdlObject(ctx.ddl_object()));
        }
        // FROM ... table_sources
        return visitChildren(ctx);
    }

    @Override
    public Void visitDelete_statement(TSqlParser.Delete_statementContext ctx) {
        // FROM clause may include table sources
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsert_statement(TSqlParser.Insert_statementContext ctx) {
        // INSERT INTO target (ddl_object or rowset)
        if (ctx.ddl_object() != null) {
            state.addTableReference(extractDdlObject(ctx.ddl_object()));
        }
        return visitChildren(ctx);
    }

    // Note: We do NOT override visitFull_table_name as a generic catch-all because it would
    // capture aliases. Instead, we rely on specific context methods (visitTable_source_item,
    // visitUpdate_statement, etc.) which only capture actual table references from SQL clauses.

    @Override
    public Void visitExecute_statement(TSqlParser.Execute_statementContext ctx) {
        // Extract procedure calls from EXEC/EXECUTE statements
        TSqlParser.Execute_bodyContext body = ctx.execute_body();
        if (body != null) {
            TSqlParser.Func_proc_name_server_database_schemaContext procName = body.func_proc_name_server_database_schema();
            if (procName != null) {
                state.addProcedureCall(extractProcFuncName(procName));
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitExecute_body_batch(TSqlParser.Execute_body_batchContext ctx) {
        // Extract procedure calls from batch-level EXEC statements
        TSqlParser.Func_proc_name_server_database_schemaContext procName = ctx.func_proc_name_server_database_schema();
        if (procName != null) {
            state.addProcedureCall(extractProcFuncName(procName));
        }
        return visitChildren(ctx);
    }

    /**
     * Extract fully qualified table name from the parse tree context.
     */
    private String extractFullTableName(TSqlParser.Full_table_nameContext ctx) {
        if (ctx == null) return null;

        StringBuilder sb = new StringBuilder();
        
        // Handle: [server].[database].[schema].[table]
        if (ctx.server != null) {
            sb.append(getText(ctx.server)).append(".");
            if (ctx.database != null) {
                sb.append(getText(ctx.database)).append(".");
            }
        }
        
        // Handle: [schema].[table]
        if (ctx.schema != null) {
            sb.append(getText(ctx.schema)).append(".");
        }
        
        // Table name
        if (ctx.table != null) {
            sb.append(getText(ctx.table));
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    private String extractTableName(TSqlParser.Table_nameContext ctx) {
        if (ctx == null) return null;
        StringBuilder sb = new StringBuilder();
        // database.schema.table | schema.table | table
        if (ctx.database != null) {
            sb.append(getText(ctx.database)).append(".");
        }
        if (ctx.schema != null) {
            sb.append(getText(ctx.schema)).append(".");
        }
        if (ctx.table != null) {
            sb.append(getText(ctx.table));
        }
        String out = sb.toString();
        if (out.endsWith(".")) out = out.substring(0, out.length() - 1);
        return out.isEmpty() ? null : out;
    }

    private String extractDdlObject(TSqlParser.Ddl_objectContext ctx) {
        if (ctx == null) return null;
        if (ctx.full_table_name() != null) {
            return extractFullTableName(ctx.full_table_name());
        }
        return null; // ignore LOCAL_ID targets
    }

    // Removed addRef - now using state.addTableReference()

    /**
     * Extract procedure/function name from the parse tree context.
     */
    private String extractProcFuncName(TSqlParser.Func_proc_name_server_database_schemaContext ctx) {
        if (ctx == null) return null;

        StringBuilder sb = new StringBuilder();
        
        // Try to get from nested contexts
        TSqlParser.Func_proc_name_database_schemaContext dbSchema = ctx.func_proc_name_database_schema();
        if (dbSchema != null) {
            TSqlParser.Func_proc_name_schemaContext schema = dbSchema.func_proc_name_schema();
            if (schema != null) {
                if (schema.schema != null) {
                    sb.append(getText(schema.schema)).append(".");
                }
                if (schema.procedure != null) {
                    sb.append(getText(schema.procedure));
                }
            } else if (dbSchema.database != null || dbSchema.schema != null || dbSchema.procedure != null) {
                // Handle database.schema.procedure format
                if (dbSchema.database != null) {
                    sb.append(getText(dbSchema.database)).append(".");
                }
                if (dbSchema.schema != null) {
                    sb.append(getText(dbSchema.schema)).append(".");
                }
                if (dbSchema.procedure != null) {
                    sb.append(getText(dbSchema.procedure));
                }
            }
        } else if (ctx.server != null || ctx.database != null || ctx.schema != null || ctx.procedure != null) {
            // Handle server.database.schema.procedure format
            if (ctx.server != null) {
                sb.append(getText(ctx.server)).append(".");
            }
            if (ctx.database != null) {
                sb.append(getText(ctx.database)).append(".");
            }
            if (ctx.schema != null) {
                sb.append(getText(ctx.schema)).append(".");
            }
            if (ctx.procedure != null) {
                sb.append(getText(ctx.procedure));
            }
        }

        String result = sb.toString();
        // Clean up trailing dots
        while (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }
        
        return result.isEmpty() ? null : result;
    }

    private String getText(TSqlParser.Id_Context ctx) {
        if (ctx == null) return null;
        return RoutineSqlUtils.stripQuotes(ctx.getText());
    }
}
