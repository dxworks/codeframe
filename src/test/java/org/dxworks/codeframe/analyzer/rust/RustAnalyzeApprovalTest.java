package org.dxworks.codeframe.analyzer.rust;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.FileAnalyzer;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.TestUtils;
import org.dxworks.codeframe.model.FileAnalysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RustAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/rust/";
    private static final FileAnalyzer ANALYZER = TestUtils.defaultFileAnalyzer();

    @Test
    void analyze_Rust_Sample() throws IOException {
        verify("Sample.rs", Language.RUST);
    }

    @Test
    void analyze_Rust_StructsAndImpls() throws IOException {
        verify("StructsAndImpls.rs", Language.RUST);
    }

    @Test
    void analyze_Rust_TraitsAndGenerics() throws IOException {
        verify("TraitsAndGenerics.rs", Language.RUST);
    }

    @Test
    void analyze_Rust_EnumsAndPatterns() throws IOException {
        verify("EnumsAndPatterns.rs", Language.RUST);
    }

    @Test
    void analyze_Rust_ModulesAndVisibility() throws IOException {
        verify("ModulesAndVisibility.rs", Language.RUST);
    }

    private static void verify(String fileName, Language language) throws IOException {
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        FileAnalysis analysis = (FileAnalysis) ANALYZER.analyze(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
