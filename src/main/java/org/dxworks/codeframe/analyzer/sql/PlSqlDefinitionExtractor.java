package org.dxworks.codeframe.analyzer.sql;

import org.dxworks.codeframe.analyzer.sql.generated.PlSqlParser;
import org.dxworks.codeframe.analyzer.sql.generated.PlSqlParserBaseVisitor;
import org.dxworks.codeframe.model.sql.*;

import java.util.ArrayList;
import java.util.List;

public class PlSqlDefinitionExtractor extends PlSqlParserBaseVisitor<Void> {

    private final List<CreateProcedureOperation> procedures = new ArrayList<>();
    private final List<CreateFunctionOperation> functions = new ArrayList<>();
    private final List<CreateTriggerOperation> triggers = new ArrayList<>();

    private String currentPackageSchema;
    private String currentPackageName;

    public PlSqlDefinitionExtractor() {
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
    public Void visitCreate_procedure_body(PlSqlParser.Create_procedure_bodyContext ctx) {
        CreateProcedureOperation op = new CreateProcedureOperation();
        op.orReplace = ctx.OR() != null && ctx.REPLACE() != null;
        String[] schemaName = RoutineSqlUtils.splitSchemaAndName(ctx.procedure_name().getText());
        op.schema = schemaName[0];
        op.procedureName = schemaName[1];
        if (ctx.parameter() != null) {
            for (PlSqlParser.ParameterContext parameterContext : ctx.parameter()) {
                ParameterDefinition pd = extractParameter(parameterContext);
                if (pd != null) op.parameters.add(pd);
            }
        }
        analyzeBody(ctx.body(), op);
        procedures.add(op);
        return super.visitCreate_procedure_body(ctx);
    }

    @Override
    public Void visitCreate_function_body(PlSqlParser.Create_function_bodyContext ctx) {
        CreateFunctionOperation op = new CreateFunctionOperation();
        op.orReplace = ctx.OR() != null && ctx.REPLACE() != null;
        String[] schemaName = RoutineSqlUtils.splitSchemaAndName(ctx.function_name().getText());
        op.schema = schemaName[0];
        op.functionName = schemaName[1];
        if (ctx.parameter() != null) {
            for (PlSqlParser.ParameterContext parameterContext : ctx.parameter()) {
                ParameterDefinition pd = extractParameter(parameterContext);
                if (pd != null) op.parameters.add(pd);
            }
        }
        if (ctx.type_spec() != null) op.returnType = ctx.type_spec().getText();
        analyzeBody(ctx.body(), op);
        functions.add(op);
        return super.visitCreate_function_body(ctx);
    }

    @Override
    public Void visitCreate_package_body(PlSqlParser.Create_package_bodyContext ctx) {
        String previousSchema = currentPackageSchema;
        String previousName = currentPackageName;

        if (ctx.schema_object_name() != null) {
            currentPackageSchema = RoutineSqlUtils.stripQuotes(ctx.schema_object_name().getText());
        } else {
            currentPackageSchema = null;
        }

        java.util.List<PlSqlParser.Package_nameContext> names = ctx.package_name();
        if (names != null && !names.isEmpty()) {
            currentPackageName = RoutineSqlUtils.stripQuotes(names.get(0).getText());
        } else {
            currentPackageName = null;
        }

        Void result = super.visitCreate_package_body(ctx);

        currentPackageSchema = previousSchema;
        currentPackageName = previousName;

        return result;
    }

    @Override
    public Void visitProcedure_body(PlSqlParser.Procedure_bodyContext ctx) {
        if (currentPackageName == null) {
            return super.visitProcedure_body(ctx);
        }

        CreateProcedureOperation op = new CreateProcedureOperation();
        op.orReplace = false;
        op.schema = currentPackageSchema;

        String localName = ctx.identifier() != null ? RoutineSqlUtils.stripQuotes(ctx.identifier().getText()) : null;
        if (localName != null) {
            op.procedureName = currentPackageName + "." + localName;
        }

        if (ctx.parameter() != null) {
            for (PlSqlParser.ParameterContext parameterContext : ctx.parameter()) {
                ParameterDefinition pd = extractParameter(parameterContext);
                if (pd != null) op.parameters.add(pd);
            }
        }

        analyzeBody(ctx.body(), op);
        procedures.add(op);

        return super.visitProcedure_body(ctx);
    }

    @Override
    public Void visitFunction_body(PlSqlParser.Function_bodyContext ctx) {
        if (currentPackageName == null) {
            return super.visitFunction_body(ctx);
        }

        CreateFunctionOperation op = new CreateFunctionOperation();
        op.orReplace = false;
        op.schema = currentPackageSchema;

        String localName = ctx.identifier() != null ? RoutineSqlUtils.stripQuotes(ctx.identifier().getText()) : null;
        if (localName != null) {
            op.functionName = currentPackageName + "." + localName;
        }

        if (ctx.parameter() != null) {
            for (PlSqlParser.ParameterContext parameterContext : ctx.parameter()) {
                ParameterDefinition pd = extractParameter(parameterContext);
                if (pd != null) op.parameters.add(pd);
            }
        }

        if (ctx.type_spec() != null) op.returnType = ctx.type_spec().getText();

        analyzeBody(ctx.body(), op);
        functions.add(op);

        return super.visitFunction_body(ctx);
    }

    private void analyzeBody(org.antlr.v4.runtime.tree.ParseTree ctx, HasReferencesAndCalls op) {
        if (ctx == null || op == null) return;
        PlSqlReferenceExtractor extractor = new PlSqlReferenceExtractor();
        extractor.visit(ctx);
        op.getReferences().relations.addAll(extractor.getTableReferences());
        op.getCalls().functions.addAll(extractor.getFunctionCalls());
        op.getCalls().procedures.addAll(extractor.getProcedureCalls());
    }

    private ParameterDefinition extractParameter(PlSqlParser.ParameterContext ctx) {
        if (ctx == null) return null;
        ParameterDefinition pd = new ParameterDefinition();
        if (ctx.parameter_name() != null) pd.name = ctx.parameter_name().getText();
        if (ctx.type_spec() != null) pd.type = ctx.type_spec().getText();
        boolean hasIn = ctx.IN() != null && !ctx.IN().isEmpty();
        boolean hasOut = ctx.OUT() != null && !ctx.OUT().isEmpty();
        boolean hasInOut = ctx.INOUT() != null && !ctx.INOUT().isEmpty();

        if (hasInOut || (hasIn && hasOut)) {
            pd.direction = "INOUT";
        } else if (hasOut) {
            pd.direction = "OUT";
        } else {
            // Default in PL/SQL is IN when no mode is specified
            pd.direction = "IN";
        }
        return pd;
    }

    // ---- Trigger extraction ----

    @Override
    public Void visitCreate_trigger(PlSqlParser.Create_triggerContext ctx) {
        CreateTriggerOperation op = new CreateTriggerOperation();
        op.orReplace = ctx.OR() != null && ctx.REPLACE() != null;

        // Extract trigger name
        if (ctx.trigger_name() != null) {
            String[] schemaName = RoutineSqlUtils.splitSchemaAndName(ctx.trigger_name().getText());
            op.schema = schemaName[0];
            op.triggerName = schemaName[1];
        }

        // Handle simple DML trigger (BEFORE/AFTER/INSTEAD OF on table)
        if (ctx.simple_dml_trigger() != null) {
            PlSqlParser.Simple_dml_triggerContext dml = ctx.simple_dml_trigger();
            
            // Extract timing
            if (dml.BEFORE() != null) {
                op.timing = "BEFORE";
            } else if (dml.AFTER() != null) {
                op.timing = "AFTER";
            } else if (dml.INSTEAD() != null) {
                op.timing = "INSTEAD OF";
            }

            // Extract events and table from dml_event_clause
            if (dml.dml_event_clause() != null) {
                extractDmlEventClause(dml.dml_event_clause(), op);
            }
        }
        // Handle compound DML trigger (FOR on table)
        else if (ctx.compound_dml_trigger() != null) {
            PlSqlParser.Compound_dml_triggerContext compound = ctx.compound_dml_trigger();
            op.timing = "COMPOUND";
            
            if (compound.dml_event_clause() != null) {
                extractDmlEventClause(compound.dml_event_clause(), op);
            }
        }
        // Handle non-DML trigger (DDL events on DATABASE/SCHEMA)
        else if (ctx.non_dml_trigger() != null) {
            PlSqlParser.Non_dml_triggerContext nonDml = ctx.non_dml_trigger();
            
            // Extract timing
            if (nonDml.BEFORE() != null) {
                op.timing = "BEFORE";
            } else if (nonDml.AFTER() != null) {
                op.timing = "AFTER";
            }

            // Extract DDL events
            List<PlSqlParser.Non_dml_eventContext> events = nonDml.non_dml_event();
            if (events != null) {
                for (PlSqlParser.Non_dml_eventContext event : events) {
                    op.events.add(event.getText().toUpperCase());
                }
            }

            // Table is DATABASE or SCHEMA for non-DML triggers
            if (nonDml.DATABASE() != null) {
                op.tableName = "DATABASE";
            } else if (nonDml.SCHEMA() != null) {
                // Check for schema_name prefix (e.g., ON hr.SCHEMA)
                if (nonDml.schema_name() != null) {
                    op.tableName = nonDml.schema_name().getText() + ".SCHEMA";
                } else {
                    op.tableName = "SCHEMA";
                }
            }
        }

        // Analyze trigger body for references/calls
        analyzeBody(ctx.trigger_body(), op);

        triggers.add(op);
        return super.visitCreate_trigger(ctx);
    }

    private void extractDmlEventClause(PlSqlParser.Dml_event_clauseContext ctx, CreateTriggerOperation op) {
        // Extract events (INSERT, UPDATE, DELETE)
        List<PlSqlParser.Dml_event_elementContext> elements = ctx.dml_event_element();
        if (elements != null) {
            for (PlSqlParser.Dml_event_elementContext elem : elements) {
                if (elem.INSERT() != null) op.events.add("INSERT");
                if (elem.UPDATE() != null) op.events.add("UPDATE");
                if (elem.DELETE() != null) op.events.add("DELETE");
            }
        }

        // Extract table name
        if (ctx.tableview_name() != null) {
            String[] schemaTable = RoutineSqlUtils.splitSchemaAndName(ctx.tableview_name().getText());
            if (schemaTable[0] != null) {
                op.tableName = schemaTable[0] + "." + schemaTable[1];
            } else {
                op.tableName = schemaTable[1];
            }
        }
    }

}
