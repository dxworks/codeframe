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

public class CppAnalyzer implements LanguageAnalyzer {
    private static final CCppAnalysisOptions OPTIONS = CCppAnalysisOptions.CPP;

    @Override
    public FileAnalysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        FileAnalysis analysis = new FileAnalysis();
        analysis.filePath = filePath;
        analysis.language = "cpp";

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

    private void extractNestedTypesFromFieldDeclaration(String source, TSNode fieldDeclaration, TypeInfo typeInfo, Set<Integer> seenNestedTypeNodes) {
        CCppHelper.collectSeenTypes(fieldDeclaration, seenNestedTypeNodes, typeInfo.types,
            "class_specifier", null, n -> analyzeClass(source, n));
        CCppHelper.collectSeenTypes(fieldDeclaration, seenNestedTypeNodes, typeInfo.types,
            "struct_specifier", null, n -> CCppHelper.analyzeStructLike(source, n, "struct", OPTIONS));
        CCppHelper.collectSeenTypes(fieldDeclaration, seenNestedTypeNodes, typeInfo.types,
            "union_specifier", null, n -> CCppHelper.analyzeStructLike(source, n, "union", OPTIONS));
        CCppHelper.collectSeenTypes(fieldDeclaration, seenNestedTypeNodes, typeInfo.types,
            "enum_specifier", null, n -> CCppHelper.analyzeEnum(source, n, OPTIONS));
    }

