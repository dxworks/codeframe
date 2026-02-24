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

    @Test
    void analyze_JavaScript_Sample() throws IOException {
        verify(Paths.get("src/test/resources/samples/javascript/sample.js"), Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_ResourceWrapper() throws IOException {
        verify(Paths.get("src/test/resources/samples/javascript/resourceLocationWrapper.js"), Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_ModernClassFeatures() throws IOException {
        verify(Paths.get("src/test/resources/samples/javascript/ModernClassFeatures.js"), Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_EnumsAndConstants() throws IOException {
        verify(Paths.get("src/test/resources/samples/javascript/EnumsAndConstants.js"), Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_DestructuringAndSpread() throws IOException {
        verify(Paths.get("src/test/resources/samples/javascript/DestructuringAndSpread.js"), Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_GeneratorsAndIterators() throws IOException {
        verify(Paths.get("src/test/resources/samples/javascript/GeneratorsAndIterators.js"), Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_DynamicImportsAndModules() throws IOException {
        verify(Paths.get("src/test/resources/samples/javascript/DynamicImportsAndModules.js"), Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_ClassicPatterns() throws IOException {
        verify(Paths.get("src/test/resources/samples/javascript/ClassicPatterns.js"), Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_ModernSyntax() throws IOException {
        verify(Paths.get("src/test/resources/samples/javascript/ModernSyntax.js"), Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_TestFile() throws IOException {
        verify(Paths.get("src/test/resources/samples/javascript/example.test.js"), Language.JAVASCRIPT);
    }

    private static void verify(Path filePath, Language language) throws IOException {
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
