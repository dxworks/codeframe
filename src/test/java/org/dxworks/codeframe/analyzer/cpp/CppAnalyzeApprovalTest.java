package org.dxworks.codeframe.analyzer.cpp;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.App;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.TestUtils;
import org.dxworks.codeframe.model.FileAnalysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CppAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/cpp/";

    @Test
    void analyze_Cpp_ClassesAndInheritance() throws IOException {
        verify("ClassesAndInheritance.cpp", Language.CPP);
    }

    @Test
    void analyze_Cpp_Templates() throws IOException {
        verify("Templates.cpp", Language.CPP);
    }

    @Test
    void analyze_Cpp_Namespaces() throws IOException {
        verify("Namespaces.cpp", Language.CPP);
    }

    @Test
    void analyze_Cpp_CallInferenceFromParamsAndGlobals() throws IOException {
        verify("CallInferenceFromParamsAndGlobals.cpp", Language.CPP);
    }

    @Test
    void analyze_Cpp_TypeCoverageAndWrappedDeclaration() throws IOException {
        verify("TypeCoverageAndWrappedDeclaration.cpp", Language.CPP);
    }

    @Test
    void analyze_Cpp_CommonHeaderFeatures() throws IOException {
        verify("CommonHeaderFeatures.h", Language.CPP);
    }

    @Test
    void analyze_Cpp_MacroHeavyHeader() throws IOException {
        verify("MacroHeavyHeader.h", Language.CPP);
    }

    private static void verify(String fileName, Language language) throws IOException {
        App.initAnalyzersForTestsFromPaths(List.of());
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