    private void extractNamespaceMethods(String source, TSNode namespaceNode, TypeInfo namespaceInfo, Map<String, String> fileScopeTypes) {
        for (TSNode functionNode : findAllDescendants(namespaceNode, "function_definition")) {
            if (CCppHelper.isInsideTypeContainer(functionNode, namespaceNode)) {
                continue;
            }
            if (isInsideNestedNamespace(functionNode, namespaceNode)) {
                continue;
            }
            if (CCppHelper.isInsideNodeType(functionNode, "template_declaration")) {
                continue;
            }
            MethodInfo method = analyzeFunctionDefinition(source, functionNode, null, fileScopeTypes);
            if (method != null && method.name != null) {
                namespaceInfo.methods.add(method);
            }
        }

        for (TSNode templateNode : findAllDescendants(namespaceNode, "template_declaration")) {
            if (CCppHelper.isInsideTypeContainer(templateNode, namespaceNode)) {
                continue;
            }
            if (isInsideNestedNamespace(templateNode, namespaceNode)) {
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
        if (method != null) {
            CCppHelper.addModifierIfAbsent(method.modifiers, "template");
        }
    }

    private void applyTemplateModifier(TypeInfo typeInfo) {
        if (typeInfo != null) {
            CCppHelper.addModifierIfAbsent(typeInfo.modifiers, "template");
        }
    }

    private boolean isInsideNestedNamespace(TSNode node, TSNode namespaceNode) {
        TSNode current = node == null ? null : node.getParent();
        while (current != null && !current.isNull()) {
            if (isSameNode(current, namespaceNode)) {
                return false;
            }
            if ("namespace_definition".equals(current.getType())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void extractNamespaceTypes(String source, TSNode namespaceNode, TypeInfo namespaceInfo, Set<Integer> seenTypeNodes) {
        java.util.function.Predicate<TSNode> notInNestedNs = n -> !isInsideNestedNamespace(n, namespaceNode);
        CCppHelper.collectSeenTypes(namespaceNode, seenTypeNodes, namespaceInfo.types,
            "type_definition", notInNestedNs, n -> CCppHelper.analyzeTypedef(source, n));
        CCppHelper.collectSeenTypes(namespaceNode, seenTypeNodes, namespaceInfo.types,
            "alias_declaration", notInNestedNs, n -> analyzeUsingAlias(source, n));
        CCppHelper.collectSeenTypes(namespaceNode, seenTypeNodes, namespaceInfo.types,
            "class_specifier", notInNestedNs, n -> analyzeClass(source, n));
        CCppHelper.collectSeenTypes(namespaceNode, seenTypeNodes, namespaceInfo.types,
            "template_declaration", notInNestedNs, n -> analyzeTemplateType(source, n));
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

        for (TSNode nestedNamespace : findAllDescendants(namespaceNode, "namespace_definition")) {
            if (!isDirectNestedNamespace(nestedNamespace, namespaceNode)) {
                continue;
            }
            TypeInfo nested = analyzeNamespace(source, nestedNamespace, fileScopeTypes);
            if (nested != null) {
                namespaceInfo.types.add(nested);
            }
        }

        return namespaceInfo;
    }

    private void addTypeIfNamed(FileAnalysis analysis, TypeInfo typeInfo) {
        if (typeInfo != null && typeInfo.name != null) {
            analysis.types.add(typeInfo);
        }
    }

    private void addNamespaceIfPresent(String source, TSNode namespaceNode, FileAnalysis analysis) {
        TypeInfo namespaceInfo = analyzeNamespace(source, namespaceNode, Map.of());
        if (namespaceInfo != null) {
            analysis.types.add(namespaceInfo);
        }
    }

    private void collectTypedefsFromNode(String source, TSNode child, FileAnalysis analysis, Set<Integer> seenTypedefNodes) {
        if ("type_definition".equals(child.getType())) {
            addTypeIfNamed(analysis, CCppHelper.analyzeTypedef(source, child));
            seenTypedefNodes.add(child.getStartByte());
        }

        for (TSNode typedefNode : findAllDescendants(child, "type_definition")) {
            if (!seenTypedefNodes.add(typedefNode.getStartByte())) {
                continue;
            }
            addTypeIfNamed(analysis, CCppHelper.analyzeTypedef(source, typedefNode));
        }
    }

    private boolean collectNamespacesFromNode(String source,
                                              TSNode child,
                                              FileAnalysis analysis,
                                              Set<Integer> seenNamespaceNodes) {
        if ("namespace_definition".equals(child.getType())) {
            addNamespaceIfPresent(source, child, analysis);
            seenNamespaceNodes.add(child.getStartByte());
            return true;
        }

        for (TSNode namespaceNode : findAllDescendants(child, "namespace_definition")) {
            if (CCppHelper.isInsideNodeType(namespaceNode, "namespace_definition")) {
                continue;
            }
            if (!seenNamespaceNodes.add(namespaceNode.getStartByte())) {
                continue;
            }
            addNamespaceIfPresent(source, namespaceNode, analysis);
        }

        return false;
    }

    private void extractTopLevelTypes(String source, TSNode rootNode, FileAnalysis analysis) {
        Set<Integer> seenTypeNodes = new HashSet<>();
        Set<Integer> seenTypedefNodes = new HashSet<>();
        Set<Integer> seenNamespaceNodes = new HashSet<>();
        for (int i = 0; i < rootNode.getNamedChildCount(); i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) {
                continue;
            }

            String childType = child.getType();
            if ("class_specifier".equals(childType)) {
                addTypeIfNamed(analysis, analyzeClass(source, child));
            }

            if ("alias_declaration".equals(childType)) {
                addTypeIfNamed(analysis, analyzeUsingAlias(source, child));
            }

            collectTypedefsFromNode(source, child, analysis, seenTypedefNodes);

            if ("template_declaration".equals(childType)) {
                addTypeIfNamed(analysis, analyzeTemplateType(source, child));
            }

            if (collectNamespacesFromNode(source, child, analysis, seenNamespaceNodes)) {
                continue;
            }

            CCppHelper.collectSeenTypes(child, seenTypeNodes, analysis.types,
                "struct_specifier", null, n -> CCppHelper.analyzeStructLike(source, n, "struct", OPTIONS));
            CCppHelper.collectSeenTypes(child, seenTypeNodes, analysis.types,
                "union_specifier", null, n -> CCppHelper.analyzeStructLike(source, n, "union", OPTIONS));
            CCppHelper.collectSeenTypes(child, seenTypeNodes, analysis.types,
                "enum_specifier", null, n -> CCppHelper.analyzeEnum(source, n, OPTIONS));
        }
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
            typeInfo = CCppHelper.analyzeStructLike(source, declaration, "struct", OPTIONS);
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
        typeInfo.name = CCppHelper.extractTypeName(source, classNode);
        CCppHelper.addModifiersFromSpecifiers(typeInfo.modifiers, source, classNode, OPTIONS.typeSpecifierNodeTypes,
            OPTIONS, CCppHelper.isTypeContainerNode(classNode));
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
            return null;
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
                if (CCppHelper.containsNonFieldFunctionDeclaration(member, OPTIONS)) {
                    MethodInfo method = analyzeClassMethod(source, member, typeInfo.name);
                    if (method != null && method.name != null) {
                        applyVisibility(method, currentVisibility);
                        typeInfo.methods.add(method);
                    }
                    continue;
                }
                extractFieldMembers(source, member, typeInfo, currentVisibility);
                extractNestedTypesFromFieldDeclaration(source, member, typeInfo, seenNestedTypeNodes);
            }

            if ("function_definition".equals(memberType)
                || "declaration".equals(memberType)
                || "template_declaration".equals(memberType)
                || "friend_declaration".equals(memberType)) {
                MethodInfo method = analyzeClassMethod(source, member, typeInfo.name);
                if (method != null && method.name != null) {
                    applyVisibility(method, currentVisibility);
                    typeInfo.methods.add(method);
                }
            }

            if ("class_specifier".equals(memberType)) {
                CCppHelper.addTypeIfNamed(typeInfo.types, analyzeClass(source, member));
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
        if (field != null) {
            CCppHelper.applyVisibility(field.modifiers, visibility, v -> field.visibility = v);
        }
    }

    private void applyVisibility(MethodInfo method, String visibility) {
        if (method != null) {
            CCppHelper.applyVisibility(method.modifiers, visibility, v -> method.visibility = v);
        }
    }

    private void extractFieldMembers(String source, TSNode fieldDecl, TypeInfo typeInfo, String visibility) {
        String fieldType = CCppHelper.extractDeclarationTypeText(source, fieldDecl, OPTIONS);
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
            CCppHelper.addModifiersFromSpecifiers(field.modifiers, source, fieldDecl, OPTIONS.fieldSpecifierNodeTypes,
                OPTIONS, CCppHelper.isTypeContainerNode(fieldDecl));
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
        if ("friend_declaration".equals(node.getType())) {
            return analyzeFriendDeclaration(source, node, className);
        }

        if ("field_declaration".equals(node.getType())) {
            return analyzeFunctionDeclaration(source, node, className);
        }

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
        normalizeAssignmentOperatorReturnType(source, functionNode, method);
        normalizeCtorDtorReturnType(method, className);
        CCppHelper.extractParameters(source, declarator, method, OPTIONS);
        CCppHelper.addModifiersFromSpecifiers(method.modifiers, source, functionNode, OPTIONS.functionSpecifierNodeTypes,
            OPTIONS, CCppHelper.isTypeContainerNode(functionNode));
        addDeletedDefaultedModifiers(method, source, functionNode);

        TSNode body = getChildByFieldName(functionNode, "body");
        if (body == null) {
            body = findFirstChild(functionNode, "compound_statement");
        }
        if (body == null) {
            method.isDeclarationOnly = true;
        }
        if (body != null) {
            CCppHelper.analyzeMethodBody(source, body, method, fileScopeTypes, OPTIONS);
        }

        return method;
    }

    private MethodInfo analyzeFunctionDeclaration(String source, TSNode declarationNode, String className) {
        TSNode declarator = CCppHelper.resolveFunctionDeclarator(declarationNode);
        if (declarator == null) {
            return null;
        }
        if (!CCppHelper.isTopLevelFunctionDeclaration(declarator, OPTIONS)) {
            return null;
        }

        MethodInfo method = new MethodInfo();
        method.name = extractCppMethodName(source, declarator, className);
        method.returnType = extractFunctionReturnType(source, declarationNode, declarator);
        normalizeAssignmentOperatorReturnType(source, declarationNode, method);
        method.isDeclarationOnly = true;
        normalizeCtorDtorReturnType(method, className);
        CCppHelper.extractParameters(source, declarator, method, OPTIONS);
        CCppHelper.addModifiersFromSpecifiers(method.modifiers, source, declarationNode, OPTIONS.functionSpecifierNodeTypes,
            OPTIONS, CCppHelper.isTypeContainerNode(declarationNode));
        addDeletedDefaultedModifiers(method, source, declarationNode);
        return method;
    }

    private void addDeletedDefaultedModifiers(MethodInfo method, String source, TSNode declarationNode) {
        if (method == null || declarationNode == null || declarationNode.isNull()) {
            return;
        }
        String declarationText = getNodeText(source, declarationNode);
        if (declarationText == null) {
            return;
        }
        if (declarationText.contains("= delete")) {
            CCppHelper.addModifierIfAbsent(method.modifiers, "deleted");
        }
        if (declarationText.contains("= default")) {
            CCppHelper.addModifierIfAbsent(method.modifiers, "defaulted");
        }
    }

    private void normalizeAssignmentOperatorReturnType(String source, TSNode declarationNode, MethodInfo method) {
        if (method == null || declarationNode == null || declarationNode.isNull()) {
            return;
        }
        if (!"operator=".equals(method.name)) {
            return;
        }
        String declarationType = CCppHelper.extractDeclarationTypeText(source, declarationNode, OPTIONS);
        if (declarationType == null || declarationType.isBlank()) {
            return;
        }

        if (method.returnType == null || method.returnType.isBlank()) {
            method.returnType = declarationType;
            return;
        }

        if (method.returnType.contains("operator")) {
            method.returnType = method.returnType.contains("&") ? declarationType + " &" : declarationType;
        }
    }

    private MethodInfo analyzeFriendDeclaration(String source, TSNode friendNode, String className) {
        if (friendNode == null || friendNode.isNull()) {
            return null;
        }

        MethodInfo method = null;
        TSNode declaration = getChildByFieldName(friendNode, "declaration");
        if (declaration != null && !declaration.isNull()) {
            if ("function_definition".equals(declaration.getType())) {
                method = analyzeFunctionDefinition(source, declaration, className, Map.of());
            } else if ("declaration".equals(declaration.getType())) {
                method = analyzeFunctionDeclaration(source, declaration, className);
            }
        }

        if (method == null) {
            TSNode functionDefinition = findFirstChild(friendNode, "function_definition");
            if (functionDefinition != null && !functionDefinition.isNull()) {
                method = analyzeFunctionDefinition(source, functionDefinition, className, Map.of());
            }
        }

        if (method == null) {
            TSNode declarationNode = findFirstChild(friendNode, "declaration");
            if (declarationNode != null && !declarationNode.isNull()) {
                method = analyzeFunctionDeclaration(source, declarationNode, className);
            }
        }

        if (method != null) {
            CCppHelper.addModifierIfAbsent(method.modifiers, "friend");
        }
        return method;
    }

    private boolean isDirectNestedNamespace(TSNode candidate, TSNode namespaceNode) {
        if (candidate == null || candidate.isNull() || namespaceNode == null || namespaceNode.isNull()) {
            return false;
        }

        TSNode current = candidate.getParent();
        while (current != null && !current.isNull()) {
            if (isSameNode(current, namespaceNode)) {
                return true;
            }
            if ("namespace_definition".equals(current.getType())) {
                return false;
            }
            current = current.getParent();
        }
        return false;
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

        for (TSNode operatorDescendant : findAllDescendants(declarator, "operator_name")) {
            String operatorName = getNodeText(source, operatorDescendant);
            if (operatorName != null && !operatorName.isBlank()) {
                return operatorName;
            }
        }

        String operatorFromText = extractOperatorNameFromText(source, declarator);
        if (operatorFromText != null) {
            return operatorFromText;
        }

        TSNode destructorNode = findFirstChild(declarator, "destructor_name");
        if (destructorNode != null) {
            String dtor = getNodeText(source, destructorNode);
            if (dtor != null) {
                return dtor;
            }
        }

        String regular = CCppHelper.extractDeclaratorName(source, declarator);
        if (regular != null) {
            return regular;
        }

        if (className != null) {
            return className;
        }
        return null;
    }

    private String extractOperatorNameFromText(String source, TSNode declarator) {
        String declaratorText = getNodeText(source, declarator);
        if (declaratorText == null) {
            return null;
        }

        int operatorStart = declaratorText.indexOf("operator");
        if (operatorStart < 0) {
            return null;
        }

        int signatureEnd = declaratorText.indexOf('(', operatorStart);
        if (signatureEnd < 0) {
            signatureEnd = declaratorText.length();
        }

        String operatorName = declaratorText.substring(operatorStart, signatureEnd).trim();
        return operatorName.isBlank() ? null : operatorName;
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
            if (CCppHelper.isInsideTypeOrTemplateContainer(functionNode)) {
                continue;
            }
            MethodInfo method = analyzeFunctionDefinition(source, functionNode, null, fileScopeTypes);
            if (method != null && method.name != null) {
                analysis.methods.add(method);
            }
        }

        for (TSNode declarationNode : findAllDescendants(container, "declaration")) {
            if (CCppHelper.isInsideTypeOrFunctionOrTemplateContainer(declarationNode)) {
                continue;
            }
            MethodInfo method = analyzeFunctionDeclaration(source, declarationNode, null);
            if (method != null && method.name != null) {
                analysis.methods.add(method);
            }
        }

        for (TSNode templateNode : findAllDescendants(container, "template_declaration")) {
            if (CCppHelper.isInsideTypeContainer(templateNode)) {
                continue;
            }
            MethodInfo method = addTemplateFunctionMethod(source, templateNode, fileScopeTypes);
            if (method != null && method.name != null) {
                analysis.methods.add(method);
            }
        }
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
                    if (CCppHelper.isInsideTypeOrFunctionOrTemplateContainer(declarationNode)) {
                        continue;
                    }
                    addTopLevelFieldsFromDeclaration(source, declarationNode, analysis, fileScopeTypes);
                }
            }
        }
        return fileScopeTypes;
    }

