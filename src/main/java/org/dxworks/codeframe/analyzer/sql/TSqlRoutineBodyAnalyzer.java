package org.dxworks.codeframe.analyzer.sql;

import org.antlr.v4.runtime.tree.ParseTree;
import org.dxworks.codeframe.analyzer.sql.generated.TSqlParser;

public class TSqlRoutineBodyAnalyzer implements RoutineBodyAnalyzer {

    @Override
    public Result analyze(String body, String dialectHint) {
        Result result = new Result();
        
        if (body == null || body.trim().isEmpty()) {
            return result;
        }

        try {
            TSqlParser parser = AntlrParserFactory.createTSqlParser(body);
            ParseTree tree = parser.tsql_file();

            // Walk the tree to extract references and calls
            TSqlReferenceExtractor extractor = new TSqlReferenceExtractor();
            extractor.visit(tree);

            result.relations.addAll(extractor.getTableReferences());
            result.procedureCalls.addAll(extractor.getProcedureCalls());
            result.functionCalls.addAll(extractor.getFunctionCalls());

        } catch (Exception e) {
            // On parse error, return empty result (graceful degradation)
        }

        return result;
    }
}
