package org.dxworks.codeframe.analyzer.java;

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

public class JavaAnalyzeApprovalTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Test
    void analyze_Java_Sample() throws IOException {
        verify(Paths.get("src/test/resources/samples/java/Sample.java"), Language.JAVA);
    }

    @Test
    void analyze_Java_MultipleClasses() throws IOException {
        verify(Paths.get("src/test/resources/samples/java/MultipleClasses.java"), Language.JAVA);
    }

    @Test
    void analyze_Java_InterfaceSample() throws IOException {
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

    private static void verify(Path file, Language language) throws IOException {
        FileAnalysis analysis = App.analyzeFile(file, language);
        Approvals.verify(MAPPER.writeValueAsString(analysis));
    }
}
