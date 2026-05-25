package org.dxworks.codeframe.analyzer.yaml;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.FileAnalyzer;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.TestUtils;
import org.dxworks.codeframe.model.Analysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class YamlAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/yaml/";
    private static final FileAnalyzer ANALYZER = TestUtils.defaultFileAnalyzer();

    @Test
    void analyze_Generic() throws IOException {
        verify("generic.yaml");
    }

    @Test
    void analyze_StreamReuse() throws IOException {
        verify("stream_reuse.yaml");
    }

    @Test
    void analyze_OpenApiCore() throws IOException {
        verify("openapi_core.yaml");
    }

    @Test
    void analyze_OpenApiContractShape() throws IOException {
        verify("openapi_contract_shape.yaml");
    }

    @Test
    void analyze_OpenApiIntegration() throws IOException {
        verify("openapi_integration.yaml");
    }

    @Test
    void analyze_RootOnlyDetection() throws IOException {
        verify("root_only_detection.yaml");
    }

    @Test
    void analyze_Malformed() throws IOException {
        verifyFailure("malformed.yaml");
    }

    private static void verify(String fileName) throws IOException {
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        Analysis analysis = ANALYZER.analyze(filePath, Language.YAML);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }

    private static void verifyFailure(String fileName) {
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ANALYZER.analyze(filePath, Language.YAML));
        Approvals.verify(exception.getClass().getSimpleName() + ": " + exception.getMessage());
    }
}
