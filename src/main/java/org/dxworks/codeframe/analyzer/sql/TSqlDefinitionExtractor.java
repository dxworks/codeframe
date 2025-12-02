package org.dxworks.codeframe.analyzer.sql;

import org.dxworks.codeframe.analyzer.sql.generated.TSqlParserBaseVisitor;
import org.dxworks.codeframe.analyzer.sql.generated.TSqlParser;
import org.dxworks.codeframe.model.sql.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts CREATE PROCEDURE, CREATE FUNCTION, and CREATE TRIGGER definitions from T-SQL parse trees.
 */
public class TSqlDefinitionExtractor extends TSqlParserBaseVisitor<Void> {

    private final String sourceCode;
    private final org.antlr.v4.runtime.CommonTokenStream tokens;
    private final List<CreateProcedureOperation> procedures = new ArrayList<>();
    private final List<CreateFunctionOperation> functions = new ArrayList<>();
    private final List<CreateTriggerOperation> triggers = new ArrayList<>();
    private final TSqlRoutineBodyAnalyzer bodyAnalyzer = new TSqlRoutineBodyAnalyzer();

    public TSqlDefinitionExtractor(String sourceCode, org.antlr.v4.runtime.CommonTokenStream tokens) {
        this.sourceCode = sourceCode;
        this.tokens = tokens;
    }

    public List<CreateProcedureOperation> getProcedures() {
        return procedures;
    }

    public List<CreateFunctionOperation> getFunctions() {
        return functions;
    }

    public List<CreateTriggerOperation> getTriggers() {
        return triggers;
    }

