package org.dxworks.codeframe;

import org.dxworks.codeframe.model.Analysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AppEncodingTest {

    @Test
    void analyzeFile_shouldHandleWindows1252EncodedFile() throws IOException {
        Path tempFile = Files.createTempFile("codeframe-nonutf8-", ".h");
        try {
            String sourceCode = "// cp1252 marker: ©\nclass EncodingProbe { public: int value; };";
            Files.writeString(tempFile, sourceCode, Charset.forName("windows-1252"));

            App.initAnalyzersForTestsFromPaths(List.of());

            Analysis analysis = assertDoesNotThrow(() -> App.analyzeFile(tempFile, Language.CPP));
            assertNotNull(analysis);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
