package org.dxworks.codeframe.analyzer.sql;

import org.dxworks.codeframe.analyzer.sql.generated.PlSqlParser;
import org.dxworks.codeframe.analyzer.sql.generated.PlSqlParserBaseVisitor;

import java.util.Set;

/**
 * Collects table references and routine calls from PL/SQL top-level statements
 * (anonymous blocks and standalone DML / CALL statements), excluding routine bodies.
 */
public class PlSqlTopLevelCollector extends PlSqlParserBaseVisitor<Void> {

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
    public Void visitUnit_statement(PlSqlParser.Unit_statementContext ctx) {
        if (ctx == null) return null;

        // Use the existing reference extractor on the specific top-level constructs
        PlSqlReferenceExtractor extractor = new PlSqlReferenceExtractor();

        if (ctx.anonymous_block() != null) {
            extractor.visit(ctx.anonymous_block());
        }
        if (ctx.call_statement() != null) {
            extractor.visit(ctx.call_statement());
        }
        if (ctx.data_manipulation_language_statements() != null) {
            extractor.visit(ctx.data_manipulation_language_statements());
        }

        // Additionally, handle plain SQL SELECT statements that appear as
        // top-level unit statements (outside any BEGIN..END block). These may
        // not always be wrapped as data_manipulation_language_statements, so
        // check the first token.
        if (ctx.getStart() != null) {
            String first = ctx.getStart().getText();
            if (first != null && first.equalsIgnoreCase("SELECT")) {
                extractor.visit(ctx);
            }
        }

        state.getTableReferences().addAll(extractor.getTableReferences());
        state.getProcedureCalls().addAll(extractor.getProcedureCalls());
        state.getFunctionCalls().addAll(extractor.getFunctionCalls());

        return null;
    }
}
