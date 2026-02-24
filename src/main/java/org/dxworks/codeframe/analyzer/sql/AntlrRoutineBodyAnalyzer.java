package org.dxworks.codeframe.analyzer.sql;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * ANTLR-based routine body analyzer, parameterized by dialect.
 * Replaces the structurally identical TSqlRoutineBodyAnalyzer and PlSqlRoutineBodyAnalyzer.
 */
final class AntlrRoutineBodyAnalyzer implements RoutineBodyAnalyzer {

    @FunctionalInterface
    interface ParseAndExtract {
        void execute(String body, ReferenceCollector target);
    }

    private final ParseAndExtract strategy;

    private AntlrRoutineBodyAnalyzer(ParseAndExtract strategy) {
        this.strategy = strategy;
    }

    @Override
    public Result analyze(String body, String dialectHint) {
        Result result = new Result();

        if (body == null || body.trim().isEmpty()) {
            return result;
        }

        try {
            ReferenceCollector collector = new ReferenceCollector();
            strategy.execute(body, collector);

            result.relations.addAll(collector.getTableReferences());
            result.procedureCalls.addAll(collector.getProcedureCalls());
            result.functionCalls.addAll(collector.getFunctionCalls());

        } catch (Exception e) {
            // On parse error, return empty result (graceful degradation)
        }

        return result;
    }

    static AntlrRoutineBodyAnalyzer forTSql() {
        return new AntlrRoutineBodyAnalyzer((body, collector) -> {
            ParseTree tree = AntlrParserFactory.createTSqlParser(body).tsql_file();
            TSqlReferenceExtractor extractor = new TSqlReferenceExtractor();
            extractor.visit(tree);
            collector.getTableReferences().addAll(extractor.getTableReferences());
            collector.getProcedureCalls().addAll(extractor.getProcedureCalls());
            collector.getFunctionCalls().addAll(extractor.getFunctionCalls());
        });
    }

    static AntlrRoutineBodyAnalyzer forPlSql() {
        return new AntlrRoutineBodyAnalyzer((body, collector) -> {
            ParseTree tree = AntlrParserFactory.createPlSqlParser(body).body();
            PlSqlReferenceExtractor extractor = new PlSqlReferenceExtractor();
            extractor.visit(tree);
            collector.getTableReferences().addAll(extractor.getTableReferences());
            collector.getProcedureCalls().addAll(extractor.getProcedureCalls());
            collector.getFunctionCalls().addAll(extractor.getFunctionCalls());
        });
    }
}
