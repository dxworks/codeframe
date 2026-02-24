package org.dxworks.codeframe.model.sql;

/**
 * Shared contract for SQL operations that carry references (table/view names)
 * and calls (function/procedure invocations) extracted from their body.
 */
public interface HasReferencesAndCalls {
    SqlReferences getReferences();
    SqlInvocations getCalls();
}
