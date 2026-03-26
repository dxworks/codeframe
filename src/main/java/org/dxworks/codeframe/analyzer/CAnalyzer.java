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
            TypeInfo info = analyzeStructLike(source, typeNode, kind);
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
            TypeInfo info = analyzeEnum(source, enumNode);
            if (info != null && info.name != null) {
                analysis.types.add(info);
            }
        }
    }

    private TypeInfo analyzeStructLike(String source, TSNode typeNode, String kind) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = kind;
        typeInfo.name = CCppHelper.extractTypeName(source, typeNode);

        TSNode body = findFirstChild(typeNode, "field_declaration_list");
        if (body == null) {
            return null;
        }
        extractStructFields(source, body, typeInfo);
        return typeInfo;
    }

    private TypeInfo analyzeEnum(String source, TSNode enumNode) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "enum";
        typeInfo.name = CCppHelper.extractTypeName(source, enumNode);

        TSNode enumeratorList = findFirstChild(enumNode, "enumerator_list");
        if (enumeratorList == null) {
            return null;
        }
        List<TSNode> enumerators = findAllChildren(enumeratorList, "enumerator");
        for (TSNode enumerator : enumerators) {
            String enumMember = extractName(source, enumerator, "identifier");
            if (enumMember != null) {
                FieldInfo field = new FieldInfo();
                field.name = enumMember;
                typeInfo.fields.add(field);
            }
        }

        return typeInfo;
    }

    private void extractStructFields(String source, TSNode fieldList, TypeInfo typeInfo) {
        List<TSNode> fieldDecls = findAllChildren(fieldList, "field_declaration");
        for (TSNode fieldDecl : fieldDecls) {
            String fieldType = CCppHelper.extractDeclarationTypeText(source, fieldDecl, false);
            List<TSNode> declarators = findAllDescendants(fieldDecl, "field_identifier");
            if (declarators.isEmpty()) {
                declarators = findAllDescendants(fieldDecl, "identifier");
            }

            for (TSNode declarator : declarators) {
                String fieldName = getNodeText(source, declarator);
                if (fieldName == null || fieldName.isBlank()) {
                    continue;
                }

                FieldInfo fieldInfo = new FieldInfo();
                fieldInfo.name = fieldName;
                fieldInfo.type = getStructFieldType(source, fieldDecl, declarator, fieldType);
                typeInfo.fields.add(fieldInfo);
            }
        }
    }

    private String getStructFieldType(String source, TSNode fieldDecl, TSNode fieldIdentifier, String baseType) {
        if (baseType == null || fieldIdentifier == null || fieldIdentifier.isNull()) {
            return baseType;
        }

        TSNode functionDeclarator = CCppHelper.findAncestorOfType(fieldIdentifier, fieldDecl, "function_declarator");
        TSNode pointerDeclarator = CCppHelper.findAncestorOfType(fieldIdentifier, fieldDecl, "pointer_declarator");
        if (functionDeclarator == null || pointerDeclarator == null) {
            return baseType;
        }

        String declaratorText = getNodeText(source, functionDeclarator);
        if (declaratorText == null || declaratorText.isBlank()) {
            return baseType;
        }

        return (baseType + " " + declaratorText).trim();
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
                    if (!CCppHelper.isTopLevelFunctionDeclaration(declarator, true)) {
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
        method.returnType = CCppHelper.extractDeclarationTypeText(source, functionNode, false);
        CCppHelper.extractParameters(source, declarator, method, false);
        CCppHelper.addModifiersFromSpecifiers(method.modifiers, source, functionNode,
            List.of("storage_class_specifier", "function_specifier"), false, false);

        TSNode body = getChildByFieldName(functionNode, "body");
        if (body == null) {
            body = findFirstChild(functionNode, "compound_statement");
        }
        if (body != null) {
            CCppHelper.analyzeMethodBody(source, body, method, fileScopeTypes, false, true, false);
        }

        return method;
    }

    private MethodInfo analyzeFunctionDeclaration(String source, TSNode functionDeclarator, TSNode declarationNode) {
        MethodInfo method = new MethodInfo();
        method.name = CCppHelper.extractDeclaratorName(source, functionDeclarator);
        method.returnType = CCppHelper.extractDeclarationTypeText(source, declarationNode, false);
        CCppHelper.extractParameters(source, functionDeclarator, method, false);
        CCppHelper.addModifiersFromSpecifiers(method.modifiers, source, declarationNode,
            List.of("storage_class_specifier", "function_specifier"), false, false);
        return method;
    }

    private Map<String, String> extractTopLevelFields(String source, TSNode rootNode, FileAnalysis analysis) {
        Map<String, String> fileScopeTypes = new HashMap<>();
        for (int i = 0; i < rootNode.getNamedChildCount(); i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull() || !"declaration".equals(child.getType())) {
                continue;
            }

            if (CCppHelper.containsNonFieldFunctionDeclaration(child, true)) {
                continue;
            }

            String typeText = CCppHelper.extractDeclarationTypeText(source, child, false);
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
                    List.of("storage_class_specifier", "type_qualifier"), false, false);
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
            CCppHelper.extractMethodCall(source, call, collector, fileScopeTypes == null ? Map.of() : fileScopeTypes, false, true);
            analysis.methodCalls.addAll(collector.methodCalls);
        }

        analysis.methodCalls.sort(METHOD_CALL_COMPARATOR);
    }
}
