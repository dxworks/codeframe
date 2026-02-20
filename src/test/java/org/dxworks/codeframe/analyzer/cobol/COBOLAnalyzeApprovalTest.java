package org.dxworks.codeframe.analyzer.cobol;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.App;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.model.Analysis;
import org.dxworks.codeframe.TestUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class COBOLAnalyzeApprovalTest {

    @Test
    void analyze_COBOL_BasicProgram() throws Exception {
        verifyWithEmptyCopybooks("src/test/resources/samples/cobol/basic-program.cbl");
    }

    @Test
    void analyze_COBOL_Sections() throws Exception {
        verifyWithEmptyCopybooks("src/test/resources/samples/cobol/sections.cbl");
    }

    @Test
    void analyze_COBOL_ProcedurePrologue() throws Exception {
        verifyWithEmptyCopybooks("src/test/resources/samples/cobol/procedure-prologue.cbl");
    }

    @Test
    void analyze_COBOL_FileOperations() throws Exception {
        verifyWithEmptyCopybooks("src/test/resources/samples/cobol/file-operations.cbl");
    }

    @Test
    void analyze_COBOL_CopybookExpansion() throws Exception {
        // Initialize analyzers with copybooks using the new convenience method
        List<Path> copybookPaths = List.of(
            Paths.get("src/test/resources/samples/cobol/SIMPLE.cpy"),
            Paths.get("src/test/resources/samples/cobol/PROCEDURES.cpy")
        );
        App.initAnalyzersForTestsFromPaths(copybookPaths);
        
        // Now use the standard verify method since analyzers are initialized
        verify("src/test/resources/samples/cobol/copybook-test.cbl");
    }

    private static void verify(String filePath) throws Exception {
        Analysis analysis = App.analyzeFile(Paths.get(filePath), Language.COBOL);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }

    private static void verifyWithEmptyCopybooks(String filePath) throws Exception {
        // Initialize analyzers for COBOL tests (no copybooks needed for basic tests)
        App.initAnalyzersForTestsFromPaths(List.of());
        Analysis analysis = App.analyzeFile(Paths.get(filePath), Language.COBOL);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
