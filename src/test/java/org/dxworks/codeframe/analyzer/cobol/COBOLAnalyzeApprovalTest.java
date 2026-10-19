package org.dxworks.codeframe.analyzer.cobol;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.CodeframeConfig;
import org.dxworks.codeframe.FileAnalyzer;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.model.Analysis;
import org.dxworks.codeframe.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

public class COBOLAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/cobol/";
    private static final FileAnalyzer ANALYZER = TestUtils.defaultFileAnalyzer();

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
        List<File> copybookFiles = List.of(
            Paths.get(SAMPLES_BASE_PATH + "SIMPLE.cpy").toFile(),
            Paths.get(SAMPLES_BASE_PATH + "PROCEDURES.cpy").toFile()
        );
        FileAnalyzer copybookAnalyzer = new FileAnalyzer(
                CodeframeConfig.load(),
                new CobolCopybookRepository(copybookFiles));
        Analysis analysis = copybookAnalyzer.analyze(
                Paths.get(SAMPLES_BASE_PATH + "copybook-test.cbl"),
                Language.COBOL);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }

    private static void verifyWithEmptyCopybooks(String filePath) throws Exception {
        Analysis analysis = ANALYZER.analyze(Paths.get(SAMPLES_BASE_PATH + filePath), Language.COBOL);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
