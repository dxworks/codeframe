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

public class CppAnalyzer implements LanguageAnalyzer {
    @Override
    public FileAnalysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        FileAnalysis analysis = new FileAnalysis();
        analysis.filePath = filePath;
        analysis.language = "cpp";

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
        for (TSNode includeNode : findAllDescendants(rootNode, "preproc_include")) {
            String text = getNodeText(source, includeNode);
            if (text != null && !text.isBlank()) {
                analysis.imports.add(text.trim());
            }
        }
    }

    private void extractNestedTypesFromFieldDeclaration(String source, TSNode fieldDeclaration, TypeInfo typeInfo, Set<Integer> seenNestedTypeNodes) {
        for (TSNode classNode : findAllDescendants(fieldDeclaration, "class_specifier")) {
            if (!markSeen(seenNestedTypeNodes, classNode)) {
                continue;
            }
            TypeInfo nested = analyzeClass(source, classNode);
            if (nested != null && nested.name != null) {
                typeInfo.types.add(nested);
            }
        }

        for (TSNode structNode : findAllDescendants(fieldDeclaration, "struct_specifier")) {
            if (!markSeen(seenNestedTypeNodes, structNode)) {
                continue;
            }
            TypeInfo nested = analyzeStructLike(source, structNode, "struct");
            if (nested != null && nested.name != null) {
                typeInfo.types.add(nested);
            }
        }

        for (TSNode unionNode : findAllDescendants(fieldDeclaration, "union_specifier")) {
            if (!markSeen(seenNestedTypeNodes, unionNode)) {
                continue;
            }
            TypeInfo nested = analyzeStructLike(source, unionNode, "union");
            if (nested != null && nested.name != null) {
                typeInfo.types.add(nested);
            }
        }

        for (TSNode enumNode : findAllDescendants(fieldDeclaration, "enum_specifier")) {
            if (!markSeen(seenNestedTypeNodes, enumNode)) {
                continue;
            }
            TypeInfo nested = analyzeEnum(source, enumNode);
            if (nested != null && nested.name != null) {
                typeInfo.types.add(nested);
            }
        }
    }

    private TSNode resolveFunctionDeclarator(TSNode declarationNode) {
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

    private void extractNamespaceMethods(String source, TSNode namespaceNode, TypeInfo namespaceInfo, Map<String, String> fileScopeTypes) {
        for (TSNode functionNode : findAllDescendants(namespaceNode, "function_definition")) {
            if (isInsideTypeContainer(functionNode, namespaceNode)) {
                continue;
            }
            if (isInsideNodeType(functionNode, "template_declaration")) {
                continue;
            }
            MethodInfo method = analyzeFunctionDefinition(source, functionNode, null, fileScopeTypes);
            if (method != null && method.name != null) {
                namespaceInfo.methods.add(method);
            }
        }

        for (TSNode templateNode : findAllDescendants(namespaceNode, "template_declaration")) {
            if (isInsideTypeContainer(templateNode, namespaceNode)) {
                continue;
            }
            MethodInfo method = addTemplateFunctionMethod(source, templateNode, fileScopeTypes);
            if (method != null && method.name != null) {
                namespaceInfo.methods.add(method);
            }
        }
    }

    private MethodInfo addTemplateFunctionMethod(String source, TSNode templateNode, Map<String, String> fileScopeTypes) {
        TSNode declaration = getChildByFieldName(templateNode, "declaration");
        if (declaration == null && templateNode.getNamedChildCount() > 0) {
            declaration = templateNode.getNamedChild(templateNode.getNamedChildCount() - 1);
        }
        if (declaration == null || declaration.isNull()) {
            return null;
        }

        TSNode templateParams = findFirstChild(templateNode, "template_parameter_list");
        MethodInfo method = null;
        if ("function_definition".equals(declaration.getType())) {
            method = analyzeFunctionDefinition(source, declaration, null, fileScopeTypes);
        } else if ("declaration".equals(declaration.getType())) {
            method = analyzeFunctionDeclaration(source, declaration, null);
        }

        if (method != null && method.name != null && templateParams != null) {
            method.name = method.name + getNodeText(source, templateParams);
        }
        applyTemplateModifier(method);
        return method;
    }

    private void applyTemplateModifier(MethodInfo method) {
        if (method == null) {
            return;
        }
        if (!method.modifiers.contains("template")) {
            method.modifiers.add("template");
        }
    }

    private void applyTemplateModifier(TypeInfo typeInfo) {
        if (typeInfo == null) {
            return;
        }
        if (!typeInfo.modifiers.contains("template")) {
            typeInfo.modifiers.add("template");
        }
    }

    private boolean isInsideTypeContainer(TSNode node, TSNode stopNode) {
        TSNode current = node == null ? null : node.getParent();
        while (current != null && !current.isNull()) {
            if (isSameNode(current, stopNode)) {
                return false;
            }
            String t = current.getType();
            if ("class_specifier".equals(t) || "struct_specifier".equals(t) || "union_specifier".equals(t)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void extractNamespaceTypes(String source, TSNode namespaceNode, TypeInfo namespaceInfo, Set<Integer> seenTypeNodes) {
        for (TSNode classNode : findAllDescendants(namespaceNode, "class_specifier")) {
            if (!markSeen(seenTypeNodes, classNode)) {
                continue;
            }
            TypeInfo classInfo = analyzeClass(source, classNode);
            if (classInfo != null && classInfo.name != null) {
                namespaceInfo.types.add(classInfo);
            }
        }

        for (TSNode templateNode : findAllDescendants(namespaceNode, "template_declaration")) {
            if (!markSeen(seenTypeNodes, templateNode)) {
                continue;
            }
            TypeInfo templateType = analyzeTemplateType(source, templateNode);
            if (templateType != null && templateType.name != null) {
                namespaceInfo.types.add(templateType);
            }
        }
    }

    private String extractNamespaceName(String source, TSNode namespaceNode) {
        TSNode nameNode = getChildByFieldName(namespaceNode, "name");
        if (nameNode != null && !nameNode.isNull()) {
            String name = getNodeText(source, nameNode);
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        TSNode identifierNode = findFirstChild(namespaceNode, "identifier");
        if (identifierNode != null && !identifierNode.isNull()) {
            String name = getNodeText(source, identifierNode);
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return "";
    }

    private TypeInfo analyzeNamespace(String source, TSNode namespaceNode, Map<String, String> fileScopeTypes) {
        TypeInfo namespaceInfo = new TypeInfo();
        namespaceInfo.kind = "namespace";
        namespaceInfo.name = extractNamespaceName(source, namespaceNode);

        Set<Integer> seenTypeNodes = new HashSet<>();
        extractNamespaceTypes(source, namespaceNode, namespaceInfo, seenTypeNodes);
        extractNamespaceMethods(source, namespaceNode, namespaceInfo, fileScopeTypes);

        for (TSNode nestedNamespace : findAllChildren(namespaceNode, "namespace_definition")) {
            TypeInfo nested = analyzeNamespace(source, nestedNamespace, fileScopeTypes);
            if (nested != null) {
                namespaceInfo.types.add(nested);
            }
        }

        return namespaceInfo;
    }

    private void extractTopLevelTypes(String source, TSNode rootNode, FileAnalysis analysis) {
        Set<Integer> seenTypeNodes = new HashSet<>();
        for (int i = 0; i < rootNode.getNamedChildCount(); i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) {
                continue;
            }

            String childType = child.getType();
            if ("class_specifier".equals(childType)) {
                TypeInfo classInfo = analyzeClass(source, child);
                if (classInfo != null && classInfo.name != null) {
                    analysis.types.add(classInfo);
                }
            }

            if ("template_declaration".equals(childType)) {
                TypeInfo templateType = analyzeTemplateType(source, child);
                if (templateType != null && templateType.name != null) {
                    analysis.types.add(templateType);
                }
            }

            if ("namespace_definition".equals(childType)) {
                TypeInfo namespaceInfo = analyzeNamespace(source, child, Map.of());
                if (namespaceInfo != null) {
                    analysis.types.add(namespaceInfo);
                }
                continue;
            }

            for (TSNode structNode : findAllDescendants(child, "struct_specifier")) {
                if (markSeen(seenTypeNodes, structNode)) {
                    TypeInfo info = analyzeStructLike(source, structNode, "struct");
                    if (info != null && info.name != null) {
                        analysis.types.add(info);
                    }
                }
            }

            for (TSNode unionNode : findAllDescendants(child, "union_specifier")) {
                if (markSeen(seenTypeNodes, unionNode)) {
                    TypeInfo info = analyzeStructLike(source, unionNode, "union");
                    if (info != null && info.name != null) {
                        analysis.types.add(info);
                    }
                }
            }

            for (TSNode enumNode : findAllDescendants(child, "enum_specifier")) {
                if (markSeen(seenTypeNodes, enumNode)) {
                    TypeInfo info = analyzeEnum(source, enumNode);
                    if (info != null && info.name != null) {
                        analysis.types.add(info);
                    }
                }
            }
        }
    }

    private boolean markSeen(Set<Integer> seen, TSNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        return seen.add(node.getStartByte());
    }

    private TypeInfo analyzeTemplateType(String source, TSNode templateNode) {
        TSNode declaration = getChildByFieldName(templateNode, "declaration");
        if (declaration == null) {
            declaration = templateNode.getNamedChild(templateNode.getNamedChildCount() - 1);
        }

        if (declaration == null || declaration.isNull()) {
            return null;
        }

        TypeInfo typeInfo;
        if ("class_specifier".equals(declaration.getType())) {
            typeInfo = analyzeClass(source, declaration);
        } else if ("struct_specifier".equals(declaration.getType())) {
            typeInfo = analyzeStructLike(source, declaration, "struct");
        } else {
            return null;
        }

        TSNode parameters = findFirstChild(templateNode, "template_parameter_list");
        if (parameters != null && typeInfo.name != null) {
            typeInfo.name = typeInfo.name + getNodeText(source, parameters);
        }
        applyTemplateModifier(typeInfo);

        return typeInfo;
    }

    private TypeInfo analyzeClass(String source, TSNode classNode) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "class";
        typeInfo.name = extractTypeName(source, classNode);
        Set<Integer> seenNestedTypeNodes = new HashSet<>();
        String currentVisibility = "private";

        TSNode baseClause = findFirstChild(classNode, "base_class_clause");
        if (baseClause != null) {
            String baseText = getNodeText(source, baseClause);
            if (baseText != null && !baseText.isBlank()) {
                typeInfo.extendsType = baseText.trim();
            }
        }

        TSNode body = findFirstChild(classNode, "field_declaration_list");
        if (body == null) {
            return typeInfo;
        }

        for (int i = 0; i < body.getNamedChildCount(); i++) {
            TSNode member = body.getNamedChild(i);
            if (member == null || member.isNull()) {
                continue;
            }

            String memberType = member.getType();
            if ("access_specifier".equals(memberType)) {
                currentVisibility = extractAccessSpecifierVisibility(source, member);
                continue;
            }

            if ("field_declaration".equals(memberType)) {
                extractFieldMembers(source, member, typeInfo, currentVisibility);
                extractNestedTypesFromFieldDeclaration(source, member, typeInfo, seenNestedTypeNodes);
            }

            if ("function_definition".equals(memberType)
                || "declaration".equals(memberType)
                || "template_declaration".equals(memberType)) {
                MethodInfo method = analyzeClassMethod(source, member, typeInfo.name);
                if (method != null && method.name != null) {
                    applyVisibility(method, currentVisibility);
                    typeInfo.methods.add(method);
                }
            }

            if ("class_specifier".equals(memberType)) {
                TypeInfo nested = analyzeClass(source, member);
                if (nested != null && nested.name != null) {
                    typeInfo.types.add(nested);
                }
            }
        }

        return typeInfo;
    }

    private String extractAccessSpecifierVisibility(String source, TSNode accessSpecifierNode) {
        if (accessSpecifierNode == null || accessSpecifierNode.isNull()) {
            return null;
        }
        String raw = getNodeText(source, accessSpecifierNode);
        if (raw == null) {
            return null;
        }
        String value = raw.replace(":", "").trim();
        if ("public".equals(value) || "private".equals(value) || "protected".equals(value)) {
            return value;
        }
        return null;
    }

    private void applyVisibility(FieldInfo field, String visibility) {
        if (field == null || visibility == null || visibility.isBlank()) {
            return;
        }
        field.visibility = visibility;
        if (!field.modifiers.contains(visibility)) {
            field.modifiers.add(visibility);
        }
    }

    private void applyVisibility(MethodInfo method, String visibility) {
        if (method == null || visibility == null || visibility.isBlank()) {
            return;
        }
        method.visibility = visibility;
        if (!method.modifiers.contains(visibility)) {
            method.modifiers.add(visibility);
        }
    }

    private void extractFieldMembers(String source, TSNode fieldDecl, TypeInfo typeInfo, String visibility) {
        String fieldType = extractDeclarationTypeText(source, fieldDecl);
        List<TSNode> names = findAllDescendants(fieldDecl, "field_identifier");
        if (names.isEmpty()) {
            names = findAllDescendants(fieldDecl, "identifier");
        }

        for (TSNode nameNode : names) {
            if (isInsideNestedFieldDeclaration(nameNode, fieldDecl)) {
                continue;
            }
            String name = getNodeText(source, nameNode);
            if (name == null || name.isBlank()) {
                continue;
            }
            FieldInfo field = new FieldInfo();
            field.name = name;
            field.type = fieldType;
            applyVisibility(field, visibility);
            typeInfo.fields.add(field);
        }
    }

    private boolean isInsideNestedFieldDeclaration(TSNode node, TSNode declarationNode) {
        TSNode current = node == null ? null : node.getParent();
        while (current != null && !current.isNull() && !isSameNode(current, declarationNode)) {
            if ("field_declaration".equals(current.getType())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private MethodInfo analyzeClassMethod(String source, TSNode node, String className) {
        if ("template_declaration".equals(node.getType())) {
            TSNode declaration = getChildByFieldName(node, "declaration");
            if (declaration == null && node.getNamedChildCount() > 0) {
                declaration = node.getNamedChild(node.getNamedChildCount() - 1);
            }
            if (declaration == null) {
                return null;
            }
            MethodInfo templated = analyzeClassMethod(source, declaration, className);
            TSNode templateParams = findFirstChild(node, "template_parameter_list");
            if (templated != null && templated.name != null && templateParams != null) {
                templated.name = templated.name + getNodeText(source, templateParams);
            }
            applyTemplateModifier(templated);
            return templated;
        }

        if ("function_definition".equals(node.getType())) {
            return analyzeFunctionDefinition(source, node, className, Map.of());
        }

        if ("declaration".equals(node.getType())) {
            return analyzeFunctionDeclaration(source, node, className);
        }

        return null;
    }

    private MethodInfo analyzeFunctionDefinition(String source, TSNode functionNode, String className, Map<String, String> fileScopeTypes) {
        MethodInfo method = new MethodInfo();

        TSNode declarator = getChildByFieldName(functionNode, "declarator");
        method.name = extractCppMethodName(source, declarator, className);
        method.returnType = extractFunctionReturnType(source, functionNode, declarator);
        normalizeCtorDtorReturnType(method, className);
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

    private MethodInfo analyzeFunctionDeclaration(String source, TSNode declarationNode, String className) {
        TSNode declarator = resolveFunctionDeclarator(declarationNode);
        if (declarator == null) {
            return null;
        }
        if (!isTopLevelFunctionDeclaration(declarator)) {
            return null;
        }

        MethodInfo method = new MethodInfo();
        method.name = extractCppMethodName(source, declarator, className);
        method.returnType = extractFunctionReturnType(source, declarationNode, declarator);
        normalizeCtorDtorReturnType(method, className);
        extractParameters(source, declarator, method);
        return method;
    }

    private void normalizeCtorDtorReturnType(MethodInfo method, String className) {
        if (method.name == null || className == null) {
            return;
        }
        if (method.name.equals(className) || method.name.equals("~" + className)) {
            method.returnType = null;
        }
    }

    private String extractCppMethodName(String source, TSNode declarator, String className) {
        if (declarator == null || declarator.isNull()) {
            return null;
        }

        TSNode operatorNode = findFirstChild(declarator, "operator_name");
        if (operatorNode != null) {
            return getNodeText(source, operatorNode);
        }

        TSNode destructorNode = findFirstChild(declarator, "destructor_name");
        if (destructorNode != null) {
            String dtor = getNodeText(source, destructorNode);
            if (dtor != null) {
                return dtor;
            }
        }

        String regular = extractDeclaratorName(source, declarator);
        if (regular != null) {
            return regular;
        }

        if (className != null) {
            return className;
        }
        return null;
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
            for (TSNode enumerator : findAllChildren(enumeratorList, "enumerator")) {
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
        for (TSNode fieldDecl : findAllChildren(fieldList, "field_declaration")) {
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
                fieldInfo.type = fieldType;
                typeInfo.fields.add(fieldInfo);
            }
        }
    }

    private void extractTopLevelMethods(String source, TSNode rootNode, FileAnalysis analysis, Map<String, String> fileScopeTypes) {
        for (int i = 0; i < rootNode.getNamedChildCount(); i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) {
                continue;
            }

            if ("namespace_definition".equals(child.getType())) {
                continue;
            }

            if ("function_definition".equals(child.getType())) {
                MethodInfo method = analyzeFunctionDefinition(source, child, null, fileScopeTypes);
                if (method != null && method.name != null) {
                    analysis.methods.add(method);
                }
                continue;
            }

            if ("declaration".equals(child.getType())) {
                MethodInfo method = analyzeFunctionDeclaration(source, child, null);
                if (method != null && method.name != null) {
                    analysis.methods.add(method);
                }
            }

            if ("template_declaration".equals(child.getType())) {
                MethodInfo method = addTemplateFunctionMethod(source, child, fileScopeTypes);
                if (method != null && method.name != null) {
                    analysis.methods.add(method);
                }
            }

            if ("linkage_specification".equals(child.getType()) || child.getType().startsWith("preproc_")) {
                extractContainerMethods(source, child, analysis, fileScopeTypes);
            }
        }
    }

    private void extractContainerMethods(String source, TSNode container, FileAnalysis analysis, Map<String, String> fileScopeTypes) {
        for (TSNode functionNode : findAllDescendants(container, "function_definition")) {
            if (isInsideNodeType(functionNode, "class_specifier")
                || isInsideNodeType(functionNode, "struct_specifier")
                || isInsideNodeType(functionNode, "union_specifier")
                || isInsideNodeType(functionNode, "enum_specifier")
                || isInsideNodeType(functionNode, "template_declaration")) {
                continue;
            }
            MethodInfo method = analyzeFunctionDefinition(source, functionNode, null, fileScopeTypes);
            if (method != null && method.name != null) {
                analysis.methods.add(method);
            }
        }

        for (TSNode declarationNode : findAllDescendants(container, "declaration")) {
            if (isInsideNodeType(declarationNode, "class_specifier")
                || isInsideNodeType(declarationNode, "struct_specifier")
                || isInsideNodeType(declarationNode, "union_specifier")
                || isInsideNodeType(declarationNode, "enum_specifier")
                || isInsideNodeType(declarationNode, "function_definition")
                || isInsideNodeType(declarationNode, "template_declaration")) {
                continue;
            }
            MethodInfo method = analyzeFunctionDeclaration(source, declarationNode, null);
            if (method != null && method.name != null) {
                analysis.methods.add(method);
            }
        }

        for (TSNode templateNode : findAllDescendants(container, "template_declaration")) {
            if (isInsideNodeType(templateNode, "class_specifier")
                || isInsideNodeType(templateNode, "struct_specifier")
                || isInsideNodeType(templateNode, "union_specifier")
                || isInsideNodeType(templateNode, "enum_specifier")) {
                continue;
            }
            MethodInfo method = addTemplateFunctionMethod(source, templateNode, fileScopeTypes);
            if (method != null && method.name != null) {
                analysis.methods.add(method);
            }
        }
    }

    private void extractParameters(String source, TSNode declarator, MethodInfo method) {
        if (declarator == null || declarator.isNull()) {
            return;
        }

        TSNode parameterList = findFirstChild(declarator, "parameter_list");
        if (parameterList == null) {
            return;
        }

        for (TSNode param : findAllChildren(parameterList, "parameter_declaration")) {
            String paramName = extractFirstIdentifier(source, param);
            String paramType = extractParameterTypeText(source, param, paramName);
            if (paramName != null) {
                method.parameters.add(new Parameter(paramName, paramType));
            }
        }
    }

    private String extractParameterTypeText(String source, TSNode parameterNode, String parameterName) {
        if (parameterNode == null || parameterNode.isNull()) {
            return null;
        }

        String fullText = getNodeText(source, parameterNode);
        if (fullText == null || fullText.isBlank()) {
            return extractDeclarationTypeText(source, parameterNode);
        }

        String normalized = normalizeWhitespace(fullText);
        if (parameterName == null || parameterName.isBlank()) {
            return normalized;
        }

        int idx = findLastIdentifierOccurrence(normalized, parameterName);
        if (idx < 0) {
            return extractDeclarationTypeText(source, parameterNode);
        }

        String typeText = normalizeWhitespace(
            normalized.substring(0, idx) + " " + normalized.substring(idx + parameterName.length())
        );
        typeText = normalizeFunctionPointerSpacing(typeText);

        if (typeText == null || typeText.isBlank()) {
            return extractDeclarationTypeText(source, parameterNode);
        }
        return typeText;
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

        for (TSNode declaration : findAllDescendants(body, "declaration")) {
            String typeText = extractDeclarationTypeText(source, declaration);
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
        } else if ("field_expression".equals(functionNode.getType()) || "qualified_identifier".equals(functionNode.getType())) {
            TSNode fieldId = findFirstChild(functionNode, "field_identifier");
            if (fieldId == null) {
                fieldId = findFirstChild(functionNode, "identifier");
            }
            if (fieldId != null) {
                methodName = getNodeText(source, fieldId);
            }

            TSNode objectNode = functionNode.getNamedChild(0);
            if (objectNode != null && "identifier".equals(objectNode.getType())) {
                objectName = getNodeText(source, objectNode);
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
            if (child == null || child.isNull()) {
                continue;
            }

            if ("declaration".equals(child.getType())) {
                addTopLevelFieldsFromDeclaration(source, child, analysis, fileScopeTypes);
            }

            if ("linkage_specification".equals(child.getType()) || child.getType().startsWith("preproc_")) {
                for (TSNode declarationNode : findAllDescendants(child, "declaration")) {
                    if (isInsideNodeType(declarationNode, "class_specifier")
                        || isInsideNodeType(declarationNode, "struct_specifier")
                        || isInsideNodeType(declarationNode, "union_specifier")
                        || isInsideNodeType(declarationNode, "enum_specifier")
                        || isInsideNodeType(declarationNode, "function_definition")
                        || isInsideNodeType(declarationNode, "template_declaration")) {
                        continue;
                    }
                    addTopLevelFieldsFromDeclaration(source, declarationNode, analysis, fileScopeTypes);
                }
            }
        }
        return fileScopeTypes;
    }

    private void addTopLevelFieldsFromDeclaration(String source, TSNode declarationNode, FileAnalysis analysis, Map<String, String> fileScopeTypes) {
        if (containsNonFieldFunctionDeclaration(declarationNode)) {
            return;
        }

        String typeText = extractDeclarationTypeText(source, declarationNode);
        for (String name : extractDeclaredVariableNames(source, declarationNode)) {
            if (name == null || name.isBlank()) {
                continue;
            }

            String fieldType = resolveFileScopeFieldType(source, declarationNode, name, typeText);

            FieldInfo field = new FieldInfo();
            field.name = name;
            field.type = fieldType;
            analysis.fields.add(field);
            if (fieldType != null) {
                fileScopeTypes.put(name, fieldType);
            }
        }
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
        for (TSNode call : findAllDescendants(rootNode, "call_expression")) {
            if (isInsideNodeType(call, "function_definition")
                || isInsideNodeType(call, "class_specifier")
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
                && !findAllDescendants(nestedDeclarator, "pointer_declarator").isEmpty()) {
                return false;
            }
            nestedDeclarator = getChildByFieldName(nestedDeclarator, "declarator");
        }

        return true;
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

        TSNode fieldIdentifier = findFirstChild(declarator, "field_identifier");
        if (fieldIdentifier != null) {
            return getNodeText(source, fieldIdentifier);
        }

        if ("identifier".equals(declarator.getType())) {
            return getNodeText(source, declarator);
        }

        if ("type_identifier".equals(declarator.getType())) {
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

        return null;
    }

    private boolean isInsideNodeType(TSNode node, String ancestorType) {
        TSNode current = node == null ? null : node.getParent();
        while (current != null && !current.isNull()) {
            if (ancestorType.equals(current.getType())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private String extractFirstIdentifier(String source, TSNode node) {
        List<TSNode> ids = findAllDescendants(node, "identifier");
        if (ids.isEmpty()) {
            return null;
        }
        return getNodeText(source, ids.get(ids.size() - 1));
    }

    private String extractFunctionReturnType(String source, TSNode declarationNode, TSNode functionDeclarator) {
        String baseType = extractDeclarationTypeText(source, declarationNode);
        if (baseType == null || functionDeclarator == null || functionDeclarator.isNull()) {
            return baseType;
        }

        TSNode current = functionDeclarator.getParent();
        while (current != null && !current.isNull() && !isSameNode(current, declarationNode)) {
            if ("reference_declarator".equals(current.getType())) {
                return baseType + "&";
            }
            current = current.getParent();
        }

        return baseType;
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

        TSNode autoType = findFirstChild(node, "auto");
        if (autoType != null) {
            return getNodeText(source, autoType);
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
