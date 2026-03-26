package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.FieldInfo;
import org.dxworks.codeframe.model.FileAnalysis;
import org.dxworks.codeframe.model.MethodInfo;
import org.dxworks.codeframe.model.TypeInfo;
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
        extractTopLevelMethods(sourceCode, rootNode, analysis, fileScopeTypes);
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
                TypeInfo typedef = CCppHelper.analyzeTypedef(source, child);
                if (typedef != null && typedef.name != null) {
                    analysis.types.add(typedef);
                }
            }

            collectStructLikeTypes(source, child, analysis, seenTypeNodes, "struct_specifier", "struct");
            collectStructLikeTypes(source, child, analysis, seenTypeNodes, "union_specifier", "union");
            collectEnumTypes(source, child, analysis, seenTypeNodes);
        }
    }

    private void collectStructLikeTypes(String source, TSNode scopeNode, FileAnalysis analysis, Set<Integer> seenTypeNodes,
                                        String specifierType, String kind) {
        for (TSNode typeNode : findAllDescendants(scopeNode, specifierType)) {
            if (!CCppHelper.markSeen(seenTypeNodes, typeNode)) {
                continue;
            }
            TypeInfo info = CCppHelper.analyzeStructLike(source, typeNode, kind, OPTIONS);
            if (info != null && info.name != null) {
                analysis.types.add(info);
            }
        }
    }

    private void collectEnumTypes(String source, TSNode scopeNode, FileAnalysis analysis, Set<Integer> seenTypeNodes) {
        for (TSNode enumNode : findAllDescendants(scopeNode, "enum_specifier")) {
            if (!CCppHelper.markSeen(seenTypeNodes, enumNode)) {
                continue;
            }
            TypeInfo info = CCppHelper.analyzeEnum(source, enumNode, OPTIONS);
            if (info != null && info.name != null) {
                analysis.types.add(info);
            }
        }
    }

    private void extractTopLevelMethods(String source, TSNode rootNode, FileAnalysis analysis, Map<String, String> fileScopeTypes) {
        for (int i = 0; i < rootNode.getNamedChildCount(); i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) {
                continue;
            }

            if ("function_definition".equals(child.getType())) {
                MethodInfo method = analyzeFunctionDefinition(source, child, fileScopeTypes);
                if (method != null && method.name != null) {
                    analysis.methods.add(method);
                }
                continue;
            }

            if ("declaration".equals(child.getType())) {
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
        method.returnType = CCppHelper.extractDeclarationTypeText(source, functionNode, OPTIONS);
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
        method.returnType = CCppHelper.extractDeclarationTypeText(source, declarationNode, OPTIONS);
        CCppHelper.extractParameters(source, functionDeclarator, method, OPTIONS);
        CCppHelper.addModifiersFromSpecifiers(method.modifiers, source, declarationNode,
            OPTIONS.functionSpecifierNodeTypes, OPTIONS, false);
        return method;
    }

    private Map<String, String> extractTopLevelFields(String source, TSNode rootNode, FileAnalysis analysis) {
        Map<String, String> fileScopeTypes = new HashMap<>();
        for (int i = 0; i < rootNode.getNamedChildCount(); i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull() || !"declaration".equals(child.getType())) {
                continue;
            }

            if (CCppHelper.containsNonFieldFunctionDeclaration(child, OPTIONS)) {
                continue;
            }

            String typeText = CCppHelper.extractDeclarationTypeText(source, child, OPTIONS);
            List<String> declaredNames = CCppHelper.extractDeclaredVariableNames(source, child);
            for (String name : declaredNames) {
                if (name == null || name.isBlank()) {
                    continue;
                }

                String fieldType = CCppHelper.resolveFileScopeFieldType(source, child, name, typeText);

                FieldInfo field = new FieldInfo();
                field.name = name;
                field.type = fieldType;
                CCppHelper.addModifiersFromSpecifiers(field.modifiers, source, child,
                    OPTIONS.fieldSpecifierNodeTypes, OPTIONS, false);
                analysis.fields.add(field);
                if (fieldType != null) {
                    fileScopeTypes.put(name, fieldType);
                }
            }
        }
        return fileScopeTypes;
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