    @Override
    public Void visitCreate_or_alter_procedure(TSqlParser.Create_or_alter_procedureContext ctx) {
        CreateProcedureOperation op = new CreateProcedureOperation();
        op.orReplace = true; // CREATE OR ALTER semantics

        // Extract procedure name
        if (ctx.procName != null) {
            String[] parts = extractSchemaAndName(ctx.procName);
            op.schema = parts[0];
            op.procedureName = parts[1];
        }

        // Extract parameters
        List<TSqlParser.Procedure_paramContext> params = ctx.procedure_param();
        if (params != null) {
            for (TSqlParser.Procedure_paramContext param : params) {
                ParameterDefinition pd = extractParameter(param);
                if (pd != null) {
                    op.parameters.add(pd);
                }
            }
        }

        // Extract body and analyze for references/calls
        analyzeBody(extractProcedureBody(ctx), op);
        procedures.add(op);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreate_or_alter_function(TSqlParser.Create_or_alter_functionContext ctx) {
        CreateFunctionOperation op = new CreateFunctionOperation();
        op.orReplace = true; // CREATE OR ALTER semantics

        // Extract function name
        if (ctx.funcName != null) {
            String[] parts = extractSchemaAndName(ctx.funcName);
            op.schema = parts[0];
            op.functionName = parts[1];
        }

        // Extract parameters
        List<TSqlParser.Procedure_paramContext> params = ctx.procedure_param();
        if (params != null) {
            for (TSqlParser.Procedure_paramContext param : params) {
                ParameterDefinition pd = extractParameter(param);
                if (pd != null) {
                    op.parameters.add(pd);
                }
            }
        }

        // Extract return type
        if (ctx.func_body_returns_select() != null) {
            op.returnType = "TABLE";
        } else if (ctx.func_body_returns_table() != null) {
            op.returnType = "TABLE";
        } else if (ctx.func_body_returns_scalar() != null) {
            TSqlParser.Func_body_returns_scalarContext returns = ctx.func_body_returns_scalar();
            if (returns.data_type() != null) {
                op.returnType = returns.data_type().getText();
            }
        }

        // Extract body and analyze for references/calls
        analyzeBody(extractFunctionBody(ctx), op);
        functions.add(op);
        return visitChildren(ctx);
    }

    private String[] extractSchemaAndName(TSqlParser.Func_proc_name_schemaContext ctx) {
        if (ctx == null) return new String[]{null, null};
        // Grammar-driven: use labeled children only
        String schema = ctx.schema != null ? RoutineSqlUtils.stripQuotes(ctx.schema.getText()) : null;
        String name = ctx.procedure != null ? RoutineSqlUtils.stripQuotes(ctx.procedure.getText()) : null;
        if (name != null) return new String[]{schema, name};
        // Fallback only if labels are absent
        return RoutineSqlUtils.splitSchemaAndName(ctx.getText());
    }

    // Removed splitSchemaAndNameClean - using RoutineSqlUtils.splitSchemaAndName instead

    private String rebuildFromSource(org.antlr.v4.runtime.Token start, org.antlr.v4.runtime.Token stop) {
        if (start == null || stop == null) return null;
        int s = start.getStartIndex();
        int e = stop.getStopIndex();
        if (s < 0 || e < s || e >= sourceCode.length()) return null;
        return sourceCode.substring(s, e + 1);
    }

    private ParameterDefinition extractParameter(TSqlParser.Procedure_paramContext ctx) {
        if (ctx == null) return null;
        
        ParameterDefinition pd = new ParameterDefinition();
        
        if (ctx.LOCAL_ID() != null) {
            pd.name = ctx.LOCAL_ID().getText();
        }
        
        if (ctx.data_type() != null) {
            pd.type = ctx.data_type().getText();
        }
        
        // Direction (IN/OUT/INOUT) - T-SQL uses OUTPUT keyword
        if (ctx.OUTPUT() != null) {
            pd.direction = "OUT";
        } else {
            pd.direction = "IN";
        }
        
        return pd;
    }

    private void analyzeBody(String body, CreateProcedureOperation op) {
        if (body == null || body.isEmpty()) return;
        RoutineBodyAnalyzer.Result result = bodyAnalyzer.analyze(body, "tsql");
        if (result == null) return;
        if (result.relations != null) op.references.relations.addAll(result.relations);
        if (result.functionCalls != null) op.calls.functions.addAll(result.functionCalls);
        if (result.procedureCalls != null) op.calls.procedures.addAll(result.procedureCalls);
    }

    private void analyzeBody(String body, CreateFunctionOperation op) {
        if (body == null || body.isEmpty()) return;
        RoutineBodyAnalyzer.Result result = bodyAnalyzer.analyze(body, "tsql");
        if (result == null) return;
        if (result.relations != null) op.references.relations.addAll(result.relations);
        if (result.functionCalls != null) op.calls.functions.addAll(result.functionCalls);
        if (result.procedureCalls != null) op.calls.procedures.addAll(result.procedureCalls);
    }

    private void analyzeBody(String body, CreateTriggerOperation op) {
        if (body == null || body.isEmpty()) return;
        RoutineBodyAnalyzer.Result result = bodyAnalyzer.analyze(body, "tsql");
        if (result == null) return;
        if (result.relations != null) op.references.relations.addAll(result.relations);
        if (result.functionCalls != null) op.calls.functions.addAll(result.functionCalls);
        if (result.procedureCalls != null) op.calls.procedures.addAll(result.procedureCalls);
    }

    private String extractProcedureBody(TSqlParser.Create_or_alter_procedureContext ctx) {
        // Token-based slice: prefer BEGIN...END block; fallback to text after AS
        String tokenSliced = sliceProcedureBodyByTokens(ctx);
        if (tokenSliced != null && !tokenSliced.isEmpty()) {
            return tokenSliced;
        }
        // Preferred: get from first sql_clauses node
        if (ctx.sql_clauses() != null && !ctx.sql_clauses().isEmpty()) {
            int start = ctx.sql_clauses(0).getStart().getStartIndex();
            int stop = ctx.getStop().getStopIndex();
            if (start >= 0 && stop >= start && stop < sourceCode.length()) {
                return sourceCode.substring(start, stop + 1);
            }
        }
        // Fallback: slice from AS token to end (covers BEGIN...END blocks)
        if (ctx.AS() != null && ctx.AS().getSymbol() != null) {
            int start = ctx.AS().getSymbol().getStopIndex() + 1;
            int stop = ctx.getStop().getStopIndex();
            if (start >= 0 && stop >= start && stop < sourceCode.length()) {
                return sourceCode.substring(start, stop + 1);
            }
        }
        return null;
    }

    private String sliceProcedureBodyByTokens(TSqlParser.Create_or_alter_procedureContext ctx) {
        if (tokens == null || ctx.getStart() == null || ctx.getStop() == null) return null;
        int startTokIdx = ctx.getStart().getTokenIndex();
        if (startTokIdx < 0) return null;

        Integer afterAs = null; // char index after AS
        Integer firstBeginStart = null; // char index at first BEGIN
        Integer matchingEndStop = null; // char index at matching END for the first BEGIN
        Integer nextGoStart = null; // char index at next GO after AS
        int depth = 0;

        java.util.List<org.antlr.v4.runtime.Token> all = tokens.getTokens();
        if (all == null) return null;

        // Find the first token at or after the AS to begin scanning body
        int i0 = -1;
        for (org.antlr.v4.runtime.Token t : all) {
            int ti = t.getTokenIndex();
            if (ti < startTokIdx) continue;
            String tt = t.getText();
            if (tt == null) continue;
            String u = tt.toUpperCase();
            if (afterAs == null && "AS".equals(u)) {
                afterAs = t.getStopIndex() + 1;
                i0 = ti + 1; // start scanning after AS
                break;
            }
        }

        if (afterAs == null) {
            return null;
        }

        if (i0 < 0) i0 = startTokIdx; // fallback, though unlikely

        for (int i = i0; i < all.size(); i++) {
            org.antlr.v4.runtime.Token t = all.get(i);
            String tt = t.getText();
            if (tt == null) continue;
            String u = tt.toUpperCase();

            // Detect GO as a hard stop when not inside a BEGIN..END block
            if ("GO".equals(u) && depth == 0) {
                nextGoStart = t.getStartIndex();
                break;
            }

            if ("BEGIN".equals(u)) {
                if (firstBeginStart == null) firstBeginStart = t.getStartIndex();
                depth++;
            } else if ("END".equals(u)) {
                if (depth > 0) depth--;
                if (depth == 0 && firstBeginStart != null) {
                    matchingEndStop = t.getStopIndex();
                    break; // matched the first BEGIN..END block
                }
            }
        }

        // Prefer BEGIN..END when present
        if (firstBeginStart != null && matchingEndStop != null && firstBeginStart >= 0 && matchingEndStop >= firstBeginStart && matchingEndStop < sourceCode.length()) {
            String s = sourceCode.substring(firstBeginStart, matchingEndStop + 1);
            return s;
        }

        // Otherwise use AS..GO (or AS..EOF if no GO)
        int stop = (nextGoStart != null ? nextGoStart : sourceCode.length());
        if (afterAs >= 0 && stop > afterAs && stop <= sourceCode.length()) {
            String s = sourceCode.substring(afterAs, stop);
            return s;
        }
        return null;
    }

    private String extractFunctionBody(TSqlParser.Create_or_alter_functionContext ctx) {
        // Try to get body from func_body_returns_scalar (has BEGIN...END with sql_clauses)
        if (ctx.func_body_returns_scalar() != null) {
            TSqlParser.Func_body_returns_scalarContext body = ctx.func_body_returns_scalar();
            // Prefer full BEGIN...END block span
            if (body.BEGIN() != null) {
                int start = body.BEGIN().getSymbol().getStartIndex();
                int stop = body.getStop().getStopIndex();
                if (start >= 0 && stop >= start && stop < sourceCode.length()) {
                    return sourceCode.substring(start, stop + 1);
                }
            } else if (body.sql_clauses() != null && !body.sql_clauses().isEmpty()) {
                int start = body.sql_clauses(0).getStart().getStartIndex();
                int stop = body.getStop().getStopIndex();
                if (start >= 0 && stop >= start && stop < sourceCode.length()) {
                    return sourceCode.substring(start, stop + 1);
                }
            }
            // Fallback: RETURN expression span (may contain subqueries)
            if (body.ret != null) {
                int start = body.ret.getStart().getStartIndex();
                int stop = body.ret.getStop().getStopIndex();
                if (start >= 0 && stop >= start && stop < sourceCode.length()) {
                    String expr = sourceCode.substring(start, stop + 1);
                    return "RETURN " + expr + ";";
                }
            }
        }
        // func_body_returns_table also has sql_clauses
        else if (ctx.func_body_returns_table() != null) {
            TSqlParser.Func_body_returns_tableContext body = ctx.func_body_returns_table();
            if (body.sql_clauses() != null && !body.sql_clauses().isEmpty()) {
                int start = body.sql_clauses(0).getStart().getStartIndex();
                int stop = body.getStop().getStopIndex();
                
                if (start >= 0 && stop >= start && stop < sourceCode.length()) {
                    return sourceCode.substring(start, stop + 1);
                }
            }
        }
        // func_body_returns_select has a RETURN with select_statement
        else if (ctx.func_body_returns_select() != null) {
            TSqlParser.Func_body_returns_selectContext body = ctx.func_body_returns_select();
            if (body.select_statement_standalone() != null) {
                int start = body.select_statement_standalone().getStart().getStartIndex();
                int stop = body.select_statement_standalone().getStop().getStopIndex();
                
                if (start >= 0 && stop >= start && stop < sourceCode.length()) {
                    return sourceCode.substring(start, stop + 1);
                }
            }
        }
        
        return null;
    }

    // ---- Trigger extraction ----
    @Override
    public Void visitCreate_or_alter_dml_trigger(TSqlParser.Create_or_alter_dml_triggerContext ctx) {
        CreateTriggerOperation op = new CreateTriggerOperation();
        op.orReplace = ctx.ALTER() != null || (ctx.OR() != null && ctx.ALTER() != null);

        // Extract trigger name from simple_name
        if (ctx.simple_name() != null) {
            String[] parts = extractSchemaAndNameFromSimpleName(ctx.simple_name());
            op.schema = parts[0];
            op.triggerName = parts[1];
        }

        // Extract table name
        if (ctx.table_name() != null) {
            op.tableName = extractTableNameText(ctx.table_name());
        }

        // Extract timing (FOR/AFTER/INSTEAD OF)
        if (ctx.INSTEAD() != null) {
            op.timing = "INSTEAD OF";
        } else if (ctx.AFTER() != null) {
            op.timing = "AFTER";
        } else if (ctx.FOR() != null) {
            op.timing = "AFTER"; // FOR is equivalent to AFTER in T-SQL
        }

        // Extract events (INSERT, UPDATE, DELETE)
        List<TSqlParser.Dml_trigger_operationContext> ops = ctx.dml_trigger_operation();
        if (ops != null) {
            for (TSqlParser.Dml_trigger_operationContext opCtx : ops) {
                if (opCtx.INSERT() != null) op.events.add("INSERT");
                if (opCtx.UPDATE() != null) op.events.add("UPDATE");
                if (opCtx.DELETE() != null) op.events.add("DELETE");
            }
        }

        // Extract body and analyze for references/calls
        analyzeBody(extractTriggerBody(ctx), op);
        triggers.add(op);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreate_or_alter_ddl_trigger(TSqlParser.Create_or_alter_ddl_triggerContext ctx) {
        CreateTriggerOperation op = new CreateTriggerOperation();
        op.orReplace = ctx.ALTER() != null || (ctx.OR() != null && ctx.ALTER() != null);

        // Extract trigger name from simple_name
        if (ctx.simple_name() != null) {
            String[] parts = extractSchemaAndNameFromSimpleName(ctx.simple_name());
            op.schema = parts[0];
            op.triggerName = parts[1];
        }

        // DDL triggers are on DATABASE or ALL SERVER, not a table
        if (ctx.DATABASE() != null) {
            op.tableName = "DATABASE";
        } else if (ctx.ALL() != null && ctx.SERVER() != null) {
            op.tableName = "ALL SERVER";
        }

        // Extract timing (FOR/AFTER for DDL triggers)
        if (ctx.AFTER() != null) {
            op.timing = "AFTER";
        } else if (ctx.FOR() != null) {
            op.timing = "AFTER"; // FOR is equivalent to AFTER
        }

        // Extract DDL events
        List<TSqlParser.Ddl_trigger_operationContext> ops = ctx.ddl_trigger_operation();
        if (ops != null) {
            for (TSqlParser.Ddl_trigger_operationContext opCtx : ops) {
                if (opCtx.simple_id() != null) {
                    op.events.add(opCtx.simple_id().getText().toUpperCase());
                }
            }
        }

        // Extract body and analyze for references/calls
        analyzeBody(extractDdlTriggerBody(ctx), op);
        triggers.add(op);
        return visitChildren(ctx);
    }

    private String[] extractSchemaAndNameFromSimpleName(TSqlParser.Simple_nameContext ctx) {
        if (ctx == null) return new String[]{null, null};
        String schema = ctx.schema != null ? RoutineSqlUtils.stripQuotes(ctx.schema.getText()) : null;
        String name = ctx.name != null ? RoutineSqlUtils.stripQuotes(ctx.name.getText()) : null;
        return new String[]{schema, name};
    }

    private String extractTableNameText(TSqlParser.Table_nameContext ctx) {
        if (ctx == null) return null;
        StringBuilder sb = new StringBuilder();
        if (ctx.schema != null) {
            sb.append(RoutineSqlUtils.stripQuotes(ctx.schema.getText())).append(".");
        }
        if (ctx.table != null) {
            sb.append(RoutineSqlUtils.stripQuotes(ctx.table.getText()));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String extractTriggerBody(TSqlParser.Create_or_alter_dml_triggerContext ctx) {
        return extractBodyFromSqlClauses(ctx.sql_clauses(), ctx.getStop());
    }

    private String extractDdlTriggerBody(TSqlParser.Create_or_alter_ddl_triggerContext ctx) {
        return extractBodyFromSqlClauses(ctx.sql_clauses(), ctx.getStop());
    }

    private String extractBodyFromSqlClauses(List<TSqlParser.Sql_clausesContext> clauses, 
                                              org.antlr.v4.runtime.Token stopToken) {
        if (clauses == null || clauses.isEmpty() || stopToken == null) return null;
        int start = clauses.get(0).getStart().getStartIndex();
        int stop = stopToken.getStopIndex();
        if (start >= 0 && stop >= start && stop < sourceCode.length()) {
            return sourceCode.substring(start, stop + 1);
        }
        return null;
    }
}
