package org.dxworks.codeframe.analyzer.sql;

import org.antlr.v4.runtime.tree.ParseTree;
import org.dxworks.codeframe.analyzer.sql.generated.PlSqlParser;

public class PlSqlRoutineBodyAnalyzer implements RoutineBodyAnalyzer {

    @Override
    public Result analyze(String body, String dialectHint) {
        Result result = new Result();

        if (body == null || body.trim().isEmpty()) {
            return result;
        }

        try {
            PlSqlParser parser = AntlrParserFactory.createPlSqlParser(body);
            // The body text passed from PlSqlDefinitionExtractor is a BEGIN...END block,
            // which matches the 'body' rule in the grammar.
            ParseTree tree = parser.body();

            PlSqlReferenceExtractor extractor = new PlSqlReferenceExtractor();
            extractor.visit(tree);

            result.relations.addAll(extractor.getTableReferences());
            result.procedureCalls.addAll(extractor.getProcedureCalls());
            result.functionCalls.addAll(extractor.getFunctionCalls());

        } catch (Exception e) {
            // graceful degradation: return whatever we managed to collect so far
        }

        return result;
    }
}
