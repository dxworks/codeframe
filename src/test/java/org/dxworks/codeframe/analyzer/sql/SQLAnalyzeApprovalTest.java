package org.dxworks.codeframe.analyzer.sql;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.App;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.TestUtils;
import org.dxworks.codeframe.CodeframeConfig;
import org.dxworks.codeframe.model.Analysis;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class SQLAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/sql/";
    @Test
    void analyze_SQL_Sample() throws Exception {
        verify("sample.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_AlterRoutinesPLSQL() throws Exception {
        verify("plsql_routine_bodies.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_ConstraintsAndIndexes() throws Exception {
        verify("constraints_and_indexes.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_AlterTablesViews() throws Exception {
        verify("alter_tables_views.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_DropStatements() throws Exception {
        verify("drop_statements.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_ProceduresFunctions() throws Exception {
        verify("procedures_functions.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_FunctionsCrossDialects() throws Exception {
        verify("functions_crossdialects.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_TopLevelStatements() throws Exception {
        verify("top_level_statements.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_TopLevelStatementsMySQL() throws Exception {
        verify("top_level_mysql.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_TopLevelStatementsTSQL() throws Exception {
        verify("top_level_tsql.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_TopLevelStatementsPLSQL() throws Exception {
        verify("top_level_plsql.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_AlterRoutinesTSQL() throws Exception {
        verify("alter_routines_tsql.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_AlterRoutinesPostgreSQL() throws Exception {
        verify("alter_routines_postgresql.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_AlterRoutinesMySQL() throws Exception {
        verify("alter_routines_mysql.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_TSqlRoutineBodies() throws Exception {
        verify("tsql_routine_bodies.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_PLSQLRoutineVariants() throws Exception {
        verify("plsql_routine_variants.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_PLSQLPackages() throws Exception {
        verify("plsql_packages.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_TriggersTSQL() throws Exception {
        verify("triggers_tsql.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_TriggersPLSQL() throws Exception {
        verify("triggers_plsql.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_TriggersPostgreSQL() throws Exception {
        verify("triggers_postgresql.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_TriggersMySQL() throws Exception {
        verify("triggers_mysql.sql", Language.SQL);
    }

    @Test
    void analyze_SQL_Sample_hideColumns() throws Exception {
        CodeframeConfig config = CodeframeConfig.with(20000, true);
        Analysis analysis = App.analyzeFile(
                Paths.get(SAMPLES_BASE_PATH + "sample.sql"),
                Language.SQL,
                config);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }

    private static void verify(String fileName, Language language) throws Exception {
        // Initialize analyzers for tests
        App.initAnalyzersForTestsFromPaths(List.of());
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        Analysis analysis = App.analyzeFile(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