    private void addTopLevelFieldsFromDeclaration(String source, TSNode declarationNode, FileAnalysis analysis, Map<String, String> fileScopeTypes) {
        CCppHelper.addFieldsFromDeclaration(source, declarationNode, analysis.fields, fileScopeTypes, OPTIONS);
    }

    private TypeInfo analyzeUsingAlias(String source, TSNode aliasNode) {
        if (aliasNode == null || aliasNode.isNull()) {
            return null;
        }
        String aliasText = getNodeText(source, aliasNode);
        if (aliasText == null || aliasText.isBlank()) {
            return null;
        }

        String normalized = aliasText.trim();
        if (!normalized.startsWith("using ")) {
            return null;
        }

        int eq = normalized.indexOf('=');
        if (eq < 0) {
            return null;
        }

        String left = normalized.substring("using ".length(), eq).trim();
        String right = normalized.substring(eq + 1).trim();
        if (right.endsWith(";")) {
            right = right.substring(0, right.length() - 1).trim();
        }
        if (left.isEmpty() || right.isEmpty()) {
            return null;
        }

        TypeInfo aliasInfo = new TypeInfo();
        aliasInfo.kind = "typedef";
        aliasInfo.name = left;
        aliasInfo.extendsType = right;
        return aliasInfo;
    }

