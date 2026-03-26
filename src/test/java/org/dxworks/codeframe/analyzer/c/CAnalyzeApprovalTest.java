package org.dxworks.codeframe.analyzer.c;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.App;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.TestUtils;
import org.dxworks.codeframe.model.FileAnalysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/c/";

    @Test
    void analyze_C_BasicFunctions() throws IOException {
        verify("BasicFunctions.c", Language.C);
    }

    @Test
    void analyze_C_TypeDeclarations() throws IOException {
        verify("TypeDeclarations.c", Language.C);
    }

    @Test
    void analyze_C_CallInferenceAndTopLevelCalls() throws IOException {
        verify("CallInferenceAndTopLevelCalls.c", Language.C);
    }

    @Test
    void analyze_C_BasicHeader() throws IOException {
        verify("BasicHeader.h", Language.C);
    }

    private static void verify(String fileName, Language language) throws IOException {
        App.initAnalyzersForTestsFromPaths(List.of());
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
