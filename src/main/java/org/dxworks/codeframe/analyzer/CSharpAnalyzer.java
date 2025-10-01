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
        
        // Extract namespace (supports both block and file-scoped namespaces)
        // Try file-scoped namespace first (C# 10+): namespace X;
        TSNode fileScopedNs = findFirstDescendant(rootNode, "file_scoped_namespace_declaration");
        if (fileScopedNs != null) {
            // First child is typically the qualified_name or identifier
            TSNode nameNode = fileScopedNs.getNamedChild(0);
            if (nameNode != null) {
                analysis.packageName = getNodeText(sourceCode, nameNode);
            }
        } else {
            // Try block namespace: namespace X { }
            TSNode namespaceDecl = findFirstDescendant(rootNode, "namespace_declaration");
            if (namespaceDecl != null) {
                // First child is typically the qualified_name or identifier
                TSNode nameNode = namespaceDecl.getNamedChild(0);
                if (nameNode != null) {
                    analysis.packageName = getNodeText(sourceCode, nameNode);
                }
            }
        }
        
        // Collect using directives
        List<TSNode> usingDirectives = findAllDescendants(rootNode, "using_directive");
        for (TSNode usingDir : usingDirectives) {
            String text = getNodeText(sourceCode, usingDir).trim();
            analysis.imports.add(text);
        }
        
        // Find only top-level class declarations (not nested)
        // We need to identify classes that are NOT nested inside other classes
        List<TSNode> allClassDecls = findAllDescendants(rootNode, "class_declaration");
        
        // Use node IDs (start byte position) to track nested classes since TSNode may not have proper equals/hashCode
        Set<Integer> nestedClassIds = new HashSet<>();
        
        // Mark all classes that are nested (at any level) by recursively checking declaration_list nodes
        for (TSNode classDecl : allClassDecls) {
            TSNode classBody = findFirstChild(classDecl, "declaration_list");
            if (classBody != null) {
                // Use findAllDescendants to find ALL nested classes (including deeply nested)
                List<TSNode> nested = findAllDescendants(classBody, "class_declaration");
                for (TSNode n : nested) {
                    nestedClassIds.add(n.getStartByte());
                }
            }
        }
        
        // Process only top-level classes (those not marked as nested)
        for (TSNode classDecl : allClassDecls) {
            if (!nestedClassIds.contains(classDecl.getStartByte())) {
                analyzeClassRecursively(sourceCode, classDecl, analysis);
            }
        }
        
        // Find all interface declarations
        List<TSNode> interfaceDecls = findAllDescendants(rootNode, "interface_declaration");
        for (TSNode interfaceDecl : interfaceDecls) {
            TypeInfo typeInfo = analyzeInterface(sourceCode, interfaceDecl);
            analysis.types.add(typeInfo);
        }
        
        return analysis;
    }
    
    private void analyzeClassRecursively(String source, TSNode classDecl, FileAnalysis analysis) {
        TypeInfo typeInfo = analyzeClass(source, classDecl);
        analysis.types.add(typeInfo);
        
        // In C# Tree-sitter, the class body is called "declaration_list", not "class_body"
        TSNode classBody = findFirstChild(classDecl, "declaration_list");
        
        // Collect fields from this class only
        List<FieldInfo> fields = collectFieldsFromBody(source, classBody);
        typeInfo.fields.addAll(fields);
        
        // Analyze methods within this class only (not from nested classes)
        if (classBody != null) {
            List<TSNode> methods = findAllChildren(classBody, "method_declaration");
            for (TSNode method : methods) {
                MethodInfo methodInfo = analyzeMethod(source, method, typeInfo.name);
                typeInfo.methods.add(methodInfo);
            }
            
            // Recursively handle nested classes
            List<TSNode> nestedClasses = findAllChildren(classBody, "class_declaration");
            for (TSNode nested : nestedClasses) {
                analyzeClassRecursively(source, nested, analysis);
            }
        }
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
        
        // Extract modifiers and visibility
        extractModifiersAndVisibility(source, methodDecl, methodInfo.modifiers, methodInfo);
        
        // Extract attributes
        extractAttributes(source, methodDecl, methodInfo.annotations);
        
        // Get child count first
        int childCount = methodDecl.getNamedChildCount();
        
        // Get method name - find identifier that comes after modifiers/attributes/return type
        TSNode nameNode = null;
        for (int i = 0; i < childCount; i++) {
            TSNode child = methodDecl.getNamedChild(i);
            if ("identifier".equals(child.getType())) {
                // This should be the method name (comes after return type)
                nameNode = child;
                break;
            }
        }
        if (nameNode != null) {
            methodInfo.name = getNodeText(source, nameNode);
        }
        
        // Get return type - in C# Tree-sitter, it's a field named "returns"
        TSNode returnTypeNode = null;
        for (int i = 0; i < childCount; i++) {
            String fieldName = methodDecl.getFieldNameForChild(i);
            if ("returns".equals(fieldName)) {
                returnTypeNode = methodDecl.getNamedChild(i);
                break;
            }
        }
        
        if (returnTypeNode != null) {
            String typeText = getNodeText(source, returnTypeNode);
            if (typeText != null) {
                methodInfo.returnType = typeText;
            }
        }
        
        // Get parameters with types
        Map<String, String> paramTypes = new HashMap<>();
        TSNode paramsNode = findFirstChild(methodDecl, "parameter_list");
        if (paramsNode != null) {
            List<TSNode> params = findAllChildren(paramsNode, "parameter");
            for (TSNode param : params) {
                // In C#, Tree-sitter labels nodes with field names like "type:" and "name:"
                // We need to look for children by their field names
                String paramName = null;
                String paramType = null;
                
                int paramChildCount = param.getNamedChildCount();
                
                // Iterate through children to find type and name
                for (int i = 0; i < paramChildCount; i++) {
                    TSNode child = param.getNamedChild(i);
                    String fieldName = param.getFieldNameForChild(i);
                    
                    if ("name".equals(fieldName)) {
                        // This is the parameter name
                        paramName = getNodeText(source, child);
                    } else if ("type".equals(fieldName)) {
                        // This is the parameter type
                        paramType = getNodeText(source, child);
                    }
                }
                
                if (paramName != null) {
                    methodInfo.parameters.add(new Parameter(paramName, paramType));
                    if (paramType != null) {
                        paramTypes.put(paramName, paramType);
                    }
                }
            }
        }
        
        // Get method body - can be either a block (traditional) or arrow_expression_clause (expression-bodied)
        TSNode bodyNode = findFirstChild(methodDecl, "block");
        if (bodyNode != null) {
            analyzeMethodBody(source, bodyNode, methodInfo, className, paramTypes);
        } else {
            // Check for expression-bodied method (using =>)
            TSNode arrowBody = findFirstChild(methodDecl, "arrow_expression_clause");
            if (arrowBody != null) {
                analyzeMethodBody(source, arrowBody, methodInfo, className, paramTypes);
            }
        }
        
        return methodInfo;
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
                    // Aggregate method calls
                    boolean found = false;
                    for (MethodCall existingCall : methodInfo.methodCalls) {
                        if (existingCall.matches(methodName, objectType, objectName)) {
                            existingCall.callCount++;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        methodInfo.methodCalls.add(new MethodCall(methodName, objectType, objectName));
                    }
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
                // Add as a method call (properties are accessed like methods in the analysis)
                boolean found = false;
                for (MethodCall existingCall : methodInfo.methodCalls) {
                    if (existingCall.matches(propertyName, objectType, objectName)) {
                        existingCall.callCount++;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    methodInfo.methodCalls.add(new MethodCall(propertyName, objectType, objectName));
                }
            }
        }
        
        // Sort method calls alphabetically
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
                annotations.add(getNodeText(source, child));
            }
        }
    }
}
