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
import java.util.List;

public class CSharpAnalyzeApprovalTest {
    private static final String SAMPLES_BASE_PATH = "src/test/resources/samples/csharp/";

    @Test
    void analyze_CSharp_DataClass() throws IOException {
        verify("DataClass.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_DataClassNS() throws IOException {
        verify("DataClassNS.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_InterfaceAndGenericsSample() throws IOException {
        verify("InterfaceAndGenericsSample.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_InnerOutter() throws IOException {
        verify("InnerOutter.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_InheritanceSample() throws IOException {
        verify("InheritanceSample.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_ExceptionsAndUsingSample() throws IOException {
        verify("ExceptionsAndUsingSample.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_DelegatesEventsLambdasSample() throws IOException {
        verify("DelegatesEventsLambdasSample.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_ExtensionMethodsSample() throws IOException {
        verify("ExtensionMethodsSample.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_RecordsAndPatternsSample() throws IOException {
        verify("RecordsAndPatternsSample.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_NullabilitySample() throws IOException {
        verify("NullabilitySample.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_IteratorsAndIndexersSample() throws IOException {
        verify("IteratorsAndIndexersSample.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_TuplesRangesAndTargetTypedNewSample() throws IOException {
        verify("TuplesRangesAndTargetTypedNewSample.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_PropertiesUsageSample() throws IOException {
        verify("PropertiesUsageSample.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_LoopLocalsSample() throws IOException {
        verify("LoopLocalsSample.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_EnumUsageSample() throws IOException {
        verify("EnumUsageSample.cs", Language.CSHARP);
    }

    @Test
    void analyze_CSharp_RecursionSample() throws IOException {
        verify("RecursionSample.cs", Language.CSHARP);
    }

    private static void verify(String fileName, Language language) throws IOException {
        // Initialize analyzers for tests
        App.initAnalyzersForTestsFromPaths(List.of());
        Path filePath = Paths.get(SAMPLES_BASE_PATH + fileName);
        FileAnalysis analysis = (FileAnalysis) App.analyzeFile(filePath, language);
        Approvals.verify(TestUtils.APPROVAL_MAPPER.writeValueAsString(analysis));
    }
}
