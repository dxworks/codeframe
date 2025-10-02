package org.dxworks.codeframe.analyzer;

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

public class AppAnalyzeFileApprovalTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    // ---- Java ----
    @Test
    void analyze_Java_Sample() throws IOException {
        verify(Paths.get("src/test/resources/samples/java/Sample.java"), Language.JAVA);
    }

    // ---- JavaScript ----
    @Test
    void analyze_JavaScript_Sample() throws IOException {
        verify(Paths.get("src/test/resources/samples/javascript/sample.js"), Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_ResourceWrapper() throws IOException {
        verify(Paths.get("src/test/resources/samples/javascript/resourceLocationWrapper.js"), Language.JAVASCRIPT);
    }

    // ---- TypeScript / TSX ----
    @Test
    void analyze_TypeScript_Sample() throws IOException {
        verify(Paths.get("src/test/resources/samples/typescript/sample.ts"), Language.TYPESCRIPT);
    }

    @Test
    void analyze_Tsx_ConfirmDiscardPrompt() throws IOException {
        verify(Paths.get("src/test/resources/samples/typescript/confirm-discard-prompt.tsx"), Language.TYPESCRIPT);
    }

    // ---- C# ----
    @Test
    void analyze_CSharp_DataClass() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/DataClass.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_DataClassNS() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/DataClassNS.cs"), Language.CSHARP);
    }
    
    // ---- Python ----
    @Test
    void analyze_Python_ComplexExample() throws IOException {
        verify(Paths.get("src/test/resources/samples/python/complex_example.py"), Language.PYTHON);
    }

    // ---- PHP ----
    @Test
    void analyze_Php_UserService() throws IOException {
        verify(Paths.get("src/test/resources/samples/php/UserService.php"), Language.PHP);
    }

    private static void verify(Path file, Language language) throws IOException {
        FileAnalysis analysis = App.analyzeFile(file, language);
        Approvals.verify(MAPPER.writeValueAsString(analysis));
    }
}
