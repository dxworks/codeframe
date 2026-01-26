package org.dxworks.codeframe.analyzer.rust;

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

public class RustAnalyzeApprovalTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Test
    void analyze_Rust_Sample() throws IOException {
        verify(Paths.get("src/test/resources/samples/rust/Sample.rs"), Language.RUST);
    }

    @Test
    void analyze_Rust_StructsAndImpls() throws IOException {
        verify(Paths.get("src/test/resources/samples/rust/StructsAndImpls.rs"), Language.RUST);
    }

    @Test
    void analyze_Rust_TraitsAndGenerics() throws IOException {
        verify(Paths.get("src/test/resources/samples/rust/TraitsAndGenerics.rs"), Language.RUST);
    }

    @Test
    void analyze_Rust_EnumsAndPatterns() throws IOException {
        verify(Paths.get("src/test/resources/samples/rust/EnumsAndPatterns.rs"), Language.RUST);
    }

    @Test
    void analyze_Rust_ModulesAndVisibility() throws IOException {
        verify(Paths.get("src/test/resources/samples/rust/ModulesAndVisibility.rs"), Language.RUST);
    }

    private static void verify(Path file, Language language) throws IOException {
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(file, language);
        Approvals.verify(MAPPER.writeValueAsString(analysis));
    }
}
