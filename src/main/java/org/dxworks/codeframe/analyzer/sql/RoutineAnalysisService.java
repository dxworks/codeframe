package org.dxworks.codeframe.analyzer.sql;

import net.sf.jsqlparser.statement.create.function.CreateFunction;
import net.sf.jsqlparser.statement.create.procedure.CreateProcedure;
import org.dxworks.codeframe.model.sql.AlterFunctionOperation;
import org.dxworks.codeframe.model.sql.AlterProcedureOperation;
import org.dxworks.codeframe.model.sql.CreateFunctionOperation;
import org.dxworks.codeframe.model.sql.CreateProcedureOperation;
import org.dxworks.codeframe.model.sql.SQLFileAnalysis;

import java.util.List;

public class RoutineAnalysisService {

    private final RoutineBodyAnalyzer noopAnalyzer;
    private final RoutineBodyAnalyzer tsqlAnalyzer;
    private final RoutineBodyAnalyzer sqlRoutineAnalyzer;

    public RoutineAnalysisService(RoutineBodyAnalyzer noopAnalyzer,
                                  RoutineBodyAnalyzer tsqlAnalyzer,
                                  RoutineBodyAnalyzer sqlRoutineAnalyzer) {
        this.noopAnalyzer = noopAnalyzer;
        this.tsqlAnalyzer = tsqlAnalyzer;
        this.sqlRoutineAnalyzer = sqlRoutineAnalyzer;
    }

    public void handleCreateFunction(CreateFunction cf, SQLFileAnalysis out, String sourceCode) {
        CreateFunctionOperation op = new CreateFunctionOperation();

        String decl = cf.toString();
        RoutineSignatureParser.RoutineSignature sig = RoutineSignatureParser.parse(decl, true);
        if (sig != null) {
            op.functionName = sig.name;
            op.schema = sig.schema;
            op.parameters.addAll(sig.parameters);
            op.returnType = sig.returnType;
        } else {
            List<String> parts = cf.getFunctionDeclarationParts();
            if (parts != null && !parts.isEmpty()) {
                String[] sn = RoutineSqlUtils.splitSchemaAndName(parts.get(parts.size() - 1));
                op.schema = sn[0];
                op.functionName = sn[1];
            }
        }

        populateRoutineBodyData(op.references, op.calls, sourceCode, op.schema, op.functionName, true);

        boolean isAlter = decl.toUpperCase().contains("CREATE OR REPLACE");
        if (isAlter) {
            AlterFunctionOperation alt = new AlterFunctionOperation();
            alt.functionName = op.functionName;
            alt.schema = op.schema;
            alt.parameters.addAll(op.parameters);
            alt.returnType = op.returnType;
            alt.references.relations.addAll(op.references.relations);
            alt.calls.functions.addAll(op.calls.functions);
            alt.calls.procedures.addAll(op.calls.procedures);
            out.alterFunctions.add(alt);
        } else {
            out.createFunctions.add(op);
        }
    }

    public void handleCreateProcedure(CreateProcedure cp, SQLFileAnalysis out, String sourceCode) {
        CreateProcedureOperation op = new CreateProcedureOperation();

        String decl = cp.toString();
        RoutineSignatureParser.RoutineSignature sig = RoutineSignatureParser.parse(decl, false);
        if (sig != null) {
            op.procedureName = sig.name;
            op.schema = sig.schema;
            op.parameters.addAll(sig.parameters);
        } else {
            List<String> parts = cp.getFunctionDeclarationParts();
            if (parts != null && !parts.isEmpty()) {
                String[] sn = RoutineSqlUtils.splitSchemaAndName(parts.get(parts.size() - 1));
                op.schema = sn[0];
                op.procedureName = sn[1];
            }
        }

        populateRoutineBodyData(op.references, op.calls, sourceCode, op.schema, op.procedureName, false);

        boolean isAlter = decl.toUpperCase().contains("CREATE OR REPLACE");
        if (isAlter) {
            AlterProcedureOperation alt = new AlterProcedureOperation();
            alt.procedureName = op.procedureName;
            alt.schema = op.schema;
            alt.parameters.addAll(op.parameters);
            alt.references.relations.addAll(op.references.relations);
            alt.calls.functions.addAll(op.calls.functions);
            alt.calls.procedures.addAll(op.calls.procedures);
            out.alterProcedures.add(alt);
        } else {
            out.createProcedures.add(op);
        }
    }

    private void populateRoutineBodyData(org.dxworks.codeframe.model.sql.SqlReferences references,
                                         org.dxworks.codeframe.model.sql.SqlInvocations calls,
                                         String sourceCode,
                                         String schema,
                                         String routineName,
                                         boolean isFunction) {
        String qualifiedName = schema != null ? schema + "." + routineName : routineName;
        if (qualifiedName == null) {
            return;
        }
        String[] bodyAndHint = DialectHeuristics.extractRoutineBodyAndDialect(sourceCode, qualifiedName, isFunction);
        String body = bodyAndHint[0];
        String hint = bodyAndHint[1];
        RoutineBodyAnalyzer analyzer = selectAnalyzer(hint);
        RoutineBodyAnalyzer.Result rb = analyzer != null ? analyzer.analyze(body, hint) : null;
        if (rb != null) {
            if (rb.relations != null) references.relations.addAll(rb.relations);
            if (rb.functionCalls != null) calls.functions.addAll(rb.functionCalls);
            if (rb.procedureCalls != null) calls.procedures.addAll(rb.procedureCalls);
        }
    }

    private RoutineBodyAnalyzer selectAnalyzer(String dialectHint) {
        if ("tsql".equalsIgnoreCase(dialectHint)) {
            return tsqlAnalyzer;
        } else if ("mysql".equalsIgnoreCase(dialectHint) ||
                   "plpgsql".equalsIgnoreCase(dialectHint)) {
            return sqlRoutineAnalyzer;
        }
        return noopAnalyzer;
    }

}
