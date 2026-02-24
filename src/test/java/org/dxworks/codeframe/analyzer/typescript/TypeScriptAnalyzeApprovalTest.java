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

public class TypeScriptAnalyzeApprovalTest {

    @Test
    void analyze_TypeScript_Sample() throws IOException {
        verify(Paths.get("src/test/resources/samples/typescript/sample.ts"), Language.TYPESCRIPT);
    }

    @Test
    void analyze_Tsx_ConfirmDiscardPrompt() throws IOException {
        verify(Paths.get("src/test/resources/samples/typescript/confirm-discard-prompt.tsx"), Language.TYPESCRIPT);
    }

    @Test
    void analyze_TypeScript_OrderParams() throws IOException {
        verify(Paths.get("src/test/resources/samples/typescript/order-params.ts"), Language.TYPESCRIPT);
    }

    @Test
    void analyze_TypeScript_InterfaceSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/typescript/interface-sample.ts"), Language.TYPESCRIPT);
    }

    @Test
    void analyze_TypeScript_ClassInheritance() throws IOException {
        verify(Paths.get("src/test/resources/samples/typescript/ClassInheritance.ts"), Language.TYPESCRIPT);
    }

    @Test
    void analyze_TypeScript_TypeScriptTypes() throws IOException {
        verify(Paths.get("src/test/resources/samples/typescript/TypeScriptTypes.ts"), Language.TYPESCRIPT);
    }

    @Test
    void analyze_TypeScript_ClassFeatures() throws IOException {
        verify(Paths.get("src/test/resources/samples/typescript/ClassFeatures.ts"), Language.TYPESCRIPT);
    }

    private static void verify(Path file, Language language) throws IOException {
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(file, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
