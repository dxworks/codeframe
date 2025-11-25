package org.dxworks.codeframe.analyzer.sql;

import org.dxworks.codeframe.analyzer.sql.generated.PlSqlParser;
import org.dxworks.codeframe.analyzer.sql.generated.PlSqlParserBaseVisitor;
import org.dxworks.codeframe.model.sql.CreateFunctionOperation;
import org.dxworks.codeframe.model.sql.CreateProcedureOperation;
import org.dxworks.codeframe.model.sql.ParameterDefinition;
import org.dxworks.codeframe.model.sql.SqlInvocations;
import org.dxworks.codeframe.model.sql.SqlReferences;

import java.util.ArrayList;
import java.util.List;

public class PlSqlDefinitionExtractor extends PlSqlParserBaseVisitor<Void> {

    private final String source;
    private final RoutineBodyAnalyzer bodyAnalyzer = new PlSqlRoutineBodyAnalyzer();
    private final List<CreateProcedureOperation> procedures = new ArrayList<>();
    private final List<CreateFunctionOperation> functions = new ArrayList<>();

    private String currentPackageSchema;
    private String currentPackageName;

    public PlSqlDefinitionExtractor(String source) {
        this.source = source == null ? "" : source;
    }

    public List<CreateProcedureOperation> getProcedures() {
        return procedures;
    }

    public List<CreateFunctionOperation> getFunctions() {
        return functions;
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
        analyzeBody(ctx.body(), op.references, op.calls);
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
        analyzeBody(ctx.body(), op.references, op.calls);
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

        analyzeBody(ctx.body(), op.references, op.calls);
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

        analyzeBody(ctx.body(), op.references, op.calls);
        functions.add(op);

        return super.visitFunction_body(ctx);
    }

    private void analyzeBody(PlSqlParser.BodyContext bodyCtx, SqlReferences references,
                             SqlInvocations calls) {
        if (bodyCtx == null || references == null || calls == null) return;
        String text = slice(bodyCtx);
        if (text == null || text.isEmpty()) return;
        RoutineBodyAnalyzer.Result result = bodyAnalyzer.analyze(text, "plpgsql");
        if (result == null) return;
        references.relations.addAll(result.relations);
        calls.functions.addAll(result.functionCalls);
        calls.procedures.addAll(result.procedureCalls);
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

    private String slice(org.antlr.v4.runtime.ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null || ctx.getStop() == null) return null;
        int start = ctx.getStart().getStartIndex();
        int stop = ctx.getStop().getStopIndex();
        if (start < 0 || stop < start || stop >= source.length()) return null;
        return source.substring(start, stop + 1);
    }

}