    private void extractTopLevelCalls(String source, TSNode rootNode, FileAnalysis analysis, Map<String, String> fileScopeTypes) {
        for (TSNode call : findAllDescendants(rootNode, "call_expression")) {
            if (CCppHelper.isInsideTypeOrFunctionContainer(call)) {
                continue;
            }

            MethodInfo collector = new MethodInfo();
            CCppHelper.extractMethodCall(source, call, collector, fileScopeTypes == null ? Map.of() : fileScopeTypes, OPTIONS);
            analysis.methodCalls.addAll(collector.methodCalls);
        }

        analysis.methodCalls.sort(METHOD_CALL_COMPARATOR);
    }

    private String extractFunctionReturnType(String source, TSNode declarationNode, TSNode functionDeclarator) {
        String returnType = CCppHelper.extractFunctionReturnTypeText(source, declarationNode, functionDeclarator, OPTIONS);
        if (returnType == null || functionDeclarator == null || functionDeclarator.isNull()) {
            return returnType;
        }

        TSNode current = functionDeclarator.getParent();
        while (current != null && !current.isNull() && !isSameNode(current, declarationNode)) {
            if ("reference_declarator".equals(current.getType())) {
                return returnType + "&";
            }
            current = current.getParent();
        }

        return returnType;
    }
}
