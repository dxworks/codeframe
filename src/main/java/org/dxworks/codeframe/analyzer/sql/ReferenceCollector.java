package org.dxworks.codeframe.analyzer.sql;

import java.util.HashSet;
import java.util.Set;

/**
 * Collects table references and routine calls extracted by ANTLR-based visitors.
 */
public class ReferenceCollector {

    protected final Set<String> tableReferences = new HashSet<>();
    protected final Set<String> procedureCalls = new HashSet<>();
    protected final Set<String> functionCalls = new HashSet<>();

    public Set<String> getTableReferences() {
        return tableReferences;
    }

    public Set<String> getProcedureCalls() {
        return procedureCalls;
    }

    public Set<String> getFunctionCalls() {
        return functionCalls;
    }

    /**
     * Adds a table reference if non-null and non-empty.
     */
    protected void addTableReference(String name) {
        if (name != null && !name.trim().isEmpty()) {
            tableReferences.add(name.trim());
        }
    }

    /**
     * Adds a procedure call if non-null and non-empty.
     */
    protected void addProcedureCall(String name) {
        if (name != null && !name.trim().isEmpty()) {
            procedureCalls.add(name.trim());
        }
    }

    /**
     * Adds a function call if non-null and non-empty.
     */
    protected void addFunctionCall(String name) {
        if (name != null && !name.trim().isEmpty()) {
            functionCalls.add(name.trim());
        }
    }
}
