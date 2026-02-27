package org.dxworks.codeframe.analyzer.markdown;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.App;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.TestUtils;
import org.dxworks.codeframe.model.Analysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class MarkdownAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/markdown/";

    @Test
    void analyze_Basic() throws IOException {
        verify("Basic.md", Language.MARKDOWN);
    }

    @Test
    void analyze_Bold_headers() throws IOException {
        verify("Bold_headers.md", Language.MARKDOWN);
    }

    private static void verify(String fileName, Language language) throws IOException {
        App.initAnalyzersForTestsFromPaths(List.of());
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        Analysis analysis = App.analyzeFile(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
