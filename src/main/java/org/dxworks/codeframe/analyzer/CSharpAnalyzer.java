package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.*;
import org.treesitter.TSNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.dxworks.codeframe.analyzer.TreeSitterHelper.*;

public class CSharpAnalyzer implements LanguageAnalyzer {
    
    @Override
    public FileAnalysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        FileAnalysis analysis = new FileAnalysis();
        analysis.filePath = filePath;
        analysis.language = "csharp";
        
        analysis.packageName = extractNamespace(rootNode, sourceCode);
        analysis.imports.addAll(extractImports(rootNode, sourceCode));
        
        analyzeTypes(rootNode, sourceCode, analysis);
        
        return analysis;
    }
    
    private String extractNamespace(TSNode rootNode, String sourceCode) {
        // Try file-scoped namespace first (C# 10+): namespace X;
        TSNode fileScopedNs = findFirstDescendant(rootNode, "file_scoped_namespace_declaration");
        if (fileScopedNs != null) {
            return extractNamespaceNameFrom(fileScopedNs, sourceCode);
        }
        
        // Try block namespace: namespace X { }
        TSNode namespaceDecl = findFirstDescendant(rootNode, "namespace_declaration");
        if (namespaceDecl != null) {
            return extractNamespaceNameFrom(namespaceDecl, sourceCode);
        }
        
        return null;
    }
    
    private String extractNamespaceNameFrom(TSNode namespaceNode, String sourceCode) {
        TSNode nameNode = namespaceNode.getNamedChild(0);
        return nameNode != null ? getNodeText(sourceCode, nameNode) : null;
    }
    
    private List<String> extractImports(TSNode rootNode, String sourceCode) {
        List<String> imports = new ArrayList<>();
        List<TSNode> usingDirectives = findAllDescendants(rootNode, "using_directive");
        for (TSNode usingDir : usingDirectives) {
            imports.add(getNodeText(sourceCode, usingDir).trim());
        }
        return imports;
    }
    
    private void analyzeTypes(TSNode rootNode, String sourceCode, FileAnalysis analysis) {
        List<TSNode> allClasses = findAllDescendants(rootNode, "class_declaration");
        Set<Integer> nestedClassIds = identifyNestedClasses(allClasses);
        
        // Process only top-level classes recursively (they will add nested classes themselves)
        for (TSNode classDecl : allClasses) {
            if (!nestedClassIds.contains(classDecl.getStartByte())) {
                analyzeClassRecursively(sourceCode, classDecl, analysis);
            }
        }
        
        // Process interfaces
        List<TSNode> interfaces = findAllDescendants(rootNode, "interface_declaration");
        for (TSNode interfaceDecl : interfaces) {
            analysis.types.add(analyzeInterface(sourceCode, interfaceDecl));
        }
    }
    
    private Set<Integer> identifyNestedClasses(List<TSNode> allClasses) {
        Set<Integer> nestedClassIds = new HashSet<>();
        for (TSNode classDecl : allClasses) {
            TSNode classBody = findFirstChild(classDecl, "declaration_list");
            if (classBody != null) {
                List<TSNode> nested = findAllDescendants(classBody, "class_declaration");
                for (TSNode n : nested) {
                    nestedClassIds.add(n.getStartByte());
                }
            }
        }
        return nestedClassIds;
    }
    
    private void analyzeClassRecursively(String source, TSNode classDecl, FileAnalysis analysis) {
        analyzeClassRecursivelyInto(source, classDecl, analysis.types);
    }
    
    private void analyzeClassRecursivelyInto(String source, TSNode classDecl, List<TypeInfo> targetList) {
        TypeInfo typeInfo = analyzeClass(source, classDecl);
        targetList.add(typeInfo);
        
        TSNode classBody = findFirstChild(classDecl, "declaration_list");
        if (classBody == null) {
            return;
        }
        
        // Collect members from this class only (not from nested classes)
        typeInfo.fields.addAll(collectFieldsFromBody(source, classBody));
        typeInfo.methods.addAll(collectMethodsFromBody(source, classBody, typeInfo.name));
        
        // Recursively process nested classes - add them to THIS type's types list
        List<TSNode> nestedClasses = findAllChildren(classBody, "class_declaration");
        for (TSNode nested : nestedClasses) {
            analyzeClassRecursivelyInto(source, nested, typeInfo.types);
        }
    }
    
    private List<MethodInfo> collectMethodsFromBody(String source, TSNode classBody, String className) {
        List<MethodInfo> methods = new ArrayList<>();
        List<TSNode> methodNodes = findAllChildren(classBody, "method_declaration");
        for (TSNode method : methodNodes) {
            methods.add(analyzeMethod(source, method, className));
        }
        return methods;
    }
    
    private TypeInfo analyzeClass(String source, TSNode classDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "class";
        
        // Extract modifiers and visibility
        extractModifiersAndVisibility(source, classDecl, typeInfo.modifiers, typeInfo);
        
        // Extract attributes (C#'s annotations)
        extractAttributes(source, classDecl, typeInfo.annotations);
        
        // Get class name
        TSNode nameNode = findFirstChild(classDecl, "identifier");
        if (nameNode != null) {
            typeInfo.name = getNodeText(source, nameNode);
        }
        
        // Get base list (extends and implements)
        TSNode baseListNode = findFirstChild(classDecl, "base_list");
        if (baseListNode != null) {
            // In C#, base_list contains simple_base_type nodes
            // We need to extract the actual type names from these nodes
            List<String> baseTypeNames = new ArrayList<>();
            for (int i = 0; i < baseListNode.getNamedChildCount(); i++) {
                TSNode baseTypeNode = baseListNode.getNamedChild(i);
                String typeName = getNodeText(source, baseTypeNode);
                if (typeName != null && !typeName.isEmpty()) {
                    baseTypeNames.add(typeName);
                }
            }
            
            // In C#, the first base type is the base class if it doesn't start with 'I'
            // Convention: interfaces typically start with 'I' (e.g., IDisposable, IEnumerable)
            // However, this is just a convention. Without semantic analysis, we use this heuristic.
            for (int i = 0; i < baseTypeNames.size(); i++) {
                String typeName = baseTypeNames.get(i);
                // If it's the first type and doesn't look like an interface name, treat as base class
                if (i == 0 && !looksLikeInterface(typeName)) {
                    typeInfo.extendsType = typeName;
                } else {
                    typeInfo.implementsInterfaces.add(typeName);
                }
            }
        }
        
        return typeInfo;
    }
    
    private TypeInfo analyzeInterface(String source, TSNode interfaceDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "interface";
        
        TSNode nameNode = findFirstChild(interfaceDecl, "identifier");
        if (nameNode != null) {
            typeInfo.name = getNodeText(source, nameNode);
        }
        
        // Interfaces can extend other interfaces
        TSNode baseListNode = findFirstChild(interfaceDecl, "base_list");
        if (baseListNode != null) {
            for (int i = 0; i < baseListNode.getNamedChildCount(); i++) {
                TSNode baseTypeNode = baseListNode.getNamedChild(i);
                String typeName = getNodeText(source, baseTypeNode);
                if (typeName != null && !typeName.isEmpty()) {
                    typeInfo.implementsInterfaces.add(typeName);
                }
            }
        }
        
        return typeInfo;
    }
    
    /**
     * Heuristic to determine if a type name looks like an interface.
     * In C#, interfaces conventionally start with 'I' followed by an uppercase letter.
     */
    private boolean looksLikeInterface(String typeName) {
        if (typeName == null || typeName.length() < 2) {
            return false;
        }
        // Check if starts with 'I' followed by uppercase letter (e.g., IDisposable, IDetectTypeDesignSmell)
        return typeName.charAt(0) == 'I' && Character.isUpperCase(typeName.charAt(1));
    }
    
    private MethodInfo analyzeMethod(String source, TSNode methodDecl, String className) {
        MethodInfo methodInfo = new MethodInfo();
        
        extractModifiersAndVisibility(source, methodDecl, methodInfo.modifiers, methodInfo);
        extractAttributes(source, methodDecl, methodInfo.annotations);
        
        methodInfo.name = extractMethodName(source, methodDecl);
        methodInfo.returnType = extractReturnType(source, methodDecl);
        
        Map<String, String> paramTypes = extractParameters(source, methodDecl, methodInfo);
        
        TSNode bodyNode = findMethodBody(methodDecl);
        if (bodyNode != null) {
            analyzeMethodBody(source, bodyNode, methodInfo, className, paramTypes);
        }
        
        return methodInfo;
    }
    
    private String extractMethodName(String source, TSNode methodDecl) {
        for (int i = 0; i < methodDecl.getNamedChildCount(); i++) {
            TSNode child = methodDecl.getNamedChild(i);
            if ("identifier".equals(child.getType())) {
                return getNodeText(source, child);
            }
        }
        return null;
    }
    
    private String extractReturnType(String source, TSNode methodDecl) {
        for (int i = 0; i < methodDecl.getNamedChildCount(); i++) {
            if ("returns".equals(methodDecl.getFieldNameForChild(i))) {
                TSNode returnTypeNode = methodDecl.getNamedChild(i);
                return getNodeText(source, returnTypeNode);
            }
        }
        return null;
    }
    
    private Map<String, String> extractParameters(String source, TSNode methodDecl, MethodInfo methodInfo) {
        Map<String, String> paramTypes = new HashMap<>();
        TSNode paramsNode = findFirstChild(methodDecl, "parameter_list");
        if (paramsNode == null) {
            return paramTypes;
        }
        
        List<TSNode> params = findAllChildren(paramsNode, "parameter");
        for (TSNode param : params) {
            ParameterInfo paramInfo = extractParameter(source, param);
            if (paramInfo.name != null) {
                methodInfo.parameters.add(new Parameter(paramInfo.name, paramInfo.type));
                if (paramInfo.type != null) {
                    paramTypes.put(paramInfo.name, paramInfo.type);
                }
            }
        }
        return paramTypes;
    }
    
    private ParameterInfo extractParameter(String source, TSNode param) {
        String paramName = null;
        String paramType = null;
        
        for (int i = 0; i < param.getNamedChildCount(); i++) {
            TSNode child = param.getNamedChild(i);
            String fieldName = param.getFieldNameForChild(i);
            
            if ("name".equals(fieldName)) {
                paramName = getNodeText(source, child);
            } else if ("type".equals(fieldName)) {
                paramType = getNodeText(source, child);
            }
        }
        return new ParameterInfo(paramName, paramType);
    }
    
    private TSNode findMethodBody(TSNode methodDecl) {
        TSNode bodyNode = findFirstChild(methodDecl, "block");
        if (bodyNode != null) {
            return bodyNode;
        }
        // Expression-bodied method (using =>)
        return findFirstChild(methodDecl, "arrow_expression_clause");
    }
    
    private static class ParameterInfo {
        final String name;
        final String type;
        
        ParameterInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }
    
    private void analyzeMethodBody(String source, TSNode bodyNode, MethodInfo methodInfo, 
                                   String className, Map<String, String> paramTypes) {
        // Build local types map
        Map<String, String> localTypes = new HashMap<>(paramTypes);
        
        // Find local variable declarations and track types
        List<TSNode> localVarDecls = findAllDescendants(bodyNode, "local_declaration_statement");
        for (TSNode varDecl : localVarDecls) {
            // In C#, local_declaration_statement contains a variable_declaration
            // which has the type as its first child
            String declaredType = null;
            TSNode variableDeclaration = findFirstChild(varDecl, "variable_declaration");
            if (variableDeclaration != null && variableDeclaration.getNamedChildCount() > 0) {
                TSNode typeNode = variableDeclaration.getNamedChild(0);
                if (typeNode != null) {
                    declaredType = getNodeText(source, typeNode);
                }
            }
            
            List<TSNode> declarators = findAllDescendants(varDecl, "variable_declarator");
            for (TSNode declarator : declarators) {
                TSNode varName = findFirstChild(declarator, "identifier");
                if (varName != null) {
                    String name = getNodeText(source, varName);
                    methodInfo.localVariables.add(name);
                    if (declaredType != null) {
                        localTypes.put(name, declaredType);
                    }
                }
            }
        }
        
        // Track which member_access_expression nodes are part of invocations
        // so we can distinguish property accesses from method calls
        Set<TSNode> memberAccessInInvocations = new HashSet<>();
        
        // Find invocation expressions (method calls)
        // Note: We use findAllDescendants which recursively finds ALL invocation_expression nodes,
        // including nested ones in chained calls like obj.Method1().Method2()
        List<TSNode> invocations = findAllDescendants(bodyNode, "invocation_expression");
        for (TSNode invocation : invocations) {
            // The function node contains the method being called
            TSNode functionNode = null;
            for (int i = 0; i < invocation.getNamedChildCount(); i++) {
                String fieldName = invocation.getFieldNameForChild(i);
                if ("function".equals(fieldName)) {
                    functionNode = invocation.getNamedChild(i);
                    break;
                }
            }
            
            if (functionNode != null) {
                // Mark this member_access as part of an invocation
                if ("member_access_expression".equals(functionNode.getType())) {
                    memberAccessInInvocations.add(functionNode);
                }
                
                String methodName = null;
                String objectName = null;
                String objectType = null;
                
                if ("member_access_expression".equals(functionNode.getType())) {
                    // obj.Method() or Type.Method() call
                    // In C#, member_access_expression has the structure: expression . identifier
                    // The first child is the object/type, the last child is the method name
                    TSNode expressionNode = null;
                    TSNode nameNode = null;
                    
                    int childCount = functionNode.getNamedChildCount();
                    if (childCount >= 2) {
                        // First child is the expression (object/type)
                        expressionNode = functionNode.getNamedChild(0);
                        // Last child is the identifier (method name)
                        nameNode = functionNode.getNamedChild(childCount - 1);
                    }
                    
                    if (nameNode != null && "identifier".equals(nameNode.getType())) {
                        methodName = getNodeText(source, nameNode);
                    } else if (nameNode != null && "generic_name".equals(nameNode.getType())) {
                        // For generic methods like Method<T>()
                        TSNode idNode = findFirstChild(nameNode, "identifier");
                        if (idNode != null) {
                            methodName = getNodeText(source, idNode);
                        }
                    }
                    
                    if (expressionNode != null) {
                        String exprType = expressionNode.getType();
                        String exprText = getNodeText(source, expressionNode);
                        
                        if ("identifier".equals(exprType)) {
                            objectName = exprText;
                            objectType = localTypes.get(objectName);
                            
                            // Check for 'this'
                            if ("this".equals(objectName) && className != null) {
                                objectType = className;
                            }
                        } else if ("this_expression".equals(exprType)) {
                            objectName = "this";
                            objectType = className;
                        } else if ("generic_name".equals(exprType) || "qualified_name".equals(exprType)) {
                            // Static method call like Maybe<T>.From(), Metrics.Metrics.For()
                            // These are type names, not variable references
                            objectName = exprText;
                            objectType = exprText;
                        } else if ("member_access_expression".equals(exprType) || "invocation_expression".equals(exprType)) {
                            // Chained call like obj.Method1().Method2() or obj.Prop.Method()
                            // We cannot determine the runtime type without semantic analysis
                            // Leave objectType and objectName as null
                            objectName = null;
                            objectType = null;
                        }
                    }
                } else if ("identifier".equals(functionNode.getType())) {
                    // Direct method call
                    methodName = getNodeText(source, functionNode);
                } else if ("generic_name".equals(functionNode.getType())) {
                    // Generic method call like Method<T>()
                    // Extract just the method name (before the type arguments)
                    TSNode idNode = findFirstChild(functionNode, "identifier");
                    if (idNode != null) {
                        methodName = getNodeText(source, idNode);
                    }
                }
                
                if (methodName != null) {
                    addOrIncrementMethodCall(methodInfo, methodName, objectType, objectName);
                }
            }
        }
        
        // Find property accesses (member_access_expression NOT part of invocations)
        List<TSNode> allMemberAccesses = findAllDescendants(bodyNode, "member_access_expression");
        for (TSNode memberAccess : allMemberAccesses) {
            // Skip if this is part of an invocation (method call)
            if (memberAccessInInvocations.contains(memberAccess)) {
                continue;
            }
            
            // This is a property access like obj.Property or Type.StaticProperty
            String propertyName = null;
            String objectName = null;
            String objectType = null;
            
            int childCount = memberAccess.getNamedChildCount();
            if (childCount >= 2) {
                // First child is the expression (object/type)
                TSNode expressionNode = memberAccess.getNamedChild(0);
                // Last child is the identifier (property name)
                TSNode nameNode = memberAccess.getNamedChild(childCount - 1);
                
                if (nameNode != null && "identifier".equals(nameNode.getType())) {
                    propertyName = getNodeText(source, nameNode);
                }
                
                if (expressionNode != null) {
                    String exprType = expressionNode.getType();
                    String exprText = getNodeText(source, expressionNode);
                    
                    if ("identifier".equals(exprType)) {
                        objectName = exprText;
                        objectType = localTypes.get(objectName);
                        
                        if ("this".equals(objectName) && className != null) {
                            objectType = className;
                        }
                    } else if ("this_expression".equals(exprType)) {
                        objectName = "this";
                        objectType = className;
                    } else if ("generic_name".equals(exprType) || "qualified_name".equals(exprType)) {
                        // Static property access
                        objectName = exprText;
                        objectType = exprText;
                    }
                }
            }
            
            if (propertyName != null) {
                addOrIncrementMethodCall(methodInfo, propertyName, objectType, objectName);
            }
        }
        
        sortMethodCalls(methodInfo);
    }
    
    private void addOrIncrementMethodCall(MethodInfo methodInfo, String methodName, String objectType, String objectName) {
        for (MethodCall existingCall : methodInfo.methodCalls) {
            if (existingCall.matches(methodName, objectType, objectName)) {
                existingCall.callCount++;
                return;
            }
        }
        methodInfo.methodCalls.add(new MethodCall(methodName, objectType, objectName));
    }
    
    private void sortMethodCalls(MethodInfo methodInfo) {
        methodInfo.methodCalls.sort((a, b) -> {
            int nameCompare = a.methodName.compareTo(b.methodName);
            if (nameCompare != 0) return nameCompare;
            
            if (a.objectType != null && b.objectType != null) {
                int typeCompare = a.objectType.compareTo(b.objectType);
                if (typeCompare != 0) return typeCompare;
            } else if (a.objectType != null) {
                return 1;
            } else if (b.objectType != null) {
                return -1;
            }
            
            if (a.objectName != null && b.objectName != null) {
                return a.objectName.compareTo(b.objectName);
            } else if (a.objectName != null) {
                return 1;
            } else if (b.objectName != null) {
                return -1;
            }
            return 0;
        });
    }
    
    @SuppressWarnings("unused")
    private List<FieldInfo> collectFields(String source, TSNode classDecl) {
        List<FieldInfo> fields = new ArrayList<>();
        List<TSNode> fieldDecls = findAllDescendants(classDecl, "field_declaration");
        
        for (TSNode field : fieldDecls) {
            // Get field type from variable_declaration -> type
            String declaredType = null;
            TSNode variableDeclaration = findFirstChild(field, "variable_declaration");
            if (variableDeclaration != null && variableDeclaration.getNamedChildCount() > 0) {
                TSNode typeNode = variableDeclaration.getNamedChild(0);
                if (typeNode != null) {
                    declaredType = getNodeText(source, typeNode);
                }
            }
            
            // Variable names are under variable_declaration -> variable_declarator
            List<TSNode> declarators = variableDeclaration != null
                ? findAllDescendants(variableDeclaration, "variable_declarator")
                : findAllDescendants(field, "variable_declarator");
            for (TSNode declarator : declarators) {
                TSNode varName = findFirstChild(declarator, "identifier");
                if (varName != null) {
                    FieldInfo fieldInfo = new FieldInfo();
                    fieldInfo.name = getNodeText(source, varName);
                    fieldInfo.type = declaredType;
                    
                    // Extract modifiers and visibility
                    extractModifiersAndVisibility(source, field, fieldInfo.modifiers, fieldInfo);
                    
                    // Extract attributes
                    extractAttributes(source, field, fieldInfo.annotations);
                    
                    fields.add(fieldInfo);
                }
            }
        }
        
        return fields;
    }

    private List<FieldInfo> collectFieldsFromBody(String source, TSNode classBody) {
        List<FieldInfo> fields = new ArrayList<>();
        if (classBody == null) return fields;
        // Only direct field_declaration children of this class body
        List<TSNode> fieldDecls = findAllChildren(classBody, "field_declaration");
        for (TSNode field : fieldDecls) {
            // Get field type from variable_declaration -> type
            String declaredType = null;
            TSNode variableDeclaration = findFirstChild(field, "variable_declaration");
            if (variableDeclaration != null && variableDeclaration.getNamedChildCount() > 0) {
                TSNode typeNode = variableDeclaration.getNamedChild(0);
                if (typeNode != null) {
                    declaredType = getNodeText(source, typeNode);
                }
            }
            // Variable names are under variable_declaration -> variable_declarator
            List<TSNode> declarators = variableDeclaration != null
                ? findAllDescendants(variableDeclaration, "variable_declarator")
                : findAllDescendants(field, "variable_declarator");
            for (TSNode declarator : declarators) {
                TSNode varName = findFirstChild(declarator, "identifier");
                if (varName != null) {
                    FieldInfo fieldInfo = new FieldInfo();
                    fieldInfo.name = getNodeText(source, varName);
                    fieldInfo.type = declaredType;
                    // Extract modifiers and visibility
                    extractModifiersAndVisibility(source, field, fieldInfo.modifiers, fieldInfo);
                    // Extract attributes
                    extractAttributes(source, field, fieldInfo.annotations);
                    fields.add(fieldInfo);
                }
            }
        }
        return fields;
    }
    
    private void extractModifiersAndVisibility(String source, TSNode node, List<String> modifiers, Object target) {
        // In C#, Tree-sitter uses a generic "modifier" node type for all modifiers
        int childCount = node.getNamedChildCount();
        
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getNamedChild(i);
            String type = child.getType();
            
            // Check if this is a modifier node
            if ("modifier".equals(type)) {
                String modText = getNodeText(source, child);
                modifiers.add(modText);
                
                // Set visibility based on the modifier text
                if ("public".equals(modText) || "private".equals(modText) || 
                    "protected".equals(modText) || "internal".equals(modText)) {
                    if (target instanceof TypeInfo) {
                        ((TypeInfo) target).visibility = modText;
                    } else if (target instanceof MethodInfo) {
                        ((MethodInfo) target).visibility = modText;
                    } else if (target instanceof FieldInfo) {
                        ((FieldInfo) target).visibility = modText;
                    }
                }
            }
        }
        
        // Default visibility is internal for types, private for members
        if (target instanceof TypeInfo && ((TypeInfo) target).visibility == null) {
            ((TypeInfo) target).visibility = "internal";
        } else if (target instanceof MethodInfo && ((MethodInfo) target).visibility == null) {
            ((MethodInfo) target).visibility = "private";
        } else if (target instanceof FieldInfo && ((FieldInfo) target).visibility == null) {
            ((FieldInfo) target).visibility = "private";
        }
    }
    
    private void extractAttributes(String source, TSNode node, List<String> annotations) {
        // Look for attribute_list nodes as children
        int childCount = node.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getNamedChild(i);
            if ("attribute_list".equals(child.getType())) {
                String raw = getNodeText(source, child);
                if (raw != null) {
                    String normalized = normalizeInline(raw);
                    annotations.add(normalized);
                }
            }
        }
    }
}
