package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.FieldInfo;
import org.dxworks.codeframe.model.FileAnalysis;
import org.dxworks.codeframe.model.MethodInfo;
import org.dxworks.codeframe.model.Parameter;
import org.dxworks.codeframe.model.TypeInfo;
import org.treesitter.TSNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.dxworks.codeframe.analyzer.TreeSitterHelper.*;

public final class CCppHelper {
    private CCppHelper() {
    }

    public static void extractIncludes(String source, TSNode rootNode, FileAnalysis analysis) {
        for (TSNode includeNode : findAllDescendants(rootNode, "preproc_include")) {
            String text = getNodeText(source, includeNode);
            if (text != null && !text.isBlank()) {
                analysis.imports.add(text.trim());
            }
        }
    }

    public static boolean markSeen(Set<Integer> seen, TSNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        return seen.add(node.getStartByte());
    }

    public static String extractTypeName(String source, TSNode typeNode) {
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

    public static TypeInfo analyzeTypedef(String source, TSNode typedefNode) {
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

    public static TypeInfo analyzeStructLike(String source, TSNode typeNode, String kind, CCppAnalysisOptions options) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = kind;
        typeInfo.name = extractTypeName(source, typeNode);
        addModifiersFromSpecifiers(typeInfo.modifiers, source, typeNode, options.typeSpecifierNodeTypes,
            options, isTypeContainerNode(typeNode));

        TSNode body = findFirstChild(typeNode, "field_declaration_list");
        if (body == null) {
            return null;
        }
        extractStructFields(source, body, typeInfo, options);
        return typeInfo;
    }

    public static TypeInfo analyzeEnum(String source, TSNode enumNode, CCppAnalysisOptions options) {
        TypeInfo typeInfo = new TypeInfo();
        String enumText = getNodeText(source, enumNode);
        typeInfo.kind = enumText != null && containsKeyword(enumText, "class") ? "enum class" : "enum";
        typeInfo.name = extractTypeName(source, enumNode);

        TSNode enumeratorList = findFirstChild(enumNode, "enumerator_list");
        if (enumeratorList == null) {
            return null;
        }
        for (TSNode enumerator : findAllChildren(enumeratorList, "enumerator")) {
            String enumMember = extractName(source, enumerator, "identifier");
            if (enumMember != null) {
                FieldInfo field = new FieldInfo();
                field.name = enumMember;
                typeInfo.fields.add(field);
            }
        }

        return typeInfo;
    }

    public static void extractStructFields(String source, TSNode fieldList, TypeInfo typeInfo, CCppAnalysisOptions options) {
        for (TSNode fieldDecl : findAllChildren(fieldList, "field_declaration")) {
            String fieldType = extractDeclarationTypeText(source, fieldDecl, options);
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
                addModifiersFromSpecifiers(fieldInfo.modifiers, source, fieldDecl,
                    options.fieldSpecifierNodeTypes, options, false);
                typeInfo.fields.add(fieldInfo);
            }
        }
    }

    public static String getStructFieldType(String source, TSNode fieldDecl, TSNode fieldIdentifier, String baseType) {
        if (baseType == null || fieldIdentifier == null || fieldIdentifier.isNull()) {
            return baseType;
        }

        TSNode functionDeclarator = findAncestorOfType(fieldIdentifier, fieldDecl, "function_declarator");
        TSNode pointerDeclarator = findAncestorOfType(fieldIdentifier, fieldDecl, "pointer_declarator");
        if (functionDeclarator == null || pointerDeclarator == null) {
            return baseType;
        }

        String declaratorText = getNodeText(source, functionDeclarator);
        if (declaratorText == null || declaratorText.isBlank()) {
            return baseType;
        }

        return (baseType + " " + declaratorText).trim();
    }

    static boolean isTypeContainerNode(TSNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        String type = node.getType();
        return "class_specifier".equals(type) || "struct_specifier".equals(type) || "union_specifier".equals(type);
    }

