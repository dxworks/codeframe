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

public class PhpAnalyzeApprovalTest {

    @Test
    void analyze_Php_UserService() throws IOException {
        verify(Paths.get("src/test/resources/samples/php/UserService.php"), Language.PHP);
    }

    @Test
    void analyze_Php_OrderParams() throws IOException {
        verify(Paths.get("src/test/resources/samples/php/OrderParams.php"), Language.PHP);
    }

    @Test
    void analyze_Php_InterfaceSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/php/InterfaceSample.php"), Language.PHP);
    }

    @Test
    void analyze_Php_TraitsSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/php/TraitsSample.php"), Language.PHP);
    }

    @Test
    void analyze_Php_EnumsSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/php/EnumsSample.php"), Language.PHP);
    }

    @Test
    void analyze_Php_ClosuresAndArrowFunctionsSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/php/ClosuresAndArrowFunctionsSample.php"), Language.PHP);
    }

    @Test
    void analyze_Php_InheritanceSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/php/InheritanceSample.php"), Language.PHP);
    }

    @Test
    void analyze_Php_ExceptionHandlingSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/php/ExceptionHandlingSample.php"), Language.PHP);
    }

    @Test
    void analyze_Php_MagicMethodsSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/php/MagicMethodsSample.php"), Language.PHP);
    }

    @Test
    void analyze_Php_ModernPhpSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/php/ModernPhpSample.php"), Language.PHP);
    }

    private static void verify(Path file, Language language) throws IOException {
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(file, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
