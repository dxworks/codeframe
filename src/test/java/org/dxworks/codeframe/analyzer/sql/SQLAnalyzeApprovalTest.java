package org.dxworks.codeframe.analyzer.sql;

import org.approvaltests.Approvals;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.dxworks.codeframe.App;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.CodeframeConfig;
import org.dxworks.codeframe.model.Analysis;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

public class SQLAnalyzeApprovalTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Test
    void analyze_SQL_Sample() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/sample.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_AlterRoutinesPLSQL() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/plsql_routine_bodies.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_ConstraintsAndIndexes() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/constraints_and_indexes.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_AlterTablesViews() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/alter_tables_views.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_DropStatements() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/drop_statements.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_ProceduresFunctions() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/procedures_functions.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_FunctionsCrossDialects() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/functions_crossdialects.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_TopLevelStatements() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/top_level_statements.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_TopLevelStatementsMySQL() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/top_level_mysql.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_TopLevelStatementsTSQL() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/top_level_tsql.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_TopLevelStatementsPLSQL() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/top_level_plsql.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_AlterRoutinesTSQL() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/alter_routines_tsql.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_AlterRoutinesPostgreSQL() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/alter_routines_postgresql.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_AlterRoutinesMySQL() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/alter_routines_mysql.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_TSqlRoutineBodies() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/tsql_routine_bodies.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_PLSQLRoutineVariants() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/plsql_routine_variants.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_PLSQLPackages() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/plsql_packages.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_TriggersTSQL() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/triggers_tsql.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_TriggersPLSQL() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/triggers_plsql.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_TriggersPostgreSQL() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/triggers_postgresql.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_TriggersMySQL() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/triggers_mysql.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_Sample_hideColumns() throws Exception {
        CodeframeConfig config = CodeframeConfig.with(20000, true);
        Analysis analysis = App.analyzeFile(
                Paths.get("src/test/resources/samples/sql/sample.sql"),
                Language.SQL,
                config);
        Approvals.verify(MAPPER.writeValueAsString(analysis));
    }

    private static void verify(java.nio.file.Path file, Language language) throws Exception {
        Analysis analysis = App.analyzeFile(file, language);
        Approvals.verify(MAPPER.writeValueAsString(analysis));
    }
}
