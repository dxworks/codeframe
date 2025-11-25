package org.dxworks.codeframe.analyzer.sql;

import org.dxworks.codeframe.analyzer.sql.generated.PlSqlParser;
import org.dxworks.codeframe.analyzer.sql.generated.PlSqlParserBaseVisitor;

import java.util.Set;

/**
 * Visitor that extracts table references and routine calls from PL/SQL bodies.
 *
 * This is intentionally conservative: we treat any tableview_name inside a body
 * as a table reference, and any function_name inside the body as a function call.
 * Procedure calls are captured from call_statement nodes.
 */
public class PlSqlReferenceExtractor extends PlSqlParserBaseVisitor<Void> {

    private final BaseReferenceExtractor state = new BaseReferenceExtractor() {};

    public Set<String> getTableReferences() {
        return state.getTableReferences();
    }

    public Set<String> getProcedureCalls() {
        return state.getProcedureCalls();
    }

    public Set<String> getFunctionCalls() {
        return state.getFunctionCalls();
    }

    @Override
    public Void visitTableview_name(PlSqlParser.Tableview_nameContext ctx) {
        state.addTableReference(RoutineSqlUtils.normalizeIdentifierChain(ctx.getText()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitOther_function(PlSqlParser.Other_functionContext ctx) {
        if (ctx == null) return null;
        String text = ctx.getText();
        if (text != null) {
            int paren = text.indexOf('(');
            if (paren >= 0) {
                state.addFunctionCall(RoutineSqlUtils.normalizeFunctionNameChain(text.substring(0, paren)));
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitCall_statement(PlSqlParser.Call_statementContext ctx) {
        if (ctx == null) return null;
        String text = ctx.getText();
        if (text != null) {
            String s = text.toUpperCase().startsWith("CALL") ? text.substring(4) : text;
            int paren = s.indexOf('(');
            if (paren >= 0) {
                s = s.substring(0, paren);
            }
            state.addProcedureCall(RoutineSqlUtils.normalizeIdentifierChain(s));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitFunction_name(PlSqlParser.Function_nameContext ctx) {
        state.addFunctionCall(RoutineSqlUtils.normalizeIdentifierChain(ctx.getText()));
        return visitChildren(ctx);
    }

    @Override
    public Void visitGeneral_element(PlSqlParser.General_elementContext ctx) {
        if (ctx == null) return null;
        String text = ctx.getText();
        if (text != null) {
            int paren = text.indexOf('(');
            if (paren >= 0) {
                state.addFunctionCall(RoutineSqlUtils.normalizeFunctionNameChain(text.substring(0, paren)));
            }
        }
        return visitChildren(ctx);
    }
}
