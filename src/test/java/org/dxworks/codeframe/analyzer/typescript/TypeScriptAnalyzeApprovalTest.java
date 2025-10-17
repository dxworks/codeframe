package org.dxworks.codeframe.analyzer.typescript;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.approvaltests.Approvals;
import org.dxworks.codeframe.App;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.model.FileAnalysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TypeScriptAnalyzeApprovalTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

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

    private static void verify(Path file, Language language) throws IOException {
        FileAnalysis analysis = App.analyzeFile(file, language);
        Approvals.verify(MAPPER.writeValueAsString(analysis));
    }
}
