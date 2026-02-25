package org.dxworks.codeframe.analyzer.python;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.App;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.TestUtils;
import org.dxworks.codeframe.model.FileAnalysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PythonAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/python/";

    @Test
    void analyze_Python_ComplexExample() throws IOException {
        verify("complex_example.py", Language.PYTHON);
    }

    @Test
    void analyze_Python_DecoratorsSample() throws IOException {
        verify("decorators_sample.py", Language.PYTHON);
    }

    @Test
    void analyze_Python_EnumSample() throws IOException {
        verify("enum_sample.py", Language.PYTHON);
    }

    @Test
    void analyze_Python_ExceptionHandlingSample() throws IOException {
        verify("exception_handling_sample.py", Language.PYTHON);
    }

    @Test
    void analyze_Python_InheritanceSample() throws IOException {
        verify("inheritance_sample.py", Language.PYTHON);
    }

    @Test
    void analyze_Python_NestedStructuresSample() throws IOException {
        verify("nested_structures_sample.py", Language.PYTHON);
    }

    @Test
    void analyze_Python_TypingSample() throws IOException {
        verify("typing_sample.py", Language.PYTHON);
    }

    private static void verify(String fileName, Language language) throws IOException {
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
