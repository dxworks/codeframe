package org.dxworks.codeframe.analyzer.sql;

import java.util.ArrayList;
import java.util.List;

public interface RoutineBodyAnalyzer {
    class Result {
        public final List<String> relations = new ArrayList<>();
        public final List<String> functionCalls = new ArrayList<>();
        public final List<String> procedureCalls = new ArrayList<>();
    }

    /**
     * Analyze a routine body and extract referenced relations and routine calls.
     *
     * @param body the routine body text (may be null during scaffolding)
     * @param dialectHint optional hint like "plpgsql", "tsql", or "unknown"
     * @return Result with lists possibly empty
     */
    Result analyze(String body, String dialectHint);
}