    public static boolean isInsideTypeContainer(TSNode node, TSNode stopNode) {
        TSNode current = node == null ? null : node.getParent();
        while (current != null && !current.isNull()) {
            if (isSameNode(current, stopNode)) {
                return false;
            }
            if (isTypeContainerNode(current)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    public static boolean isInsideTypeContainer(TSNode node) {
        return isInsideNodeType(node, "class_specifier")
            || isInsideNodeType(node, "struct_specifier")
            || isInsideNodeType(node, "union_specifier")
            || isInsideNodeType(node, "enum_specifier");
    }

    public static boolean isInsideTypeOrFunctionContainer(TSNode node) {
        return isInsideTypeContainer(node) || isInsideNodeType(node, "function_definition");
    }

    public static boolean isInsideTypeOrTemplateContainer(TSNode node) {
        return isInsideTypeContainer(node) || isInsideNodeType(node, "template_declaration");
    }

    public static boolean isInsideTypeOrFunctionOrTemplateContainer(TSNode node) {
        return isInsideTypeOrFunctionContainer(node) || isInsideNodeType(node, "template_declaration");
    }

    public static boolean isFunctionPointerDeclarator(TSNode declaratorNode) {
        if (declaratorNode == null || declaratorNode.isNull()) {
            return false;
        }
        return !findAllDescendants(declaratorNode, "function_declarator").isEmpty()
            && !findAllDescendants(declaratorNode, "pointer_declarator").isEmpty();
    }

    public static TSNode resolveFunctionDeclarator(TSNode declarationNode) {
        TSNode declarator = findFirstChild(declarationNode, "function_declarator");
        if (declarator != null) {
            return declarator;
        }

        TSNode referenceDeclarator = findFirstChild(declarationNode, "reference_declarator");
        if (referenceDeclarator != null) {
            TSNode nestedDeclarator = findFirstDescendant(referenceDeclarator, "function_declarator");
            if (nestedDeclarator != null) {
                return nestedDeclarator;
            }
        }

        return null;
    }

    public static void extractParameters(String source, TSNode declarator, MethodInfo method, CCppAnalysisOptions options) {
        extractParameters(source, declarator, method, options.includeAutoInDeclarationType);
    }

    public static void extractParameters(String source, TSNode declarator, MethodInfo method, boolean includeAutoInDeclarationType) {
        if (declarator == null || declarator.isNull()) {
            return;
        }

        TSNode parameterList = findFirstChild(declarator, "parameter_list");
        if (parameterList == null) {
            return;
        }

        for (TSNode param : findAllChildren(parameterList, "parameter_declaration")) {
            String paramName = extractFirstIdentifier(source, param);
            String paramType = extractParameterTypeText(source, param, paramName, includeAutoInDeclarationType);
            if (paramName != null) {
                method.parameters.add(new Parameter(paramName, paramType));
            }
        }

        if (hasVariadicParameter(source, parameterList)) {
            method.parameters.add(new Parameter("...", null));
        }
    }

    public static String extractParameterTypeText(String source, TSNode parameterNode, String parameterName, boolean includeAutoInDeclarationType) {
        if (parameterNode == null || parameterNode.isNull()) {
            return null;
        }

        String fullText = getNodeText(source, parameterNode);
        if (fullText == null || fullText.isBlank()) {
            return extractDeclarationTypeText(source, parameterNode, includeAutoInDeclarationType);
        }

        String normalized = normalizeWhitespace(fullText);
        if (parameterName == null || parameterName.isBlank()) {
            return normalized;
        }

        int idx = findLastIdentifierOccurrence(normalized, parameterName);
        if (idx < 0) {
            return extractDeclarationTypeText(source, parameterNode, includeAutoInDeclarationType);
        }

        String typeText = normalizeWhitespace(
            normalized.substring(0, idx) + " " + normalized.substring(idx + parameterName.length())
        );
        typeText = normalizeFunctionPointerSpacing(typeText);

        if (typeText == null || typeText.isBlank()) {
            return extractDeclarationTypeText(source, parameterNode, includeAutoInDeclarationType);
        }
        return typeText;
    }

    public static String extractFirstIdentifier(String source, TSNode node) {
        List<TSNode> ids = findAllDescendants(node, "identifier");
        if (ids.isEmpty()) {
            return null;
        }
        return getNodeText(source, ids.get(ids.size() - 1));
    }

    public static void analyzeMethodBody(String source,
                                         TSNode body,
                                         MethodInfo method,
                                         Map<String, String> fileScopeTypes,
                                         CCppAnalysisOptions options) {
        analyzeMethodBody(source, body, method, fileScopeTypes,
            options.allowQualifiedIdentifierCalls, options.deepBaseObjectLookup, options.includeAutoInDeclarationType);
    }

    public static void analyzeMethodBody(String source,
                                         TSNode body,
                                         MethodInfo method,
                                         Map<String, String> fileScopeTypes,
                                         boolean allowQualifiedIdentifierCalls,
                                         boolean deepBaseObjectLookup,
                                         boolean includeAutoInDeclarationType) {
        Map<String, String> localTypes = new HashMap<>();
        if (fileScopeTypes != null) {
            localTypes.putAll(fileScopeTypes);
        }

        for (Parameter param : method.parameters) {
            if (param != null && param.name != null && param.type != null) {
                localTypes.put(param.name, param.type);
            }
        }

        for (TSNode declaration : findAllDescendants(body, "declaration")) {
            String typeText = extractDeclarationTypeText(source, declaration, includeAutoInDeclarationType);
            for (String name : extractDeclaredVariableNames(source, declaration)) {
                if (name != null && !name.isBlank()) {
                    method.localVariables.add(name);
                    if (typeText != null) {
                        localTypes.put(name, typeText);
                    }
                }
            }
        }

        for (TSNode call : findAllDescendants(body, "call_expression")) {
            extractMethodCall(source, call, method, localTypes, allowQualifiedIdentifierCalls, deepBaseObjectLookup);
        }

        method.methodCalls.sort(METHOD_CALL_COMPARATOR);
    }

    public static void extractMethodCall(String source,
                                         TSNode call,
                                         MethodInfo method,
                                         Map<String, String> localTypes,
                                         CCppAnalysisOptions options) {
        extractMethodCall(source, call, method, localTypes,
            options.allowQualifiedIdentifierCalls, options.deepBaseObjectLookup);
    }

    public static void extractMethodCall(String source,
                                         TSNode call,
                                         MethodInfo method,
                                         Map<String, String> localTypes,
                                         boolean allowQualifiedIdentifierCalls,
                                         boolean deepBaseObjectLookup) {
        TSNode functionNode = call.getNamedChild(0);
        if (functionNode == null || functionNode.isNull()) {
            return;
        }

        String functionNodeType = functionNode.getType();
        boolean isMemberLike = "field_expression".equals(functionNodeType)
            || (allowQualifiedIdentifierCalls && "qualified_identifier".equals(functionNodeType));

        String methodName = null;
        String objectName = null;
        String objectType = null;

        if ("identifier".equals(functionNodeType)) {
            methodName = getNodeText(source, functionNode);
        } else if (isMemberLike) {
            TSNode fieldId = findFirstChild(functionNode, "field_identifier");
            if (fieldId == null) {
                fieldId = findFirstChild(functionNode, "identifier");
            }
            if (fieldId != null) {
                methodName = getNodeText(source, fieldId);
            }

            if (deepBaseObjectLookup) {
                objectName = extractBaseObjectIdentifier(source, functionNode);
            } else {
                TSNode objectNode = functionNode.getNamedChild(0);
                if (objectNode != null && "identifier".equals(objectNode.getType())) {
                    objectName = getNodeText(source, objectNode);
                }
            }

            if (objectName != null) {
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

    private static String extractBaseObjectIdentifier(String source, TSNode fieldExpressionNode) {
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

    public static List<String> extractDeclaredVariableNames(String source, TSNode declarationNode) {
        List<String> names = new ArrayList<>();

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

    public static boolean containsNonFieldFunctionDeclaration(TSNode declarationNode, CCppAnalysisOptions options) {
        return containsNonFieldFunctionDeclaration(declarationNode, options.allowFunctionReturningFunctionPointer);
    }

    public static boolean containsNonFieldFunctionDeclaration(TSNode declarationNode, boolean allowFunctionReturningFunctionPointer) {
        List<TSNode> functionDeclarators = findAllDescendants(declarationNode, "function_declarator");
        for (TSNode functionDeclarator : functionDeclarators) {
            if (isTopLevelFunctionDeclaration(functionDeclarator, allowFunctionReturningFunctionPointer)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTopLevelFunctionDeclaration(TSNode functionDeclarator, CCppAnalysisOptions options) {
        return isTopLevelFunctionDeclaration(functionDeclarator, options.allowFunctionReturningFunctionPointer);
    }

    public static boolean isTopLevelFunctionDeclaration(TSNode functionDeclarator, boolean allowFunctionReturningFunctionPointer) {
        if (functionDeclarator == null || functionDeclarator.isNull()) {
            return false;
        }

        TSNode ancestor = functionDeclarator.getParent();
        while (ancestor != null && !ancestor.isNull()) {
            if ("pointer_declarator".equals(ancestor.getType())) {
                return false;
            }
            ancestor = ancestor.getParent();
        }

        TSNode nestedDeclarator = getChildByFieldName(functionDeclarator, "declarator");
        while (nestedDeclarator != null && !nestedDeclarator.isNull()) {
            if ("pointer_declarator".equals(nestedDeclarator.getType())) {
                return false;
            }
            if ("parenthesized_declarator".equals(nestedDeclarator.getType())
                && !findAllDescendants(nestedDeclarator, "pointer_declarator").isEmpty()
                && (!allowFunctionReturningFunctionPointer
                || !isFunctionReturningFunctionPointerDeclaration(nestedDeclarator))) {
                return false;
            }
            nestedDeclarator = getChildByFieldName(nestedDeclarator, "declarator");
        }

        return true;
    }

    private static boolean isFunctionReturningFunctionPointerDeclaration(TSNode parenthesizedDeclarator) {
        if (parenthesizedDeclarator == null || parenthesizedDeclarator.isNull()) {
            return false;
        }

        List<TSNode> pointerDeclarators = findAllDescendants(parenthesizedDeclarator, "pointer_declarator");
        for (TSNode pointerDeclarator : pointerDeclarators) {
            TSNode pointedDeclarator = getChildByFieldName(pointerDeclarator, "declarator");
            if (pointedDeclarator == null || pointedDeclarator.isNull()) {
                continue;
            }

            if ("function_declarator".equals(pointedDeclarator.getType())
                || !findAllDescendants(pointedDeclarator, "function_declarator").isEmpty()) {
                return true;
            }
        }

        return false;
    }

    public static String resolveFileScopeFieldType(String source, TSNode declarationNode, String fieldName, String baseType) {
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

    public static String extractDeclaratorName(String source, TSNode declarator) {
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

        TSNode fieldIdentifier = findFirstChild(declarator, "field_identifier");
        if (fieldIdentifier != null) {
            return getNodeText(source, fieldIdentifier);
        }

        if ("identifier".equals(declarator.getType()) || "type_identifier".equals(declarator.getType())) {
            return getNodeText(source, declarator);
        }

        List<TSNode> fieldIds = findAllDescendants(declarator, "field_identifier");
        if (!fieldIds.isEmpty()) {
            return getNodeText(source, fieldIds.get(0));
        }

        List<TSNode> ids = findAllDescendants(declarator, "identifier");
        for (TSNode id : ids) {
            if (isInsideNodeType(id, "parameter_declaration")) {
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

    public static boolean hasVariadicParameter(String source, TSNode parameterList) {
        if (parameterList == null || parameterList.isNull()) {
            return false;
        }
        for (int i = 0; i < parameterList.getNamedChildCount(); i++) {
            TSNode child = parameterList.getNamedChild(i);
            if (child != null && !child.isNull() && "variadic_parameter".equals(child.getType())) {
                return true;
            }
        }
        String text = getNodeText(source, parameterList);
        return text != null && text.contains("...");
    }

    public static void addModifiersFromSpecifiers(List<String> target,
                                                   String source,
                                                   TSNode node,
                                                   List<String> specifierNodeTypes,
                                                   CCppAnalysisOptions options,
                                                   boolean collectingTypeModifiers) {
        addModifiersFromSpecifiers(target, source, node, specifierNodeTypes,
            options.includeAnonymousSpecifiers, collectingTypeModifiers);
    }

    public static void addModifiersFromSpecifiers(List<String> target,
                                                   String source,
                                                   TSNode node,
                                                   List<String> specifierNodeTypes,
                                                   boolean includeAnonymous,
                                                   boolean collectingTypeModifiers) {
        if (target == null || node == null || node.isNull() || specifierNodeTypes == null || specifierNodeTypes.isEmpty()) {
            return;
        }

        for (String specifierNodeType : specifierNodeTypes) {
            List<TSNode> specifiers = includeAnonymous
                ? findAllDescendantsIncludingAnonymous(node, specifierNodeType)
                : findAllDescendants(node, specifierNodeType);
            for (TSNode specifierNode : specifiers) {
                if (isInsideNodeType(specifierNode, "parameter_declaration")) {
                    continue;
                }

                if (collectingTypeModifiers
                    && (isInsideNodeType(specifierNode, "function_definition")
                    || isInsideNodeType(specifierNode, "declaration")
                    || isInsideNodeType(specifierNode, "field_declaration"))) {
                    continue;
                }

                String token = getNodeText(source, specifierNode);
                if (token == null) {
                    continue;
                }
                String normalized = token.trim();
                if (!normalized.isEmpty() && !target.contains(normalized)) {
                    target.add(normalized);
                }
            }
        }
    }

    private static List<TSNode> findAllDescendantsIncludingAnonymous(TSNode root, String nodeType) {
        List<TSNode> result = new ArrayList<>();
        if (root == null || root.isNull()) {
            return result;
        }

        Deque<TSNode> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            TSNode current = stack.pop();
            if (current == null || current.isNull()) {
                continue;
            }

            if (nodeType.equals(current.getType())) {
                result.add(current);
            }

            for (int i = current.getChildCount() - 1; i >= 0; i--) {
                TSNode child = current.getChild(i);
                if (child != null && !child.isNull()) {
                    stack.push(child);
                }
            }
        }

        return result;
    }

    public static String extractDeclarationTypeText(String source, TSNode node, CCppAnalysisOptions options) {
        return extractDeclarationTypeText(source, node, options.includeAutoInDeclarationType);
    }

    public static String extractDeclarationTypeText(String source, TSNode node, boolean includeAuto) {
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

        if (includeAuto) {
            TSNode autoType = findFirstChild(node, "auto");
            if (autoType != null) {
                return getNodeText(source, autoType);
            }
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

    public static boolean isInsideNodeType(TSNode node, String ancestorType) {
        return findAncestorOfType(node, null, ancestorType) != null;
    }

    public static TSNode findAncestorOfType(TSNode startNode, TSNode boundaryNode, String type) {
        TSNode current = startNode == null ? null : startNode.getParent();
        while (current != null && !current.isNull() && (boundaryNode == null || !isSameNode(current, boundaryNode))) {
            if (type.equals(current.getType())) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    public static boolean containsKeyword(String text, String keyword) {
        int index = text.indexOf(keyword);
        while (index >= 0) {
            boolean startOk = index == 0 || !isIdentifierChar(text.charAt(index - 1));
            int endIndex = index + keyword.length();
            boolean endOk = endIndex >= text.length() || !isIdentifierChar(text.charAt(endIndex));
            if (startOk && endOk) {
                return true;
            }
            index = text.indexOf(keyword, index + 1);
        }
        return false;
    }

    public static String normalizeWhitespace(String text) {
        if (text == null) {
            return null;
        }

        StringBuilder out = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                pendingSpace = true;
                continue;
            }
            if (pendingSpace && out.length() > 0) {
                out.append(' ');
            }
            out.append(c);
            pendingSpace = false;
        }

        return out.toString().trim();
    }

    public static int findLastIdentifierOccurrence(String text, String identifier) {
        if (text == null || identifier == null || identifier.isBlank()) {
            return -1;
        }

        int from = text.length();
        while (true) {
            int idx = text.lastIndexOf(identifier, from - 1);
            if (idx < 0) {
                return -1;
            }

            boolean leftOk = idx == 0 || !isIdentifierChar(text.charAt(idx - 1));
            int end = idx + identifier.length();
            boolean rightOk = end >= text.length() || !isIdentifierChar(text.charAt(end));
            if (leftOk && rightOk) {
                return idx;
            }

            from = idx;
        }
    }

    public static String normalizeFunctionPointerSpacing(String text) {
        if (text == null) {
            return null;
        }

        String normalized = text;
        while (normalized.contains("(* )")) {
            normalized = normalized.replace("(* )", "(*)");
        }
        while (normalized.contains("(& )")) {
            normalized = normalized.replace("(& )", "(&)");
        }

        return normalized;
    }

    public static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
