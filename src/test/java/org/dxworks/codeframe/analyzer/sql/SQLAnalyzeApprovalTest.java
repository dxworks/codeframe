package org.dxworks.codeframe.analyzer.sql;

import org.approvaltests.Approvals;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.dxworks.codeframe.App;
import org.dxworks.codeframe.Language;
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
    void analyze_SQL_ConstraintsAndIndexes() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/constraints_and_indexes.sql"), Language.SQL);
    }

    @Test
    void analyze_SQL_AlterTable() throws Exception {
        verify(Paths.get("src/test/resources/samples/sql/alter_table.sql"), Language.SQL);
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

    private static void verify(java.nio.file.Path file, Language language) throws Exception {
        Analysis analysis = App.analyzeFile(file, language);
        Approvals.verify(MAPPER.writeValueAsString(analysis));
    }
}
