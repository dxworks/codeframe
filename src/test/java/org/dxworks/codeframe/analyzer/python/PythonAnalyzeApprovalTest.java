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

    @Test
    void analyze_Python_ComplexExample() throws IOException {
        verify(Paths.get("src/test/resources/samples/python/complex_example.py"), Language.PYTHON);
    }

    @Test
    void analyze_Python_DecoratorsSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/python/decorators_sample.py"), Language.PYTHON);
    }

    @Test
    void analyze_Python_EnumSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/python/enum_sample.py"), Language.PYTHON);
    }

    @Test
    void analyze_Python_ExceptionHandlingSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/python/exception_handling_sample.py"), Language.PYTHON);
    }

    @Test
    void analyze_Python_InheritanceSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/python/inheritance_sample.py"), Language.PYTHON);
    }

    @Test
    void analyze_Python_NestedStructuresSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/python/nested_structures_sample.py"), Language.PYTHON);
    }

    @Test
    void analyze_Python_TypingSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/python/typing_sample.py"), Language.PYTHON);
    }

    private static void verify(Path file, Language language) throws IOException {
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(file, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
