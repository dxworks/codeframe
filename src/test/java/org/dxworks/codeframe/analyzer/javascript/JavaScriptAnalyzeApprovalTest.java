package org.dxworks.codeframe.analyzer.javascript;

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

public class JavaScriptAnalyzeApprovalTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

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

    private static void verify(Path file, Language language) throws IOException {
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(file, language);
        Approvals.verify(MAPPER.writeValueAsString(analysis));
    }
}
