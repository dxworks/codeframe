package org.dxworks.codeframe.analyzer.xml;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.FileAnalyzer;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.TestUtils;
import org.dxworks.codeframe.model.Analysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class XmlAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/xml/";
    private static final FileAnalyzer ANALYZER = TestUtils.defaultFileAnalyzer();

    @Test
    void analyze_Simple() throws IOException {
        verify("simple.xml");
    }

    @Test
    void analyze_Soap() throws IOException {
        verify("soap.xml");
    }

    @Test
    void analyze_Fragment() throws IOException {
        verify("fragment.xml");
    }

    @Test
    void analyze_Malformed() throws IOException {
        verify("malformed.xml");
    }

    @Test
    void analyze_DefaultNamespace() throws IOException {
        verify("default_namespace.xml");
    }

    @Test
    void analyze_CommentsPisCdata() throws IOException {
        verify("comments_pis_cdata.xml");
    }

    private static void verify(String fileName) throws IOException {
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        Analysis analysis = ANALYZER.analyze(filePath, Language.XML);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
