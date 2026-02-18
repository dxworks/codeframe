package org.dxworks.codeframe.analyzer.cobol;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.App;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.model.Analysis;
import org.dxworks.codeframe.TestUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

public class COBOLAnalyzeApprovalTest {

    @Test
    void analyze_COBOL_BasicProgram() throws Exception {
        verify("src/test/resources/samples/cobol/basic-program.cbl");
    }

    @Test
    void analyze_COBOL_Sections() throws Exception {
        verify("src/test/resources/samples/cobol/sections.cbl");
    }

    @Test
    void analyze_COBOL_ProcedurePrologue() throws Exception {
        verify("src/test/resources/samples/cobol/procedure-prologue.cbl");
    }

    @Test
    void analyze_COBOL_FileOperations() throws Exception {
        verify("src/test/resources/samples/cobol/file-operations.cbl");
    }

    private static void verify(String filePath) throws Exception {
        Analysis analysis = App.analyzeFile(Paths.get(filePath), Language.COBOL);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
