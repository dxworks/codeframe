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

        // Process enums
        List<TSNode> enums = findAllDescendants(rootNode, "enum_declaration");
        for (TSNode enumDecl : enums) {
            analysis.types.add(analyzeEnum(sourceCode, enumDecl));
        }

        // Process records (C# 9+)
        List<TSNode> records = findAllDescendants(rootNode, "record_declaration");
        for (TSNode recordDecl : records) {
            analysis.types.add(analyzeRecord(sourceCode, recordDecl));
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
        // Collect properties separately
        typeInfo.properties.addAll(collectPropertiesFromBody(source, classBody));
        typeInfo.methods.addAll(collectMethodsFromBody(source, classBody, typeInfo.name));
        typeInfo.methods.addAll(collectConstructorsFromBody(source, classBody, typeInfo.name));
        
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

    private List<MethodInfo> collectConstructorsFromBody(String source, TSNode classBody, String className) {
        List<MethodInfo> ctors = new ArrayList<>();
        List<TSNode> ctorNodes = findAllChildren(classBody, "constructor_declaration");
        for (TSNode ctor : ctorNodes) {
            ctors.add(analyzeConstructor(source, ctor, className));
        }
        return ctors;
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
            String baseName = getNodeText(source, nameNode);
            TSNode typeParams = findFirstChild(classDecl, "type_parameter_list");
            if (typeParams != null) {
                typeInfo.name = baseName + getNodeText(source, typeParams);
            } else {
                typeInfo.name = baseName;
            }
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

    private TypeInfo analyzeEnum(String source, TSNode enumDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "enum";

        // Modifiers and attributes
        extractModifiersAndVisibility(source, enumDecl, typeInfo.modifiers, typeInfo);
        extractAttributes(source, enumDecl, typeInfo.annotations);

        // Name
        TSNode nameNode = findFirstChild(enumDecl, "identifier");
        if (nameNode != null) {
            typeInfo.name = getNodeText(source, nameNode);
        }

        // Enum members as fields with type equal to enum name
        TSNode body = findFirstChild(enumDecl, "enum_member_declaration_list");
        if (body == null) {
            // Some grammars put members directly under enum_declaration
            body = enumDecl;
        }
        List<TSNode> members = findAllDescendants(body, "enum_member_declaration");
        for (TSNode member : members) {
            TSNode id = findFirstChild(member, "identifier");
            if (id != null) {
                FieldInfo fi = new FieldInfo();
                fi.name = getNodeText(source, id);
                fi.type = typeInfo.name;
                typeInfo.fields.add(fi);
            }
        }

        return typeInfo;
    }
    private TypeInfo analyzeRecord(String source, TSNode recordDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "record";

        // Modifiers and attributes
        extractModifiersAndVisibility(source, recordDecl, typeInfo.modifiers, typeInfo);
        extractAttributes(source, recordDecl, typeInfo.annotations);

        // Name (include generic parameters if present)
        TSNode nameNode = findFirstChild(recordDecl, "identifier");
        if (nameNode != null) {
            String baseName = getNodeText(source, nameNode);
            TSNode typeParams = findFirstChild(recordDecl, "type_parameter_list");
            if (typeParams != null) {
                typeInfo.name = baseName + getNodeText(source, typeParams);
            } else {
                typeInfo.name = baseName;
            }
        }

        // Base list (extends/implements)
        TSNode baseListNode = findFirstChild(recordDecl, "base_list");
        if (baseListNode != null) {
            List<String> baseTypeNames = new ArrayList<>();
            for (int i = 0; i < baseListNode.getNamedChildCount(); i++) {
                TSNode baseTypeNode = baseListNode.getNamedChild(i);
                String typeName = getNodeText(source, baseTypeNode);
                if (typeName != null && !typeName.isEmpty()) {
                    baseTypeNames.add(typeName);
                }
            }
            for (int i = 0; i < baseTypeNames.size(); i++) {
                String typeName = baseTypeNames.get(i);
                if (i == 0 && !looksLikeInterface(typeName)) {
                    typeInfo.extendsType = typeName;
                } else {
                    typeInfo.implementsInterfaces.add(typeName);
                }
            }
        }

        // Primary constructor parameters -> fields (to mirror Java records components behavior)
        TSNode paramList = findFirstChild(recordDecl, "parameter_list");
        if (paramList != null) {
            List<TSNode> parameters = findAllChildren(paramList, "parameter");
            for (TSNode param : parameters) {
                // Reuse parameter extraction logic
                String pName = null;
                String pType = null;
                for (int i = 0; i < param.getNamedChildCount(); i++) {
                    TSNode child = param.getNamedChild(i);
                    String fieldName = param.getFieldNameForChild(i);
                    if ("name".equals(fieldName)) {
                        pName = getNodeText(source, child);
                    } else if ("type".equals(fieldName)) {
                        pType = extractTypeWithGenerics(source, child, param);
                    }
                }
                if (pName != null) {
                    FieldInfo fi = new FieldInfo();
                    fi.name = pName;
                    fi.type = pType;
                    // Record primary params are effectively properties; we record as fields here
                    typeInfo.fields.add(fi);
                }
            }
        }

        // Body members
        TSNode body = findFirstChild(recordDecl, "declaration_list");
        if (body != null) {
            typeInfo.fields.addAll(collectFieldsFromBody(source, body));
            typeInfo.properties.addAll(collectPropertiesFromBody(source, body));
            typeInfo.methods.addAll(collectMethodsFromBody(source, body, typeInfo.name));
            typeInfo.methods.addAll(collectConstructorsFromBody(source, body, typeInfo.name));
        }

        return typeInfo;
    }
    
    private TypeInfo analyzeInterface(String source, TSNode interfaceDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "interface";
        
        // Extract modifiers and visibility
        extractModifiersAndVisibility(source, interfaceDecl, typeInfo.modifiers, typeInfo);
        // Extract attributes
        extractAttributes(source, interfaceDecl, typeInfo.annotations);

        TSNode nameNode = findFirstChild(interfaceDecl, "identifier");
        if (nameNode != null) {
            String baseName = getNodeText(source, nameNode);
            TSNode typeParams = findFirstChild(interfaceDecl, "type_parameter_list");
            if (typeParams != null) {
                typeInfo.name = baseName + getNodeText(source, typeParams);
            } else {
                typeInfo.name = baseName;
            }
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
        
        // Collect interface methods from body
        TSNode body = findFirstChild(interfaceDecl, "declaration_list");
        if (body != null) {
            List<TSNode> methodNodes = findAllChildren(body, "method_declaration");
            for (TSNode method : methodNodes) {
                MethodInfo mi = analyzeMethod(source, method, typeInfo.name);
                // Do not default visibility for interface members; leave null unless specified
                typeInfo.methods.add(mi);
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

    private MethodInfo analyzeConstructor(String source, TSNode ctorDecl, String className) {
        MethodInfo methodInfo = new MethodInfo();
        extractModifiersAndVisibility(source, ctorDecl, methodInfo.modifiers, methodInfo);
        extractAttributes(source, ctorDecl, methodInfo.annotations);
        // Constructors are represented under methods as the class name, no return type
        methodInfo.name = className;
        methodInfo.returnType = null;
        Map<String, String> paramTypes = extractParameters(source, ctorDecl, methodInfo);
        TSNode bodyNode = findMethodBody(ctorDecl);
        if (bodyNode != null) {
            analyzeMethodBody(source, bodyNode, methodInfo, className, paramTypes);
        }
        return methodInfo;
    }
    
    private String extractMethodName(String source, TSNode methodDecl) {
        String baseName = null;
        // Prefer the child with field name 'name' (Tree-sitter assigns fields)
        for (int i = 0; i < methodDecl.getNamedChildCount(); i++) {
            if ("name".equals(methodDecl.getFieldNameForChild(i))) {
                TSNode nameNode = methodDecl.getNamedChild(i);
                baseName = getNodeText(source, nameNode);
                break;
            }
        }
        // Fallback: first identifier child
        if (baseName == null) {
            for (int i = 0; i < methodDecl.getNamedChildCount(); i++) {
                TSNode child = methodDecl.getNamedChild(i);
                if ("identifier".equals(child.getType())) {
                    baseName = getNodeText(source, child);
                    break;
                }
            }
        }
        if (baseName == null) return null;
        // Append generic type parameters if present on the declaration
        TSNode typeParams = findFirstChild(methodDecl, "type_parameter_list");
        if (typeParams != null) {
            return baseName + getNodeText(source, typeParams);
        }
        return baseName;
    }
    
    private String extractReturnType(String source, TSNode methodDecl) {
        for (int i = 0; i < methodDecl.getNamedChildCount(); i++) {
            if ("returns".equals(methodDecl.getFieldNameForChild(i))) {
                TSNode returnTypeNode = methodDecl.getNamedChild(i);
                return extractTypeWithGenerics(source, returnTypeNode, methodDecl);
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
                paramType = extractTypeWithGenerics(source, child, param);
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
                    declaredType = extractTypeWithGenerics(source, typeNode, variableDeclaration);
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
        
        // Track nodes that are part of invocations to avoid counting them as property accesses
        // Includes member_access_expression and qualified_name nodes
        Set<TSNode> nodesInInvocations = new HashSet<>();

        // Track invocation nodes by span to prevent duplicate counting of the same call
        Set<String> seenInvocationSpans = new HashSet<>();
        // Track call sites by the function node span and method name (more robust)
        Set<String> seenCallSites = new HashSet<>();
        
        // Find invocation expressions (method calls)
        // Note: We use findAllDescendants which recursively finds ALL invocation_expression nodes,
        // including nested ones in chained calls like obj.Method1().Method2()
        List<TSNode> invocations = findAllDescendants(bodyNode, "invocation_expression");
        for (TSNode invocation : invocations) {
            // Deduplicate the exact same invocation by its source span
            String invocationKey = invocation.getStartByte() + ":" + invocation.getEndByte();
            if (!seenInvocationSpans.add(invocationKey)) {
                continue;
            }
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
                    nodesInInvocations.add(functionNode);
                    // Also mark all nested member_access_expression nodes beneath the function node
                    // to avoid counting qualified name segments as separate property accesses
                    List<TSNode> nestedMemberAccesses = findAllDescendants(functionNode, "member_access_expression");
                    nodesInInvocations.addAll(nestedMemberAccesses);
                }
                // Note: Do NOT mark member accesses from invocation arguments; we only exclude
                // those under the 'function' node to avoid silencing legitimate property gets
                // passed as arguments (e.g., t.FilePath in From(new CodeIssue { SourceFile = t.FilePath }))
                
                String methodName = null;
                String objectName = null;
                String objectType = null;
                // Track the span of the callee identifier for robust dedupe
                TSNode calleeNameNodeForDedupe = null;
                
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
                    
                    if (nameNode != null && ("identifier".equals(nameNode.getType()) || "identifier_name".equals(nameNode.getType()))) {
                        methodName = getNodeText(source, nameNode);
                        calleeNameNodeForDedupe = nameNode;
                    } else if (nameNode != null && "generic_name".equals(nameNode.getType())) {
                        // For generic methods like Method<T>()
                        TSNode idNode = findFirstChild(nameNode, "identifier");
                        if (idNode != null) {
                            methodName = getNodeText(source, idNode);
                            calleeNameNodeForDedupe = idNode;
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
                            objectName = null; // do not treat type as an object instance
                            objectType = exprText;
                        } else if ("predefined_type".equals(exprType)) {
                            // Static method call on a predefined type like int.Parse, string.Join
                            objectName = null;
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
                    calleeNameNodeForDedupe = functionNode;
                } else if ("generic_name".equals(functionNode.getType())) {
                    // Generic method call like Method<T>()
                    // Extract just the method name (before the type arguments)
                    TSNode idNode = findFirstChild(functionNode, "identifier");
                    if (idNode != null) {
                        methodName = getNodeText(source, idNode);
                        calleeNameNodeForDedupe = idNode;
                    }
                }
                
                if (methodName != null) {
                    // Deduplicate by call site using the invocation span + method name
                    String callSiteKey = methodName + "@" + invocation.getStartByte() + ":" + invocation.getEndByte();
                    if (seenCallSites.add(callSiteKey)) {
                        // Additional dedupe by callee identifier span
                        if (calleeNameNodeForDedupe != null) {
                            String calleeKey = methodName + "@id(" + calleeNameNodeForDedupe.getStartByte() + ":" + calleeNameNodeForDedupe.getEndByte() + ")";
                            if (seenCallSites.add(calleeKey)) {
                                addOrIncrementMethodCall(methodInfo, methodName, objectType, objectName);
                            }
                        } else {
                            addOrIncrementMethodCall(methodInfo, methodName, objectType, objectName);
                        }
                    }
                }
            }
        }
        
        // Find property GET accesses (member_access_expression NOT part of invocations' function)
        List<TSNode> allMemberAccesses = findAllDescendants(bodyNode, "member_access_expression");
        for (TSNode memberAccess : allMemberAccesses) {
            // Skip if this is part of an invocation (method call)
            if (nodesInInvocations.contains(memberAccess)) {
                continue;
            }
            // If parent is an invocation and this node is the function child, skip (method call)
            TSNode parForFunc = memberAccess.getParent();
            if (parForFunc != null && !parForFunc.isNull() && "invocation_expression".equals(parForFunc.getType())) {
                int nc = parForFunc.getNamedChildCount();
                if (nc > 0 && parForFunc.getNamedChild(0) == memberAccess) {
                    continue; // skip counting GET for method callee
                }
            }
            // Also handle nested/wrapped callees: if any ancestor invocation's function subtree fully contains this member access, skip
            TSNode ancInv = memberAccess.getParent();
            boolean underInvocationFunction = false;
            while (ancInv != null && !ancInv.isNull()) {
                if ("invocation_expression".equals(ancInv.getType())) {
                    TSNode func = ancInv.getNamedChildCount() > 0 ? ancInv.getNamedChild(0) : null;
                    if (func != null) {
                        int fStart = func.getStartByte();
                        int fEnd = func.getEndByte();
                        int mStart = memberAccess.getStartByte();
                        int mEnd = memberAccess.getEndByte();
                        if (mStart >= fStart && mEnd <= fEnd) {
                            underInvocationFunction = true;
                            break;
                        }
                    }
                }
                ancInv = ancInv.getParent();
            }
            if (underInvocationFunction) continue;
            // Skip if this member access is under the LEFT subtree of any ancestor assignment_expression
            TSNode anc = memberAccess;
            boolean underAssignmentLHS = false;
            while (anc != null && !anc.isNull()) {
                TSNode par = anc.getParent();
                if (par == null || par.isNull()) break;
                if ("assignment_expression".equals(par.getType())) {
                    // Find the left child subtree of this assignment
                    TSNode leftNode = null;
                    for (int i = 0; i < par.getNamedChildCount(); i++) {
                        String fname = par.getFieldNameForChild(i);
                        if ("left".equals(fname) || fname == null) { // some grammars omit field names
                            leftNode = par.getNamedChild(i);
                            break;
                        }
                    }
                    if (leftNode != null) {
                        int lStart = leftNode.getStartByte();
                        int lEnd = leftNode.getEndByte();
                        int mStart = memberAccess.getStartByte();
                        int mEnd = memberAccess.getEndByte();
                        if (mStart >= lStart && mEnd <= lEnd) {
                            underAssignmentLHS = true;
                            break;
                        }
                    }
                }
                anc = par;
            }
            if (underAssignmentLHS) continue;
            
            // This is a property access like obj.Property or Type.StaticProperty
            String propertyName = null;
            String objectName = null;
            String objectType = null;
            
            int childCount = memberAccess.getNamedChildCount();
            if (childCount >= 2) {
                // First child is the expression (object/type)
                TSNode expressionNode = memberAccess.getNamedChild(0);
                // Last child is expected to be the identifier (property name)
                TSNode nameNode = memberAccess.getNamedChild(childCount - 1);
                if (nameNode != null) {
                    String nt = nameNode.getType();
                    if ("identifier".equals(nt) || "identifier_name".equals(nt)) {
                        propertyName = getNodeText(source, nameNode);
                    } else if ("generic_name".equals(nt)) {
                        TSNode idNode = findFirstChild(nameNode, "identifier");
                        if (idNode != null) propertyName = getNodeText(source, idNode);
                    } else {
                        // Fallback: try to find an identifier descendant, else use raw text
                        TSNode anyId = findFirstChild(nameNode, "identifier");
                        if (anyId == null) anyId = findFirstChild(nameNode, "identifier_name");
                        propertyName = anyId != null ? getNodeText(source, anyId) : getNodeText(source, nameNode);
                    }
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
                        objectName = null; // keep only the type
                        objectType = exprText;
                    } else if ("predefined_type".equals(exprType)) {
                        // Static property access on predefined type (rare, but for consistency)
                        objectName = null;
                        objectType = exprText;
                    }
                }
            }
            
            if (propertyName != null) {
                addOrIncrementMethodCall(methodInfo, "get_" + propertyName, objectType, objectName);
            }
        }

        // Find property SET accesses: assignment where LHS is a member_access_expression (exclude events)
        List<TSNode> assignments = findAllDescendants(bodyNode, "assignment_expression");
        for (TSNode assignment : assignments) {
            // Skip event add/remove patterns: += or -=
            String assignText = getNodeText(source, assignment);
            if (assignText.contains("+=") || assignText.contains("-=")) {
                continue;
            }
            // Expect first named child to be LHS expression
            TSNode lhs = null;
            for (int i = 0; i < assignment.getNamedChildCount(); i++) {
                TSNode child = assignment.getNamedChild(i);
                String fname = assignment.getFieldNameForChild(i);
                if (fname == null || "left".equals(fname)) { lhs = child; break; }
            }
            if (lhs == null) continue;
            if ("member_access_expression".equals(lhs.getType())) {
                // Extract property name and object details from LHS
                String propertyName = null;
                String objectName = null;
                String objectType = null;

                int childCount = lhs.getNamedChildCount();
                TSNode exprNode = null;
                TSNode nameNode = null;
                if (childCount >= 2) {
                    exprNode = lhs.getNamedChild(0);
                    nameNode = lhs.getNamedChild(childCount - 1);
                }
                if (nameNode != null) {
                    String nt = nameNode.getType();
                    if ("identifier".equals(nt) || "identifier_name".equals(nt)) {
                        propertyName = getNodeText(source, nameNode);
                    } else if ("generic_name".equals(nt)) {
                        TSNode idNode = findFirstChild(nameNode, "identifier");
                        if (idNode != null) propertyName = getNodeText(source, idNode);
                    } else {
                        TSNode anyId = findFirstChild(nameNode, "identifier");
                        if (anyId == null) anyId = findFirstChild(nameNode, "identifier_name");
                        propertyName = anyId != null ? getNodeText(source, anyId) : getNodeText(source, nameNode);
                    }
                }
                if (exprNode != null) {
                    String exprType = exprNode.getType();
                    String exprText = getNodeText(source, exprNode);
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
                        objectName = null;
                        objectType = exprText;
                    } else if ("predefined_type".equals(exprType)) {
                        objectName = null;
                        objectType = exprText;
                    }
                }
                if (propertyName != null) {
                    addOrIncrementMethodCall(methodInfo, "set_" + propertyName, objectType, objectName);
                }
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
                    declaredType = extractTypeWithGenerics(source, typeNode, variableDeclaration);
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

    private List<PropertyInfo> collectPropertiesFromBody(String source, TSNode classBody) {
        List<PropertyInfo> properties = new ArrayList<>();
        if (classBody == null) return properties;
        // Only direct property_declaration children of this class body
        List<TSNode> propDecls = findAllChildren(classBody, "property_declaration");
        for (TSNode prop : propDecls) {
            String propType = null;
            String propName = null;
            // Prefer field-named children when available
            for (int i = 0; i < prop.getNamedChildCount(); i++) {
                TSNode child = prop.getNamedChild(i);
                String fieldName = prop.getFieldNameForChild(i);
                if ("type".equals(fieldName)) {
                    propType = extractTypeWithGenerics(source, child, prop);
                } else if ("name".equals(fieldName)) {
                    propName = getNodeText(source, child);
                }
            }
            // Fallbacks
            if (propName == null) {
                TSNode id = findFirstChild(prop, "identifier");
                if (id != null) propName = getNodeText(source, id);
            }
            if (propType == null) {
                // try first named child as type
                if (prop.getNamedChildCount() > 0) {
                    TSNode first = prop.getNamedChild(0);
                    propType = extractTypeWithGenerics(source, first, prop);
                }
            }
            if (propName != null) {
                PropertyInfo pi = new PropertyInfo();
                pi.name = propName;
                pi.type = propType;
                extractModifiersAndVisibility(source, prop, pi.modifiers, pi);
                extractAttributes(source, prop, pi.annotations);
                // Analyze bodies of accessors and expression-bodied properties and merge locals/calls
                analyzePropertyBodies(source, prop, pi);
                properties.add(pi);
            }
        }
        return properties;
    }

    private void analyzePropertyBodies(String source, TSNode propDecl, PropertyInfo pi) {
        // Expression-bodied property directly on declaration -> synthesize a 'get' accessor
        TSNode arrow = findFirstChild(propDecl, "arrow_expression_clause");
        if (arrow != null) {
            AccessorInfo getter = new AccessorInfo();
            getter.kind = "get";
            // Accessor inherits property's modifiers unless overridden (no separate accessor node here)
            extractModifiersAndVisibility(source, propDecl, getter.modifiers, getter);
            extractAttributes(source, propDecl, getter.annotations);
            MethodInfo tmp = new MethodInfo();
            analyzeMethodBody(source, arrow, tmp, /*className*/ null, /*paramTypes*/ new HashMap<>());
            getter.localVariables.addAll(tmp.localVariables);
            getter.methodCalls.addAll(tmp.methodCalls);
            pi.accessors.add(getter);
            return;
        }
        // Accessor list: get/set with blocks or arrows
        TSNode accessors = findFirstChild(propDecl, "accessor_list");
        if (accessors != null) {
            List<TSNode> accessorDecls = findAllChildren(accessors, "accessor_declaration");
            for (TSNode acc : accessorDecls) {
                AccessorInfo ai = new AccessorInfo();
                // Determine kind: try identifier first, fall back to raw text prefix (handles auto-props)
                TSNode id = findFirstChild(acc, "identifier");
                if (id != null) {
                    ai.kind = getNodeText(source, id);
                } else {
                    String accText = getNodeText(source, acc).trim();
                    if (accText.startsWith("get")) ai.kind = "get";
                    else if (accText.startsWith("set")) ai.kind = "set";
                }
                extractModifiersAndVisibility(source, acc, ai.modifiers, ai);
                extractAttributes(source, acc, ai.annotations);
                // Prefer block, otherwise arrow
                TSNode body = findFirstChild(acc, "block");
                if (body == null) body = findFirstChild(acc, "arrow_expression_clause");
                if (body != null) {
                    MethodInfo tmp = new MethodInfo();
                    analyzeMethodBody(source, body, tmp, /*className*/ null, /*paramTypes*/ new HashMap<>());
                    ai.localVariables.addAll(tmp.localVariables);
                    ai.methodCalls.addAll(tmp.methodCalls);
                }
                pi.accessors.add(ai);
            }
        }
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
                // Set visibility only when explicitly specified
                if ("public".equals(modText) || "private".equals(modText) ||
                    "protected".equals(modText) || "internal".equals(modText)) {
                    if (target instanceof TypeInfo) {
                        ((TypeInfo) target).visibility = modText;
                    } else if (target instanceof MethodInfo) {
                        ((MethodInfo) target).visibility = modText;
                    } else if (target instanceof FieldInfo) {
                        ((FieldInfo) target).visibility = modText;
                    } else if (target instanceof PropertyInfo) {
                        ((PropertyInfo) target).visibility = modText;
                    } else if (target instanceof AccessorInfo) {
                        ((AccessorInfo) target).visibility = modText;
                    }
                }
            }
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

    // Generic-aware extraction for C# that stays local to the base type node:
    // - If the base node is a generic_name or contains its own type_argument_list, return its full text
    // - Else, if the immediate next named sibling is a type_argument_list, append it
    // - Do NOT scan the whole method signature to avoid pulling parameter generics into return type
    private String extractTypeWithGenerics(String source, TSNode baseTypeNode, TSNode searchScope) {
        if (baseTypeNode == null) return null;
        try {
            // Case 1: base node itself is a generic_name or has type arguments within
            if ("generic_name".equals(baseTypeNode.getType()) || findFirstDescendant(baseTypeNode, "type_argument_list") != null) {
                return getNodeText(source, baseTypeNode);
            }

            // Case 2: immediate next named sibling is the type_argument_list
            TSNode parent = baseTypeNode.getParent();
            if (parent != null) {
                // Find index of baseTypeNode among parent's named children
                int idx = -1;
                int n = parent.getNamedChildCount();
                for (int i = 0; i < n; i++) {
                    if (parent.getNamedChild(i) == baseTypeNode) { idx = i; break; }
                }
                if (idx != -1 && idx + 1 < n) {
                    TSNode next = parent.getNamedChild(idx + 1);
                    if (next != null && "type_argument_list".equals(next.getType())) {
                        int start = baseTypeNode.getStartByte();
                        int end = next.getEndByte();
                        if (start >= 0 && end > start && end <= source.length()) {
                            return source.substring(start, end);
                        }
                    }
                }
            }
        } catch (Exception ignored) { }
        return getNodeText(source, baseTypeNode);
    }
}
