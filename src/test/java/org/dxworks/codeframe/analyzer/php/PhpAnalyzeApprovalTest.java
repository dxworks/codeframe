package org.dxworks.codeframe.analyzer.php;

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

public class PhpAnalyzeApprovalTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Test
    void analyze_Php_UserService() throws IOException {
        verify(Paths.get("src/test/resources/samples/php/UserService.php"), Language.PHP);
    }

    @Test
    void analyze_Php_OrderParams() throws IOException {
        verify(Paths.get("src/test/resources/samples/php/OrderParams.php"), Language.PHP);
    }

    @Test
    void analyze_Php_InterfaceSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/php/InterfaceSample.php"), Language.PHP);
    }

    private static void verify(Path file, Language language) throws IOException {
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(file, language);
        Approvals.verify(MAPPER.writeValueAsString(analysis));
    }
}
