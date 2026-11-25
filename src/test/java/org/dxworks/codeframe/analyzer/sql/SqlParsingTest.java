package org.dxworks.codeframe.analyzer.sql;

import net.sf.jsqlparser.JSQLParserException;
import org.dxworks.codeframe.CodeframeConfig;
import org.dxworks.codeframe.model.sql.SQLFileAnalysis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlParsingTest {

    // Fails the simple parse; nesting > 10 makes JSqlParser skip the complex retry and return null
    private static final String UNPARSEABLE_DEEPLY_NESTED_SQL = """
            GRANT ALL PRIVILEGES ON db.* TO pc@localhost IDENTIFIED BY 'pc';
            SELECT ((((((((((((v)))))))))))) FROM t;
            """;

    // T-SQL that JSqlParser rejects, nested deeply enough to skip the complex retry
    private static final String DEEPLY_NESTED_TSQL_PROCEDURE = """
            CREATE PROCEDURE dbo.DeepCalc
                @x INT
            AS
            BEGIN
                SET NOCOUNT ON;
                SELECT ((((((((((((@x)))))))))))) AS v;
            END
            GO
            """;

    @Test
    void emptyInputGivesNoStatements() throws Exception {
        assertTrue(SqlParsing.parseStatements("", null).isEmpty());
        assertTrue(SqlParsing.parseStatements(null, null).isEmpty());
    }

    @Test
    void skippedComplexRetryIsAParseError() {
        assertThrows(JSQLParserException.class,
                () -> SqlParsing.parseStatements(UNPARSEABLE_DEEPLY_NESTED_SQL, p -> p.withAllowComplexParsing(true)));
    }

    @Test
    void analyzerFallsBackInsteadOfFailingOnDeeplyNestedSql() {
        SQLAnalyzer analyzer = new SQLAnalyzer(CodeframeConfig.load());
        SQLFileAnalysis out = (SQLFileAnalysis) assertDoesNotThrow(
                () -> analyzer.analyze("deep.sql", UNPARSEABLE_DEEPLY_NESTED_SQL));
        assertEquals("deep.sql", out.filePath);
    }

    @Test
    void analyzerHandlesGoOnlyFile() {
        SQLAnalyzer analyzer = new SQLAnalyzer(CodeframeConfig.load());
        assertDoesNotThrow(() -> analyzer.analyze("empty.sql", "GO\nGO\n"));
    }

    @Test
    void deeplyNestedTsqlProcedureIsExtractedByFallback() {
        SQLAnalyzer analyzer = new SQLAnalyzer(CodeframeConfig.load());
        SQLFileAnalysis out = (SQLFileAnalysis) assertDoesNotThrow(
                () -> analyzer.analyze("deep_proc.sql", DEEPLY_NESTED_TSQL_PROCEDURE));
        assertEquals(1, out.createProcedures.size());
        assertTrue(out.createProcedures.get(0).procedureName.contains("DeepCalc"));
    }
}
