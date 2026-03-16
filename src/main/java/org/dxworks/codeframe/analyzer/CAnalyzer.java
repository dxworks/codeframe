package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.FieldInfo;
import org.dxworks.codeframe.model.FileAnalysis;
import org.dxworks.codeframe.model.MethodInfo;
import org.dxworks.codeframe.model.Parameter;
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

        extractIncludes(sourceCode, rootNode, analysis);
        extractTopLevelTypes(sourceCode, rootNode, analysis);
        Map<String, String> fileScopeTypes = extractTopLevelFields(sourceCode, rootNode, analysis);
        extractTopLevelMethods(sourceCode, rootNode, analysis, fileScopeTypes);
        extractTopLevelCalls(sourceCode, rootNode, analysis, fileScopeTypes);

        return analysis;
    }

    private void extractIncludes(String source, TSNode rootNode, FileAnalysis analysis) {
        List<TSNode> includes = findAllDescendants(rootNode, "preproc_include");
        for (TSNode includeNode : includes) {
            String text = getNodeText(source, includeNode);
            if (text != null && !text.isBlank()) {
                analysis.imports.add(text.trim());
            }
        }
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
                TypeInfo typedef = analyzeTypedef(source, child);
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
            if (!markSeen(seenTypeNodes, typeNode)) {
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
            if (!markSeen(seenTypeNodes, enumNode)) {
                continue;
            }
            TypeInfo info = analyzeEnum(source, enumNode);
            if (info != null && info.name != null) {
                analysis.types.add(info);
            }
        }
    }

    private boolean markSeen(Set<Integer> seen, TSNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        return seen.add(node.getStartByte());
    }

    private TypeInfo analyzeStructLike(String source, TSNode typeNode, String kind) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = kind;
        typeInfo.name = extractTypeName(source, typeNode);

        TSNode body = findFirstChild(typeNode, "field_declaration_list");
        if (body != null) {
            extractStructFields(source, body, typeInfo);
        }
        return typeInfo;
    }

    private TypeInfo analyzeEnum(String source, TSNode enumNode) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "enum";
        typeInfo.name = extractTypeName(source, enumNode);

        TSNode enumeratorList = findFirstChild(enumNode, "enumerator_list");
        if (enumeratorList != null) {
            List<TSNode> enumerators = findAllChildren(enumeratorList, "enumerator");
            for (TSNode enumerator : enumerators) {
                String enumMember = extractName(source, enumerator, "identifier");
                if (enumMember != null) {
                    FieldInfo field = new FieldInfo();
                    field.name = enumMember;
                    typeInfo.fields.add(field);
                }
            }
        }

        return typeInfo;
    }

    private TypeInfo analyzeTypedef(String source, TSNode typedefNode) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "typedef";

        TSNode declaratorNode = getChildByFieldName(typedefNode, "declarator");
        String aliasName = extractDeclaratorName(source, declaratorNode);
        if (aliasName != null) {
            typeInfo.name = aliasName;

            String typedefText = getNodeText(source, typedefNode);
            if (typedefText != null) {
                String normalized = typedefText.trim();
                if (normalized.endsWith(";")) {
                    normalized = normalized.substring(0, normalized.length() - 1);
                }

                if (isFunctionPointerDeclarator(declaratorNode)) {
                    typeInfo.extendsType = normalized.replaceFirst("^typedef\\s+", "").trim();
                } else {
                    int aliasPos = normalized.lastIndexOf(aliasName);
                    if (aliasPos > 0) {
                        String targetText = normalized.substring(0, aliasPos)
                            .replaceFirst("^typedef\\s+", "")
                            .trim();
                        if (!targetText.isEmpty()) {
                            typeInfo.extendsType = targetText;
                        }
                    }
                }
            }
        }

        return typeInfo;
    }

    private boolean isFunctionPointerDeclarator(TSNode declaratorNode) {
        if (declaratorNode == null || declaratorNode.isNull()) {
            return false;
        }
        return !findAllDescendants(declaratorNode, "function_declarator").isEmpty()
            && !findAllDescendants(declaratorNode, "pointer_declarator").isEmpty();
    }

    private String extractTypeName(String source, TSNode typeNode) {
        TSNode nameNode = findFirstChild(typeNode, "type_identifier");
        if (nameNode != null) {
            return getNodeText(source, nameNode);
        }

        nameNode = findFirstChild(typeNode, "identifier");
        if (nameNode != null) {
            return getNodeText(source, nameNode);
        }
        return null;
    }

    private void extractStructFields(String source, TSNode fieldList, TypeInfo typeInfo) {
        List<TSNode> fieldDecls = findAllChildren(fieldList, "field_declaration");
        for (TSNode fieldDecl : fieldDecls) {
            String fieldType = extractDeclarationTypeText(source, fieldDecl);
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

        TSNode functionDeclarator = findEnclosingNodeWithin(fieldIdentifier, fieldDecl, "function_declarator");
        TSNode pointerDeclarator = findEnclosingNodeWithin(fieldIdentifier, fieldDecl, "pointer_declarator");
        if (functionDeclarator == null || pointerDeclarator == null) {
            return baseType;
        }

        String declaratorText = getNodeText(source, functionDeclarator);
        if (declaratorText == null || declaratorText.isBlank()) {
            return baseType;
        }

        return (baseType + " " + declaratorText).trim();
    }

    private TSNode findEnclosingNodeWithin(TSNode startNode, TSNode boundaryNode, String type) {
        return findAncestorOfType(startNode, boundaryNode, type);
    }

    private TSNode findAncestorOfType(TSNode startNode, TSNode boundaryNode, String type) {
        TSNode current = startNode == null ? null : startNode.getParent();
        while (current != null && !current.isNull() && !isSameNode(current, boundaryNode)) {
            if (type.equals(current.getType())) {
                return current;
            }
            current = current.getParent();
        }
        return null;
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
                    if (!isTopLevelFunctionDeclaration(declarator)) {
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
        method.name = extractDeclaratorName(source, declarator);
        method.returnType = extractDeclarationTypeText(source, functionNode);
        extractParameters(source, declarator, method);

        TSNode body = getChildByFieldName(functionNode, "body");
        if (body == null) {
            body = findFirstChild(functionNode, "compound_statement");
        }
        if (body != null) {
            analyzeMethodBody(source, body, method, fileScopeTypes);
        }

        return method;
    }

    private MethodInfo analyzeFunctionDeclaration(String source, TSNode functionDeclarator, TSNode declarationNode) {
        MethodInfo method = new MethodInfo();
        method.name = extractDeclaratorName(source, functionDeclarator);
        method.returnType = extractDeclarationTypeText(source, declarationNode);
        extractParameters(source, functionDeclarator, method);
        return method;
    }

    private void extractParameters(String source, TSNode declarator, MethodInfo method) {
        if (declarator == null || declarator.isNull()) {
            return;
        }

        TSNode parameterList = findFirstChild(declarator, "parameter_list");
        if (parameterList == null) {
            return;
        }

        List<TSNode> params = findAllChildren(parameterList, "parameter_declaration");
        for (TSNode param : params) {
            String paramType = extractDeclarationTypeText(source, param);
            String paramName = extractFirstIdentifier(source, param);
            if (paramName != null) {
                method.parameters.add(new Parameter(paramName, paramType));
            }
        }
    }

    private void analyzeMethodBody(String source, TSNode body, MethodInfo method, Map<String, String> fileScopeTypes) {
        Map<String, String> localTypes = new HashMap<>();
        if (fileScopeTypes != null) {
            localTypes.putAll(fileScopeTypes);
        }

        for (Parameter param : method.parameters) {
            if (param != null && param.name != null && param.type != null) {
                localTypes.put(param.name, param.type);
            }
        }

        List<TSNode> declarations = findAllDescendants(body, "declaration");
        for (TSNode declaration : declarations) {
            String typeText = extractDeclarationTypeText(source, declaration);
            List<String> declaredNames = extractDeclaredVariableNames(source, declaration);
            for (String name : declaredNames) {
                if (name != null && !name.isBlank()) {
                    method.localVariables.add(name);
                    if (typeText != null) {
                        localTypes.put(name, typeText);
                    }
                }
            }
        }

        List<TSNode> callExpressions = findAllDescendants(body, "call_expression");
        for (TSNode call : callExpressions) {
            extractMethodCall(source, call, method, localTypes);
        }

        method.methodCalls.sort(METHOD_CALL_COMPARATOR);
    }

    private void extractMethodCall(String source, TSNode call, MethodInfo method, Map<String, String> localTypes) {
        TSNode functionNode = call.getNamedChild(0);
        if (functionNode == null || functionNode.isNull()) {
            return;
        }

        String methodName = null;
        String objectName = null;
        String objectType = null;

        if ("identifier".equals(functionNode.getType())) {
            methodName = getNodeText(source, functionNode);
        } else if ("field_expression".equals(functionNode.getType())) {
            TSNode fieldId = findFirstChild(functionNode, "field_identifier");
            if (fieldId == null) {
                fieldId = findFirstChild(functionNode, "identifier");
            }
            if (fieldId != null) {
                methodName = getNodeText(source, fieldId);
            }

            String baseObjectName = extractBaseObjectIdentifier(source, functionNode);
            if (baseObjectName != null) {
                objectName = baseObjectName;
                objectType = localTypes.get(objectName);
            }
        }

        if (methodName == null || methodName.isBlank()) {
            return;
        }

        TSNode args = getArgumentListNode(call);
        Integer parameterCount = args == null ? 0 : args.getNamedChildCount();
        collectMethodCall(method, methodName, objectType, objectName, parameterCount);
    }

    private Map<String, String> extractTopLevelFields(String source, TSNode rootNode, FileAnalysis analysis) {
        Map<String, String> fileScopeTypes = new HashMap<>();
        for (int i = 0; i < rootNode.getNamedChildCount(); i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull() || !"declaration".equals(child.getType())) {
                continue;
            }

            if (containsNonFieldFunctionDeclaration(child)) {
                continue;
            }

            String typeText = extractDeclarationTypeText(source, child);
            List<String> declaredNames = extractDeclaredVariableNames(source, child);
            for (String name : declaredNames) {
                if (name == null || name.isBlank()) {
                    continue;
                }

                String fieldType = resolveFileScopeFieldType(source, child, name, typeText);

                FieldInfo field = new FieldInfo();
                field.name = name;
                field.type = fieldType;
                analysis.fields.add(field);
                if (fieldType != null) {
                    fileScopeTypes.put(name, fieldType);
                }
            }
        }
        return fileScopeTypes;
    }

    private String resolveFileScopeFieldType(String source, TSNode declarationNode, String fieldName, String baseType) {
        if (declarationNode == null || declarationNode.isNull() || fieldName == null || fieldName.isBlank()) {
            return baseType;
        }

        List<TSNode> functionDeclarators = findAllDescendants(declarationNode, "function_declarator");
        for (TSNode functionDeclarator : functionDeclarators) {
            if (findAllDescendants(functionDeclarator, "pointer_declarator").isEmpty()) {
                continue;
            }

            String declaratorName = extractDeclaratorName(source, functionDeclarator);
            if (!fieldName.equals(declaratorName)) {
                continue;
            }

            String declaratorText = getNodeText(source, functionDeclarator);
            if (declaratorText == null || declaratorText.isBlank()) {
                return baseType;
            }

            if (baseType == null || baseType.isBlank()) {
                return declaratorText.trim();
            }
            return (baseType + " " + declaratorText).trim();
        }

        return baseType;
    }

    private void extractTopLevelCalls(String source, TSNode rootNode, FileAnalysis analysis, Map<String, String> fileScopeTypes) {
        List<TSNode> calls = findAllDescendants(rootNode, "call_expression");
        for (TSNode call : calls) {
            if (isInsideNodeType(call, "function_definition")
                || isInsideNodeType(call, "type_definition")
                || isInsideNodeType(call, "struct_specifier")
                || isInsideNodeType(call, "union_specifier")
                || isInsideNodeType(call, "enum_specifier")) {
                continue;
            }

            MethodInfo collector = new MethodInfo();
            extractMethodCall(source, call, collector, fileScopeTypes == null ? Map.of() : fileScopeTypes);
            analysis.methodCalls.addAll(collector.methodCalls);
        }

        analysis.methodCalls.sort(METHOD_CALL_COMPARATOR);
    }

    private boolean isInsideNodeType(TSNode node, String ancestorType) {
        return findAncestorOfType(node, null, ancestorType) != null;
    }

    private String extractBaseObjectIdentifier(String source, TSNode fieldExpressionNode) {
        TSNode current = fieldExpressionNode == null ? null : fieldExpressionNode.getNamedChild(0);
        while (current != null && !current.isNull()) {
            if ("identifier".equals(current.getType())) {
                return getNodeText(source, current);
            }
            if (current.getNamedChildCount() == 0) {
                break;
            }
            current = current.getNamedChild(0);
        }
        return null;
    }

    private List<String> extractDeclaredVariableNames(String source, TSNode declarationNode) {
        List<String> names = new java.util.ArrayList<>();

        List<TSNode> initDeclarators = findAllDescendants(declarationNode, "init_declarator");
        for (TSNode initDeclarator : initDeclarators) {
            TSNode declarator = getChildByFieldName(initDeclarator, "declarator");
            String name = extractDeclaratorName(source, declarator);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }

        if (!names.isEmpty()) {
            return names;
        }

        for (TSNode identifier : findAllChildren(declarationNode, "identifier")) {
            String name = getNodeText(source, identifier);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }

        if (names.isEmpty()) {
            TSNode declarator = getChildByFieldName(declarationNode, "declarator");
            String name = extractDeclaratorName(source, declarator);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    private boolean containsNonFieldFunctionDeclaration(TSNode declarationNode) {
        List<TSNode> functionDeclarators = findAllDescendants(declarationNode, "function_declarator");
        for (TSNode functionDeclarator : functionDeclarators) {
            if (isTopLevelFunctionDeclaration(functionDeclarator)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTopLevelFunctionDeclaration(TSNode functionDeclarator) {
        if (functionDeclarator == null || functionDeclarator.isNull()) {
            return false;
        }

        // C function-pointer variables look like: int (*cb)(int)
        // where pointer_declarator is nested inside the function_declarator.
        // A true top-level function declaration should not have this nested pattern.
        return findAllDescendants(functionDeclarator, "pointer_declarator").isEmpty();
    }

    private String extractDeclaratorName(String source, TSNode declarator) {
        if (declarator == null || declarator.isNull()) {
            return null;
        }

        TSNode nestedDeclarator = getChildByFieldName(declarator, "declarator");
        if (nestedDeclarator != null && !nestedDeclarator.isNull()) {
            String nestedName = extractDeclaratorName(source, nestedDeclarator);
            if (nestedName != null) {
                return nestedName;
            }
        }

        if ("identifier".equals(declarator.getType())) {
            return getNodeText(source, declarator);
        }

        if ("type_identifier".equals(declarator.getType())) {
            return getNodeText(source, declarator);
        }

        List<TSNode> ids = findAllDescendants(declarator, "identifier");
        for (TSNode id : ids) {
            TSNode parent = id.getParent();
            if (parent != null && "parameter_declaration".equals(parent.getType())) {
                continue;
            }
            String text = getNodeText(source, id);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }

        List<TSNode> typeIds = findAllDescendants(declarator, "type_identifier");
        if (!typeIds.isEmpty()) {
            return getNodeText(source, typeIds.get(0));
        }

        return null;
    }

    private String extractFirstIdentifier(String source, TSNode node) {
        List<TSNode> ids = findAllDescendants(node, "identifier");
        if (ids.isEmpty()) {
            return null;
        }
        return getNodeText(source, ids.get(ids.size() - 1));
    }

    private String extractDeclarationTypeText(String source, TSNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        TSNode typeNode = getChildByFieldName(node, "type");
        if (typeNode != null) {
            return getNodeText(source, typeNode);
        }

        TSNode primitive = findFirstChild(node, "primitive_type");
        if (primitive != null) {
            return getNodeText(source, primitive);
        }

        TSNode typeIdentifier = findFirstChild(node, "type_identifier");
        if (typeIdentifier != null) {
            return getNodeText(source, typeIdentifier);
        }

        TSNode structSpecifier = findFirstChild(node, "struct_specifier");
        if (structSpecifier != null) {
            return getNodeText(source, structSpecifier);
        }

        TSNode unionSpecifier = findFirstChild(node, "union_specifier");
        if (unionSpecifier != null) {
            return getNodeText(source, unionSpecifier);
        }

        TSNode enumSpecifier = findFirstChild(node, "enum_specifier");
        if (enumSpecifier != null) {
            return getNodeText(source, enumSpecifier);
        }

        return null;
    }
}
