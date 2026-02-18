package org.dxworks.codeframe.analyzer.csharp;

import org.approvaltests.Approvals;
import org.dxworks.codeframe.App;
import org.dxworks.codeframe.Language;
import org.dxworks.codeframe.TestUtils;
import org.dxworks.codeframe.model.FileAnalysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CSharpAnalyzeApprovalTest {

    @Test
    void analyze_CSharp_DataClass() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/DataClass.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_DataClassNS() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/DataClassNS.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_InterfaceAndGenericsSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/InterfaceAndGenericsSample.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_InnerOutter() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/InnerOutter.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_InheritanceSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/InheritanceSample.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_ExceptionsAndUsingSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/ExceptionsAndUsingSample.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_DelegatesEventsLambdasSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/DelegatesEventsLambdasSample.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_ExtensionMethodsSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/ExtensionMethodsSample.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_RecordsAndPatternsSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/RecordsAndPatternsSample.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_NullabilitySample() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/NullabilitySample.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_IteratorsAndIndexersSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/IteratorsAndIndexersSample.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_TuplesRangesAndTargetTypedNewSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/TuplesRangesAndTargetTypedNewSample.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_PropertiesUsageSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/PropertiesUsageSample.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_LoopLocalsSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/LoopLocalsSample.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_EnumUsageSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/EnumUsageSample.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_RecursionSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/RecursionSample.cs"), Language.CSHARP);
    }

    private static void verify(Path file, Language language) throws IOException {
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(file, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
