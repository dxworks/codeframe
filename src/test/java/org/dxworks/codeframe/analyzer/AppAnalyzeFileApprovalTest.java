package org.dxworks.codeframe.analyzer;

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

public class AppAnalyzeFileApprovalTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    // ---- Java ----
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
    
    // ---- JavaScript ----
    @Test
    void analyze_JavaScript_Sample() throws IOException {
        verify(Paths.get("src/test/resources/samples/javascript/sample.js"), Language.JAVASCRIPT);
    }

    @Test
    void analyze_JavaScript_ResourceWrapper() throws IOException {
        verify(Paths.get("src/test/resources/samples/javascript/resourceLocationWrapper.js"), Language.JAVASCRIPT);
    }

    // ---- TypeScript / TSX ----
    @Test
    void analyze_TypeScript_Sample() throws IOException {
        verify(Paths.get("src/test/resources/samples/typescript/sample.ts"), Language.TYPESCRIPT);
    }

    @Test
    void analyze_Tsx_ConfirmDiscardPrompt() throws IOException {
        verify(Paths.get("src/test/resources/samples/typescript/confirm-discard-prompt.tsx"), Language.TYPESCRIPT);
    }

    @Test
    void analyze_TypeScript_OrderParams() throws IOException {
        verify(Paths.get("src/test/resources/samples/typescript/order-params.ts"), Language.TYPESCRIPT);
    }

    @Test
    void analyze_TypeScript_InterfaceSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/typescript/interface-sample.ts"), Language.TYPESCRIPT);
    }

    // ---- C# ----
    @Test
    void analyze_CSharp_DataClass() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/DataClass.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_DataClassNS() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/DataClassNS.cs"), Language.CSHARP);
    }

    @Test
    void analyze_CSharp_InterfaceSample() throws IOException {
        verify(Paths.get("src/test/resources/samples/csharp/InterfaceSample.cs"), Language.CSHARP);
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
    
    // ---- Python ----
    @Test
    void analyze_Python_ComplexExample() throws IOException {
        verify(Paths.get("src/test/resources/samples/python/complex_example.py"), Language.PYTHON);
    }

    // ---- PHP ----
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

    private static void verify(Path file, Language language) throws IOException {
        FileAnalysis analysis = App.analyzeFile(file, language);
        Approvals.verify(MAPPER.writeValueAsString(analysis));
    }
}
