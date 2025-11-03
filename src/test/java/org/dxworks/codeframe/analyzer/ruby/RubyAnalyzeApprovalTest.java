package org.dxworks.codeframe.analyzer.ruby;

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

public class RubyAnalyzeApprovalTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Test
    void analyze_Ruby_Sample() throws IOException {
        verify(Paths.get("src/test/resources/samples/ruby/Sample.rb"), Language.RUBY);
    }

    @Test
    void analyze_Ruby_ModuleSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/ruby/ModuleSample.rb"), Language.RUBY);
    }

    @Test
    void analyze_Ruby_InheritanceSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/ruby/InheritanceSample.rb"), Language.RUBY);
    }

    @Test
    void analyze_Ruby_VisibilityAndClassMethodsSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/ruby/VisibilityAndClassMethodsSample.rb"), Language.RUBY);
    }

    @Test
    void analyze_Ruby_ConstantsAndRequiresSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/ruby/ConstantsAndRequiresSample.rb"), Language.RUBY);
    }

    @Test
    void analyze_Ruby_ActiveRecordSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/ruby/ActiveRecordSample.rb"), Language.RUBY);
    }
    
    @Test
    void analyze_Ruby_NestedTypesSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/ruby/NestedTypesSample.rb"), Language.RUBY);
    }

    @Test
    void analyze_Ruby_BlocksAndLambdasSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/ruby/BlocksAndLambdasSample.rb"), Language.RUBY);
    }

    private static void verify(Path file, Language language) throws IOException {
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(file, language);
        Approvals.verify(MAPPER.writeValueAsString(analysis));
    }
}
