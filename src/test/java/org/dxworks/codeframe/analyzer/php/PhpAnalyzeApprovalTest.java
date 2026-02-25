package org.dxworks.codeframe.analyzer.php;

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

public class PhpAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/php/";

    @Test
    void analyze_Php_UserService() throws IOException {
        verify("UserService.php", Language.PHP);
    }

    @Test
    void analyze_Php_OrderParams() throws IOException {
        verify("OrderParams.php", Language.PHP);
    }

    @Test
    void analyze_Php_InterfaceSample() throws IOException {
        verify("InterfaceSample.php", Language.PHP);
    }

    @Test
    void analyze_Php_TraitsSample() throws IOException {
        verify("TraitsSample.php", Language.PHP);
    }

    @Test
    void analyze_Php_EnumsSample() throws IOException {
        verify("EnumsSample.php", Language.PHP);
    }

    @Test
    void analyze_Php_ClosuresAndArrowFunctionsSample() throws IOException {
        verify("ClosuresAndArrowFunctionsSample.php", Language.PHP);
    }

    @Test
    void analyze_Php_InheritanceSample() throws IOException {
        verify("InheritanceSample.php", Language.PHP);
    }

    @Test
    void analyze_Php_ExceptionHandlingSample() throws IOException {
        verify("ExceptionHandlingSample.php", Language.PHP);
    }

    @Test
    void analyze_Php_MagicMethodsSample() throws IOException {
        verify("MagicMethodsSample.php", Language.PHP);
    }

    @Test
    void analyze_Php_ModernPhpSample() throws IOException {
        verify("ModernPhpSample.php", Language.PHP);
    }

    private static void verify(String fileName, Language language) throws IOException {
        // Initialize analyzers for tests
        App.initAnalyzersForTestsFromPaths(List.of());
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
