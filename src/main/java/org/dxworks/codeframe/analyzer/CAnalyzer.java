package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.FileAnalysis;
import org.dxworks.codeframe.model.MethodInfo;
import org.treesitter.TSNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.dxworks.codeframe.analyzer.TreeSitterHelper.*;

public class CAnalyzer implements LanguageAnalyzer {
    private static final CCppAnalysisOptions OPTIONS = CCppAnalysisOptions.C;

    @Override
    public FileAnalysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        FileAnalysis analysis = new FileAnalysis();
        analysis.filePath = filePath;
        analysis.language = "c";

        if (rootNode == null || rootNode.isNull()) {
            return analysis;
        }

        CCppHelper.extractIncludes(sourceCode, rootNode, analysis);
        extractTopLevelTypes(sourceCode, rootNode, analysis);
        Map<String, String> fileScopeTypes = extractTopLevelFields(sourceCode, rootNode, analysis);
        extractMethodsFromScope(sourceCode, rootNode, analysis, fileScopeTypes);
        extractTopLevelCalls(sourceCode, rootNode, analysis, fileScopeTypes);

        return analysis;
    }

    private void extractTopLevelTypes(String source, TSNode rootNode, FileAnalysis analysis) {
        Set<Integer> seenTypeNodes = new HashSet<>();
        for (int i = 0; i < rootNode.getNamedChildCount(); i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) {
                continue;
            }

            String childType = child.getType();
            if ("type_definition".equals(childType)) {
                CCppHelper.addTypeIfNamed(analysis.types, CCppHelper.analyzeTypedef(source, child));
            }

            CCppHelper.collectSeenTypes(child, seenTypeNodes, analysis.types,
                "struct_specifier", null, n -> CCppHelper.analyzeStructLike(source, n, "struct", OPTIONS));
            CCppHelper.collectSeenTypes(child, seenTypeNodes, analysis.types,
                "union_specifier", null, n -> CCppHelper.analyzeStructLike(source, n, "union", OPTIONS));
            CCppHelper.collectSeenTypes(child, seenTypeNodes, analysis.types,
                "enum_specifier", null, n -> CCppHelper.analyzeEnum(source, n, OPTIONS));
        }
    }

    private void extractMethodsFromScope(String source, TSNode scopeNode, FileAnalysis analysis, Map<String, String> fileScopeTypes) {
        for (int i = 0; i < scopeNode.getNamedChildCount(); i++) {
            TSNode child = scopeNode.getNamedChild(i);
            if (child == null || child.isNull()) {
                continue;
            }

            String childType = child.getType();
            if (childType != null && (childType.startsWith("preproc_") || "linkage_specification".equals(childType))) {
                extractMethodsFromScope(source, child, analysis, fileScopeTypes);
                continue;
            }

            if ("function_definition".equals(childType)) {
                MethodInfo method = analyzeFunctionDefinition(source, child, fileScopeTypes);
                if (method != null && method.name != null) {
                    analysis.methods.add(method);
                }
                continue;
            }

            if ("declaration".equals(childType)) {
                List<TSNode> functionDeclarators = findAllDescendants(child, "function_declarator");
                for (TSNode declarator : functionDeclarators) {
                    if (!CCppHelper.isTopLevelFunctionDeclaration(declarator, OPTIONS)) {
                        continue;
                    }
                    MethodInfo method = analyzeFunctionDeclaration(source, declarator, child);
                    if (method != null && method.name != null) {
                        analysis.methods.add(method);
                    }
                }
            }
        }
    }

    private MethodInfo analyzeFunctionDefinition(String source, TSNode functionNode, Map<String, String> fileScopeTypes) {
        MethodInfo method = new MethodInfo();

        TSNode declarator = getChildByFieldName(functionNode, "declarator");
        method.name = CCppHelper.extractDeclaratorName(source, declarator);
        method.returnType = CCppHelper.extractFunctionReturnTypeText(source, functionNode, declarator, OPTIONS);
        CCppHelper.extractParameters(source, declarator, method, OPTIONS);
        CCppHelper.addModifiersFromSpecifiers(method.modifiers, source, functionNode,
            OPTIONS.functionSpecifierNodeTypes, OPTIONS, false);

        TSNode body = getChildByFieldName(functionNode, "body");
        if (body == null) {
            body = findFirstChild(functionNode, "compound_statement");
        }
        if (body != null) {
            CCppHelper.analyzeMethodBody(source, body, method, fileScopeTypes, OPTIONS);
        }

        return method;
    }

    private MethodInfo analyzeFunctionDeclaration(String source, TSNode functionDeclarator, TSNode declarationNode) {
        MethodInfo method = new MethodInfo();
        method.name = CCppHelper.extractDeclaratorName(source, functionDeclarator);
        method.returnType = CCppHelper.extractFunctionReturnTypeText(source, declarationNode, functionDeclarator, OPTIONS);
        method.isDeclarationOnly = true;
        CCppHelper.extractParameters(source, functionDeclarator, method, OPTIONS);
        CCppHelper.addModifiersFromSpecifiers(method.modifiers, source, declarationNode,
            OPTIONS.functionSpecifierNodeTypes, OPTIONS, false);
        return method;
    }

    private Map<String, String> extractTopLevelFields(String source, TSNode rootNode, FileAnalysis analysis) {
        Map<String, String> fileScopeTypes = new HashMap<>();
        extractFieldsFromScope(source, rootNode, analysis, fileScopeTypes);
        return fileScopeTypes;
    }

    private void extractFieldsFromScope(String source, TSNode scopeNode, FileAnalysis analysis, Map<String, String> fileScopeTypes) {
        for (int i = 0; i < scopeNode.getNamedChildCount(); i++) {
            TSNode child = scopeNode.getNamedChild(i);
            if (child == null || child.isNull()) {
                continue;
            }

            String childType = child.getType();
            if (childType != null && (childType.startsWith("preproc_") || "linkage_specification".equals(childType))) {
                extractFieldsFromScope(source, child, analysis, fileScopeTypes);
                continue;
            }

            if (!"declaration".equals(childType)) {
                continue;
            }

            CCppHelper.addFieldsFromDeclaration(source, child, analysis.fields, fileScopeTypes, OPTIONS);
        }
    }

    private void extractTopLevelCalls(String source, TSNode rootNode, FileAnalysis analysis, Map<String, String> fileScopeTypes) {
        List<TSNode> calls = findAllDescendants(rootNode, "call_expression");
        for (TSNode call : calls) {
            if (CCppHelper.isInsideNodeType(call, "function_definition")
                || CCppHelper.isInsideNodeType(call, "type_definition")
                || CCppHelper.isInsideNodeType(call, "struct_specifier")
                || CCppHelper.isInsideNodeType(call, "union_specifier")
                || CCppHelper.isInsideNodeType(call, "enum_specifier")) {
                continue;
            }

            MethodInfo collector = new MethodInfo();
            CCppHelper.extractMethodCall(source, call, collector, fileScopeTypes == null ? Map.of() : fileScopeTypes, OPTIONS);
            analysis.methodCalls.addAll(collector.methodCalls);
        }

        analysis.methodCalls.sort(METHOD_CALL_COMPARATOR);
    }
}
