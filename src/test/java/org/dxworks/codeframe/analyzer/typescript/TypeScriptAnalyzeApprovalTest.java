package org.dxworks.codeframe.analyzer.typescript;

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

public class TypeScriptAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/typescript/";

    @Test
    void analyze_TypeScript_Sample() throws IOException {
        verify("sample.ts", Language.TYPESCRIPT);
    }

    @Test
    void analyze_Tsx_ConfirmDiscardPrompt() throws IOException {
        verify("confirm-discard-prompt.tsx", Language.TYPESCRIPT);
    }

    @Test
    void analyze_TypeScript_OrderParams() throws IOException {
        verify("order-params.ts", Language.TYPESCRIPT);
    }

    @Test
    void analyze_TypeScript_InterfaceSample() throws IOException {
        verify("interface-sample.ts", Language.TYPESCRIPT);
    }

    @Test
    void analyze_TypeScript_ClassInheritance() throws IOException {
        verify("ClassInheritance.ts", Language.TYPESCRIPT);
    }

    @Test
    void analyze_TypeScript_TypeScriptTypes() throws IOException {
        verify("TypeScriptTypes.ts", Language.TYPESCRIPT);
    }

    @Test
    void analyze_TypeScript_ClassFeatures() throws IOException {
        verify("ClassFeatures.ts", Language.TYPESCRIPT);
    }

    private static void verify(String fileName, Language language) throws IOException {
        // Initialize analyzers for tests
        App.initAnalyzersForTestsFromPaths(List.of());
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
