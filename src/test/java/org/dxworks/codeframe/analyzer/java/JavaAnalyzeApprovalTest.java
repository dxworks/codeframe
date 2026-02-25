package org.dxworks.codeframe.analyzer.java;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.App;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.model.FileAnalysis;
import org.dxworks.codeframe.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class JavaAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/java/";

    @Test
    void analyze_Java_Sample() throws IOException {
        verify("Sample.java", Language.JAVA);
    }

    @Test
    void analyze_Java_MultipleClasses() throws IOException {
        verify("MultipleClasses.java", Language.JAVA);
    }

    @Test
    void analyze_Java_RepositorySample() throws IOException {
        verify("Repository.java", Language.JAVA);
    }

    @Test
    void analyze_Java_ConstructorSample() throws IOException {
        verify("ConstructorSample.java", Language.JAVA);
    }

    @Test
    void analyze_Java_LambdaSample() throws IOException {
        verify("LambdaSample.java", Language.JAVA);
    }

    @Test
    void analyze_Java_ExceptionHandlingSample() throws IOException {
        verify("ExceptionHandlingSample.java", Language.JAVA);
    }

    @Test
    void analyze_Java_EnumSample() throws IOException {
        verify("EnumSample.java", Language.JAVA);
    }

    @Test
    void analyze_Java_AnonymousInnerClassesSample() throws IOException {
        verify("AnonymousInnerClassesSample.java", Language.JAVA);
    }

    @Test
    void analyze_Java_GenericsSample() throws IOException {
        verify("GenericsSample.java", Language.JAVA);
    }

    @Test
    void analyze_Java_RecordsSample() throws IOException {
        verify("RecordsSample.java", Language.JAVA);
    }

    @Test
    void analyze_Java_SealedClassesSample() throws IOException {
        verify("SealedClassesSample.java", Language.JAVA);
    }

    private static void verify(String fileName, Language language) throws IOException {
        // Initialize analyzers for tests
        App.initAnalyzersForTestsFromPaths(List.of());
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
