package org.dxworks.codeframe.analyzer.markdown;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.FileAnalyzer;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.TestUtils;
import org.dxworks.codeframe.model.Analysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarkdownAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/markdown/";
    private static final FileAnalyzer ANALYZER = TestUtils.defaultFileAnalyzer();

    @Test
    void analyze_Basic() throws IOException {
        verify("Basic.md", Language.MARKDOWN);
    }

    @Test
    void analyze_Bold_headers() throws IOException {
        verify("Bold_headers.md", Language.MARKDOWN);
    }

    @Test
    void analyze_Links() throws IOException {
        verify("Links.md", Language.MARKDOWN);
    }

    private static void verify(String fileName, Language language) throws IOException {
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        Analysis analysis = ANALYZER.analyze(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
