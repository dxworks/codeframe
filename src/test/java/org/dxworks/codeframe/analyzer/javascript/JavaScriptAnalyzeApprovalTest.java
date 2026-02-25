package org.dxworks.codeframe.analyzer.javascript;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.App;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.model.FileAnalysis;
import org.dxworks.codeframe.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JavaScriptAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/javascript/";

    @Test
    void analyze_JavaScript_Sample() throws IOException {
        verify("sample.js", Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_ResourceWrapper() throws IOException {
        verify("resourceLocationWrapper.js", Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_ModernClassFeatures() throws IOException {
        verify("ModernClassFeatures.js", Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_EnumsAndConstants() throws IOException {
        verify("EnumsAndConstants.js", Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_DestructuringAndSpread() throws IOException {
        verify("DestructuringAndSpread.js", Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_GeneratorsAndIterators() throws IOException {
        verify("GeneratorsAndIterators.js", Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_DynamicImportsAndModules() throws IOException {
        verify("DynamicImportsAndModules.js", Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_ClassicPatterns() throws IOException {
        verify("ClassicPatterns.js", Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_ModernSyntax() throws IOException {
        verify("ModernSyntax.js", Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_TestFile() throws IOException {
        verify("example.test.js", Language.JAVASCRIPT);
    }

    private static void verify(String fileName, Language language) throws IOException {
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
