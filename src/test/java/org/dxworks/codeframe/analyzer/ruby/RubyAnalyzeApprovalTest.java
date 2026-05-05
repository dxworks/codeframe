package org.dxworks.codeframe.analyzer.ruby;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.FileAnalyzer;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.TestUtils;
import org.dxworks.codeframe.model.FileAnalysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RubyAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/ruby/";
    private static final FileAnalyzer ANALYZER = TestUtils.defaultFileAnalyzer();

    @Test
    void analyze_Ruby_Sample() throws IOException {
        verify("Sample.rb", Language.RUBY);
    }

    @Test
    void analyze_Ruby_ModuleSample() throws IOException {
        verify("ModuleSample.rb", Language.RUBY);
    }

    @Test
    void analyze_Ruby_InheritanceSample() throws IOException {
        verify("InheritanceSample.rb", Language.RUBY);
    }

    @Test
    void analyze_Ruby_VisibilityAndClassMethodsSample() throws IOException {
        verify("VisibilityAndClassMethodsSample.rb", Language.RUBY);
    }

    @Test
    void analyze_Ruby_ConstantsAndRequiresSample() throws IOException {
        verify("ConstantsAndRequiresSample.rb", Language.RUBY);
    }

    @Test
    void analyze_Ruby_ActiveRecordSample() throws IOException {
        verify("ActiveRecordSample.rb", Language.RUBY);
    }
    
    @Test
    void analyze_Ruby_NestedTypesSample() throws IOException {
        verify("NestedTypesSample.rb", Language.RUBY);
    }

    @Test
    void analyze_Ruby_BlocksAndLambdasSample() throws IOException {
        verify("BlocksAndLambdasSample.rb", Language.RUBY);
    }

    private static void verify(String fileName, Language language) throws IOException {
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        FileAnalysis analysis = (FileAnalysis) ANALYZER.analyze(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
