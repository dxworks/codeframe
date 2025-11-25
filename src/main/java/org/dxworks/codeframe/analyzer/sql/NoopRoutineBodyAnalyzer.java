package org.dxworks.codeframe.analyzer.sql;

public class NoopRoutineBodyAnalyzer implements RoutineBodyAnalyzer {
    @Override
    public Result analyze(String body, String dialectHint) {
        return new Result();
    }
}
