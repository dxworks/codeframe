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
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/cobol/";

    @Test
    void analyze_COBOL_BasicProgram() throws Exception {
        verifyWithEmptyCopybooks("basic-program.cbl");
    }

    @Test
    void analyze_COBOL_Sections() throws Exception {
        verifyWithEmptyCopybooks("sections.cbl");
    }

    @Test
    void analyze_COBOL_ProcedurePrologue() throws Exception {
        verifyWithEmptyCopybooks("procedure-prologue.cbl");
    }

    @Test
    void analyze_COBOL_FileOperations() throws Exception {
        verifyWithEmptyCopybooks("file-operations.cbl");
    }

    @Test
    void analyze_COBOL_CopybookExpansion() throws Exception {
        // Initialize analyzers with copybooks using the new convenience method
        List<Path> copybookPaths = List.of(
            Paths.get(SAMPLES_BASE_PATH + "SIMPLE.cpy"),
            Paths.get(SAMPLES_BASE_PATH + "PROCEDURES.cpy")
        );
        App.initAnalyzersForTestsFromPaths(copybookPaths);
        
        // Now use the standard verify method since analyzers are initialized
        verify("copybook-test.cbl");
    }

    private static void verify(String filePath) throws Exception {
        Analysis analysis = App.analyzeFile(Paths.get(SAMPLES_BASE_PATH + filePath), Language.COBOL);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }

    private static void verifyWithEmptyCopybooks(String filePath) throws Exception {
        // Initialize analyzers for COBOL tests (no copybooks needed for basic tests)
        App.initAnalyzersForTestsFromPaths(List.of());
        Analysis analysis = App.analyzeFile(Paths.get(SAMPLES_BASE_PATH + filePath), Language.COBOL);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
