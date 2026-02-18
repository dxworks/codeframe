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

public class JavaAnalyzeApprovalTest {

    @Test
    void analyze_Java_Sample() throws IOException {
        verify(Paths.get("src/test/resources/samples/java/Sample.java"), Language.JAVA);
    }

    @Test
    void analyze_Java_MultipleClasses() throws IOException {
        verify(Paths.get("src/test/resources/samples/java/MultipleClasses.java"), Language.JAVA);
    }

    @Test
    void analyze_Java_RepositorySample() throws IOException {
        verify(Paths.get("src/test/resources/samples/java/Repository.java"), Language.JAVA);
    }

    @Test
    void analyze_Java_ConstructorSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/java/ConstructorSample.java"), Language.JAVA);
    }

    @Test
    void analyze_Java_LambdaSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/java/LambdaSample.java"), Language.JAVA);
    }

    @Test
    void analyze_Java_ExceptionHandlingSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/java/ExceptionHandlingSample.java"), Language.JAVA);
    }

    @Test
    void analyze_Java_EnumSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/java/EnumSample.java"), Language.JAVA);
    }

    @Test
    void analyze_Java_AnonymousInnerClassesSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/java/AnonymousInnerClassesSample.java"), Language.JAVA);
    }

    @Test
    void analyze_Java_GenericsSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/java/GenericsSample.java"), Language.JAVA);
    }

    @Test
    void analyze_Java_RecordsSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/java/RecordsSample.java"), Language.JAVA);
    }

    @Test
    void analyze_Java_SealedClassesSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/java/SealedClassesSample.java"), Language.JAVA);
    }

    private static void verify(Path filePath, Language language) throws IOException {
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
