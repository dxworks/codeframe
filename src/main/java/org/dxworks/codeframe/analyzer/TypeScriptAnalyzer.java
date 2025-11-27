package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.*;
import org.treesitter.TSNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.dxworks.codeframe.analyzer.TreeSitterHelper.*;

public class TypeScriptAnalyzer implements LanguageAnalyzer {
    
    // Literal types for renderObjectName
    private static final String[] TS_LITERAL_TYPES = {"array", "object", "string", "number"};
    
    @Override
    public FileAnalysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        FileAnalysis analysis = new FileAnalysis();
        analysis.filePath = filePath;
        analysis.language = "typescript";
        
        // Collect imports
        List<TSNode> importStmts = findAllDescendants(rootNode, "import_statement");
        for (TSNode imp : importStmts) {
            String text = getNodeText(sourceCode, imp).trim();
            analysis.imports.add(text);
        }
        
        // Find all class declarations and identify nested ones
        // Include both regular and abstract class declarations
        List<TSNode> allClasses = findAllDescendants(rootNode, "class_declaration");
        allClasses.addAll(findAllDescendants(rootNode, "abstract_class_declaration"));
        Set<Integer> nestedClassIds = identifyNestedNodes(allClasses, "class_body", 
                "class_declaration", "abstract_class_declaration");
        
        // Process only top-level classes recursively
        for (TSNode classDecl : allClasses) {
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
        
        // Find all enum declarations
        List<TSNode> enumDecls = findAllDescendants(rootNode, "enum_declaration");
        for (TSNode enumDecl : enumDecls) {
            TypeInfo enumInfo = analyzeEnum(sourceCode, enumDecl);
            analysis.types.add(enumInfo);
        }
        
        // Find all type alias declarations (e.g., type ID = string | number)
        List<TSNode> typeAliasDecls = findAllDescendants(rootNode, "type_alias_declaration");
        for (TSNode typeAliasDecl : typeAliasDecls) {
            TypeInfo typeAliasInfo = analyzeTypeAlias(sourceCode, typeAliasDecl);
            analysis.types.add(typeAliasInfo);
        }
        
        // Find standalone functions
        List<TSNode> functionDecls = findAllDescendants(rootNode, "function_declaration");
        for (TSNode funcDecl : functionDecls) {
            MethodInfo methodInfo = analyzeFunction(sourceCode, funcDecl);
            analysis.methods.add(methodInfo);
        }
        
        // Find arrow function assignments: const name = (...) => { ... }
        // Only process top-level lexical declarations (direct children of program or export_statement)
        List<TSNode> lexicalDecls = findAllDescendants(rootNode, "lexical_declaration");
        
        for (TSNode lexDecl : lexicalDecls) {
            // Check if this is a top-level declaration
            TSNode parent = lexDecl.getParent();
            if (parent == null) {
                continue; // Skip if no parent
            }
            
            String parentType = parent.getType();
            
            if (!"program".equals(parentType) && !"export_statement".equals(parentType)) {
                continue; // Skip nested declarations (not top-level)
            }
            
            // Get all variable_declarator children
            int childCount = lexDecl.getNamedChildCount();
            for (int i = 0; i < childCount; i++) {
                TSNode child = lexDecl.getNamedChild(i);
                
                if (child != null && "variable_declarator".equals(child.getType())) {
                    TSNode nameNode = findFirstChild(child, "identifier");
                    
                    // Find the arrow function - it might be after identifier and/or type_annotation
                    TSNode arrowFunc = null;
                    int namedChildCount = child.getNamedChildCount();
                    for (int j = 0; j < namedChildCount; j++) {
                        TSNode namedChild = child.getNamedChild(j);
                        if (namedChild != null && "arrow_function".equals(namedChild.getType())) {
                            arrowFunc = namedChild;
                            break;
                        }
                    }
                    
                    if (nameNode != null && arrowFunc != null) {
                        MethodInfo methodInfo = analyzeArrowFunction(sourceCode, child, nameNode, arrowFunc);
                        analysis.methods.add(methodInfo);
                    }
                }
            }
        }
        
        // Extract prototype and static method assignments (e.g., Person.prototype.greet = function() {})
        extractPrototypeMethods(sourceCode, rootNode, analysis);
        
        // Extract file-level fields (module-level constants/variables)
        extractFileLevelFields(sourceCode, rootNode, analysis);
        
        // Extract file-level method calls (top-level function calls)
        extractFileLevelMethodCalls(sourceCode, rootNode, analysis);
        
        return analysis;
    }
    
    /**
     * Extract prototype and static method assignments.
     * Patterns: Person.prototype.greet = function() {}
     *           Person.create = function() {}
     */
    private void extractPrototypeMethods(String source, TSNode rootNode, FileAnalysis analysis) {
        // Find expression_statement nodes at top level that contain assignment_expression
        int childCount = rootNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            if (!"expression_statement".equals(child.getType())) continue;
            
            TSNode expr = child.getNamedChild(0);
            if (expr == null || !"assignment_expression".equals(expr.getType())) continue;
            
            // Left side should be a member_expression (e.g., Person.prototype.greet or Person.create)
            TSNode left = getChildByFieldName(expr, "left");
            TSNode right = getChildByFieldName(expr, "right");
            if (left == null || right == null) continue;
            if (!"member_expression".equals(left.getType())) continue;
            
            // Right side should be a function expression
            String rightType = right.getType();
            boolean isFunc = "function".equals(rightType) || "function_expression".equals(rightType) 
                          || "arrow_function".equals(rightType) || "generator_function".equals(rightType);
            if (!isFunc) continue;
            
            // Extract the full member expression as the method name (e.g., "Person.prototype.greet")
            String fullName = getNodeText(source, left);
            if (fullName == null || fullName.isEmpty()) continue;
            
            MethodInfo mi = new MethodInfo();
            mi.name = fullName;
            mi.visibility = null;
            
            // Detect async
            String rightText = getNodeText(source, right);
            if (rightText != null && rightText.trim().startsWith("async")) {
                mi.modifiers.add("async");
            }
            
            // Detect generator
            if ("generator_function".equals(rightType) || (rightText != null && rightText.contains("function*"))) {
                mi.modifiers.add("function*");
            }
            
            // Parameters
            TSNode paramsNode = findFirstChild(right, "formal_parameters");
            if (paramsNode != null) {
                analyzeParameters(source, paramsNode, mi);
            }
            
            // Body
            TSNode bodyNode = findFirstChild(right, "statement_block");
            if (bodyNode != null) {
                analyzeMethodBody(source, bodyNode, mi, null);
            }
            
            analysis.methods.add(mi);
        }
    }
    
    /**
     * Extract file-level constants and variables (const, let, var declarations at module level).
     * Excludes function declarations (arrow functions, function expressions).
     */
    private void extractFileLevelFields(String source, TSNode rootNode, FileAnalysis analysis) {
        int childCount = rootNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            
            String nodeType = child.getType();
            TSNode declNode = null;
            boolean isExported = false;
            
            // Handle export statements wrapping declarations
            if ("export_statement".equals(nodeType)) {
                isExported = true;
                for (int j = 0; j < child.getNamedChildCount(); j++) {
                    TSNode exportChild = child.getNamedChild(j);
                    if (exportChild != null) {
                        String exportChildType = exportChild.getType();
                        if ("lexical_declaration".equals(exportChildType) || "variable_declaration".equals(exportChildType)) {
                            declNode = exportChild;
                            break;
                        }
                    }
                }
            } else if ("lexical_declaration".equals(nodeType) || "variable_declaration".equals(nodeType)) {
                declNode = child;
            }
            
            if (declNode == null) continue;
            
            // Process variable declarators within the declaration
            List<TSNode> declarators = findAllChildren(declNode, "variable_declarator");
            for (TSNode declarator : declarators) {
                TSNode nameNode = findFirstChild(declarator, "identifier");
                if (nameNode == null) continue;
                
                // Check if this is a function (arrow function, function expression)
                TSNode initializer = null;
                TSNode typeAnnotation = null;
                for (int j = 0; j < declarator.getNamedChildCount(); j++) {
                    TSNode declChild = declarator.getNamedChild(j);
                    if (declChild == null) continue;
                    String declChildType = declChild.getType();
                    if ("type_annotation".equals(declChildType)) {
                        typeAnnotation = declChild;
                    } else if ("arrow_function".equals(declChildType) || "function".equals(declChildType) ||
                               "function_expression".equals(declChildType) || "generator_function".equals(declChildType)) {
                        initializer = declChild;
                        break;
                    } else if (!"identifier".equals(declChildType) && !"type_annotation".equals(declChildType)) {
                        initializer = declChild;
                    }
                }
                
                // Skip functions - they're already captured as methods
                if (initializer != null) {
                    String initType = initializer.getType();
                    if ("arrow_function".equals(initType) || "function".equals(initType) ||
                        "function_expression".equals(initType) || "generator_function".equals(initType)) {
                        continue;
                    }
                }
                
                FieldInfo field = new FieldInfo();
                field.name = getNodeText(source, nameNode);
                
                // Determine declaration kind (const, let, var)
                String declText = getNodeText(source, declNode);
                if (declText != null) {
                    if (declText.trim().startsWith("const ")) {
                        field.modifiers.add("const");
                    } else if (declText.trim().startsWith("let ")) {
                        field.modifiers.add("let");
                    } else if (declText.trim().startsWith("var ")) {
                        field.modifiers.add("var");
                    }
                }
                
                // Check for export
                if (isExported) {
                    field.modifiers.add("export");
                }
                
                // Get type from annotation or infer from initializer
                if (typeAnnotation != null) {
                    field.type = getNodeText(source, typeAnnotation).replaceFirst("^:\\s*", "");
                } else if (initializer != null) {
                    field.type = inferTypeFromExpression(source, initializer);
                }
                
                if (field.name != null && !field.name.isEmpty()) {
                    analysis.fields.add(field);
                }
            }
        }
    }
    
    /**
     * Extract file-level method calls (all function calls at module level, outside any named function or class).
     * This includes calls inside callbacks passed to top-level calls (e.g., test(), expect() inside describe()).
     */
    private void extractFileLevelMethodCalls(String source, TSNode rootNode, FileAnalysis analysis) {
        int childCount = rootNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            
            String nodeType = child.getType();
            
            // Look for expression_statement - extract ALL calls within it (including nested callbacks)
            if ("expression_statement".equals(nodeType)) {
                List<TSNode> allCalls = findAllDescendants(child, "call_expression");
                for (TSNode callExpr : allCalls) {
                    extractCallIntoList(source, callExpr, analysis.methodCalls);
                }
            }
        }
        analysis.methodCalls.sort(METHOD_CALL_COMPARATOR);
    }
    
    /**
     * Extract a single call expression into a method call list.
     */
    private void extractCallIntoList(String source, TSNode callNode, List<MethodCall> calls) {
        TSNode functionNode = callNode.getNamedChild(0);
        if (functionNode == null || functionNode.isNull()) return;
        
        String methodName = null;
        String objectName = null;
        
        if ("member_expression".equals(functionNode.getType())) {
            TSNode propNode = findFirstChild(functionNode, "property_identifier");
            if (propNode != null) {
                methodName = getNodeText(source, propNode);
            }
            TSNode objNode = functionNode.getNamedChild(0);
            if (objNode != null && !objNode.isNull()) {
                objectName = renderObjectName(source, objNode, TS_LITERAL_TYPES);
            }
        } else if ("identifier".equals(functionNode.getType())) {
            methodName = getNodeText(source, functionNode);
        }
        
        if (methodName != null && isValidIdentifier(methodName)) {
            collectMethodCall(calls, methodName, null, objectName);
        }
    }
    
    
    private void analyzeClassRecursively(String source, TSNode classDecl, FileAnalysis analysis) {
        analyzeClassRecursivelyInto(source, classDecl, analysis.types);
    }
    
    private void analyzeClassRecursivelyInto(String source, TSNode classDecl, List<TypeInfo> targetList) {
        TypeInfo typeInfo = analyzeClass(source, classDecl);
        targetList.add(typeInfo);
        
        TSNode classBody = findFirstChild(classDecl, "class_body");
        if (classBody == null) {
            return;
        }
        
        // Collect fields for this class only
        List<FieldInfo> fields = collectFieldsFromBody(source, classBody);
        typeInfo.fields.addAll(fields);
        
        // Collect constructor parameter properties (e.g., constructor(public x: number))
        List<FieldInfo> ctorParamFields = collectConstructorParameterProperties(source, classBody);
        typeInfo.fields.addAll(ctorParamFields);
        
        // Analyze methods within this class only
        List<TSNode> methods = findAllChildren(classBody, "method_definition");
        for (TSNode method : methods) {
            MethodInfo methodInfo = analyzeMethod(source, method, typeInfo.name);
            typeInfo.methods.add(methodInfo);
        }
        
        // Recursively process nested classes
        List<TSNode> nestedClasses = findAllChildren(classBody, "class_declaration");
        nestedClasses.addAll(findAllChildren(classBody, "abstract_class_declaration"));
        for (TSNode nested : nestedClasses) {
            analyzeClassRecursivelyInto(source, nested, typeInfo.types);
        }
    }
    
    private TypeInfo analyzeClass(String source, TSNode classDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "class";
        
        // Extract modifiers and visibility (export, abstract, etc.)
        extractModifiersAndVisibility(source, classDecl, typeInfo.modifiers, typeInfo);
        
        // Extract decorators (TypeScript's version of annotations)
        extractDecorators(source, classDecl, typeInfo.annotations);
        
        // Get class name
        typeInfo.name = extractName(source, classDecl, "type_identifier");
        
        // Get heritage clause (extends/implements)
        TSNode heritageNode = findFirstChild(classDecl, "class_heritage");
        if (heritageNode != null) {
            // Check for extends
            TSNode extendsClause = findFirstChild(heritageNode, "extends_clause");
            if (extendsClause != null) {
                TSNode typeId = findFirstDescendant(extendsClause, "identifier");
                if (typeId != null) {
                    typeInfo.extendsType = getNodeText(source, typeId);
                }
            }
            
            // Check for implements
            TSNode implementsClause = findFirstChild(heritageNode, "implements_clause");
            if (implementsClause != null) {
                List<TSNode> typeIds = findAllDescendants(implementsClause, "type_identifier");
                for (TSNode typeId : typeIds) {
                    typeInfo.implementsInterfaces.add(getNodeText(source, typeId));
                }
            }
        }
        
        return typeInfo;
    }
    
    private TypeInfo analyzeInterface(String source, TSNode interfaceDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "interface";
        // Extract modifiers/visibility and decorators
        extractModifiersAndVisibility(source, interfaceDecl, typeInfo.modifiers, typeInfo);
        extractDecorators(source, interfaceDecl, typeInfo.annotations);
        typeInfo.name = extractName(source, interfaceDecl, "type_identifier");
        
        // Interfaces can extend other interfaces
        // Try multiple node types as different grammar versions may use different names
        TSNode extendsNode = findFirstChild(interfaceDecl, "extends_clause");
        if (extendsNode == null) {
            extendsNode = findFirstChild(interfaceDecl, "extends_type_clause");
        }
        if (extendsNode == null) {
            // Some grammars nest extends under a heritage wrapper
            extendsNode = findFirstDescendant(interfaceDecl, "extends_clause");
        }
        if (extendsNode == null) {
            extendsNode = findFirstDescendant(interfaceDecl, "extends_type_clause");
        }
        if (extendsNode != null) {
            List<TSNode> typeIds = findAllDescendants(extendsNode, "type_identifier");
            // Fallback to identifier if type_identifier not found
            if (typeIds.isEmpty()) {
                typeIds = findAllDescendants(extendsNode, "identifier");
            }
            for (TSNode typeId : typeIds) {
                typeInfo.implementsInterfaces.add(getNodeText(source, typeId));
            }
        }
        // Collect interface method signatures
        TSNode objectType = findFirstChild(interfaceDecl, "object_type");
        List<TSNode> methodSigs = new ArrayList<>();
        if (objectType != null) {
            methodSigs.addAll(findAllDescendants(objectType, "method_signature"));
        }
        // Fallback: some grammars may not expose an object_type wrapper
        if (methodSigs.isEmpty()) {
            methodSigs.addAll(findAllDescendants(interfaceDecl, "method_signature"));
        }
        if (!methodSigs.isEmpty()) {
            for (TSNode ms : methodSigs) {
                MethodInfo mi = new MethodInfo();
                // Name
                TSNode pid = findFirstChild(ms, "property_identifier");
                if (pid == null) {
                    pid = findFirstChild(ms, "identifier");
                }
                if (pid != null) mi.name = getNodeText(source, pid);
                // Parameters and return type live under call_signature
                TSNode callSig = findFirstChild(ms, "call_signature");
                if (callSig != null) {
                    // Parameters
                    TSNode paramsNode = findFirstChild(callSig, "formal_parameters");
                    if (paramsNode != null) {
                        analyzeParameters(source, paramsNode, mi);
                    }
                    // Return type
                    TSNode typeAnnotation = findFirstChild(callSig, "type_annotation");
                    if (typeAnnotation != null) {
                        mi.returnType = getNodeText(source, typeAnnotation).replaceFirst("^:\\s*", "");
                    }
                } else {
                    // Some grammars expose parameters/type_annotation directly on method_signature
                    TSNode paramsNode = findFirstChild(ms, "formal_parameters");
                    if (paramsNode != null) {
                        analyzeParameters(source, paramsNode, mi);
                    }
                    TSNode typeAnnotation = findFirstChild(ms, "type_annotation");
                    if (typeAnnotation != null) {
                        mi.returnType = getNodeText(source, typeAnnotation).replaceFirst("^:\\s*", "");
                    }
                }
                if (mi.name != null) {
                    typeInfo.methods.add(mi);
                }
            }
        }
        
        // Collect interface properties (field-like signatures)
        List<TSNode> propSigs = new ArrayList<>();
        if (objectType != null) {
            propSigs.addAll(findAllDescendants(objectType, "property_signature"));
        }
        if (propSigs.isEmpty()) {
            propSigs.addAll(findAllDescendants(interfaceDecl, "property_signature"));
        }
        for (TSNode ps : propSigs) {
            PropertyInfo pi = new PropertyInfo();
            // Modifiers like readonly
            extractModifiersAndVisibility(source, ps, pi.modifiers, pi);
            // Name
            TSNode propNameNode = findFirstChild(ps, "property_identifier");
            if (propNameNode == null) propNameNode = findFirstChild(ps, "identifier");
            if (propNameNode != null) {
                pi.name = getNodeText(source, propNameNode);
            }
            // Type annotation
            TSNode typeAnn = findFirstChild(ps, "type_annotation");
            if (typeAnn != null) {
                pi.type = getNodeText(source, typeAnn).replaceFirst("^:\\s*", "");
            }
            if (pi.name != null) {
                typeInfo.properties.add(pi);
            }
        }
        
        return typeInfo;
    }
    
    /**
     * Analyzes a TypeScript enum declaration.
     * Supports regular enums, string enums, and const enums.
     */
    private TypeInfo analyzeEnum(String source, TSNode enumDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "enum";
        
        // Extract modifiers (export, const, etc.)
        extractModifiersAndVisibility(source, enumDecl, typeInfo.modifiers, typeInfo);
        
        // Check for 'const' enum by looking at parent or siblings
        TSNode parent = enumDecl.getParent();
        if (parent != null && "export_statement".equals(parent.getType())) {
            // Check if there's a 'const' keyword before the enum
            String parentText = getNodeText(source, parent);
            if (parentText != null && parentText.contains("const enum")) {
                if (!typeInfo.modifiers.contains("const")) {
                    typeInfo.modifiers.add("const");
                }
            }
        }
        
        // Get enum name
        typeInfo.name = extractName(source, enumDecl, "identifier");
        
        // Get enum body and extract members as fields
        TSNode enumBody = findFirstChild(enumDecl, "enum_body");
        if (enumBody != null) {
            // Find all enum assignments (members)
            List<TSNode> members = findAllChildren(enumBody, "enum_assignment");
            // Also try property_identifier for simple members without assignment
            if (members.isEmpty()) {
                members = findAllChildren(enumBody, "property_identifier");
            }
            
            for (TSNode member : members) {
                FieldInfo fieldInfo = new FieldInfo();
                
                // Get member name
                TSNode memberNameNode = findFirstChild(member, "property_identifier");
                if (memberNameNode == null) {
                    // Member itself might be the identifier
                    if ("property_identifier".equals(member.getType())) {
                        memberNameNode = member;
                    }
                }
                
                if (memberNameNode != null) {
                    fieldInfo.name = getNodeText(source, memberNameNode);
                }
                
                // Get member value/type if present
                // For string enums: Status = 'ACTIVE'
                // For numeric enums: Priority = 0 or just Priority (auto-incremented)
                TSNode valueNode = null;
                int childCount = member.getNamedChildCount();
                for (int i = 0; i < childCount; i++) {
                    TSNode child = member.getNamedChild(i);
                    if (child != null && !"property_identifier".equals(child.getType())) {
                        valueNode = child;
                        break;
                    }
                }
                
                if (valueNode != null) {
                    String valueType = valueNode.getType();
                    if ("string".equals(valueType) || valueType.contains("string")) {
                        fieldInfo.type = "string";
                    } else if ("number".equals(valueType) || valueType.contains("number")) {
                        fieldInfo.type = "number";
                    } else {
                        // Try to infer from the text
                        String valueText = getNodeText(source, valueNode);
                        if (valueText != null) {
                            if (valueText.startsWith("'") || valueText.startsWith("\"")) {
                                fieldInfo.type = "string";
                            } else {
                                fieldInfo.type = "number";
                            }
                        }
                    }
                } else {
                    // Default numeric enum
                    fieldInfo.type = "number";
                }
                
                // Set the enum name as a type qualifier
                if (typeInfo.name != null) {
                    fieldInfo.type = typeInfo.name;
                }
                
                if (fieldInfo.name != null) {
                    typeInfo.fields.add(fieldInfo);
                }
            }
        }
        
        return typeInfo;
    }
    
    /**
     * Analyzes a TypeScript type alias declaration.
     * Examples: type ID = string | number;
     *           type Coordinates = { x: number; y: number };
     *           type Result<T> = { success: true; data: T } | { success: false; error: string };
     */
    private TypeInfo analyzeTypeAlias(String source, TSNode typeAliasDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "type";
        
        // Extract modifiers (export, etc.)
        extractModifiersAndVisibility(source, typeAliasDecl, typeInfo.modifiers, typeInfo);
        
        // Get type alias name (may include generic type parameters)
        typeInfo.name = extractName(source, typeAliasDecl, "type_identifier");
        
        // Check for type parameters (generics like <T>)
        TSNode typeParams = findFirstChild(typeAliasDecl, "type_parameters");
        if (typeParams != null && typeInfo.name != null) {
            typeInfo.name = typeInfo.name + getNodeText(source, typeParams);
        }
        
        // Get the aliased type (the right side of the = sign)
        // This could be a union_type, intersection_type, object_type, etc.
        // We'll store it in extendsType as a representation of what this type aliases to
        int childCount = typeAliasDecl.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = typeAliasDecl.getNamedChild(i);
            if (child == null) continue;
            String childType = child.getType();
            // Skip the name and type parameters, get the actual type definition
            if (!"type_identifier".equals(childType) && !"type_parameters".equals(childType)) {
                typeInfo.extendsType = getNodeText(source, child);
                break;
            }
        }
        
        return typeInfo;
    }
    
    private MethodInfo analyzeMethod(String source, TSNode methodDef, String className) {
        MethodInfo methodInfo = new MethodInfo();
        
        // Extract modifiers and visibility
        extractModifiersAndVisibility(source, methodDef, methodInfo.modifiers, methodInfo);
        
        // Extract decorators
        extractDecorators(source, methodDef, methodInfo.annotations);
        
        // Get method name
        methodInfo.name = extractName(source, methodDef, "property_identifier");
        
        // Get return type annotation
        TSNode typeAnnotation = findFirstChild(methodDef, "type_annotation");
        if (typeAnnotation != null) {
            methodInfo.returnType = getNodeText(source, typeAnnotation).replaceFirst("^:\\s*", "");
        }
        
        // Get parameters with types
        TSNode paramsNode = findFirstChild(methodDef, "formal_parameters");
        if (paramsNode != null) {
            analyzeParameters(source, paramsNode, methodInfo);
        }
        
        // Get method body
        TSNode bodyNode = findFirstChild(methodDef, "statement_block");
        if (bodyNode != null) {
            analyzeMethodBody(source, bodyNode, methodInfo, className);
        }
        
        return methodInfo;
    }
    
    private MethodInfo analyzeFunction(String source, TSNode funcDecl) {
        MethodInfo methodInfo = new MethodInfo();
        
        // Extract modifiers (export, async, etc.)
        extractModifiersAndVisibility(source, funcDecl, methodInfo.modifiers, methodInfo);
        
        // Get function name
        methodInfo.name = extractName(source, funcDecl, "identifier");
        
        // Get return type annotation
        TSNode typeAnnotation = findFirstChild(funcDecl, "type_annotation");
        if (typeAnnotation != null) {
            methodInfo.returnType = getNodeText(source, typeAnnotation).replaceFirst("^:\\s*", "");
        }
        
        // Get parameters
        TSNode paramsNode = findFirstChild(funcDecl, "formal_parameters");
        if (paramsNode != null) {
            analyzeParameters(source, paramsNode, methodInfo);
        }
        
        // Get function body
        TSNode bodyNode = findFirstChild(funcDecl, "statement_block");
        if (bodyNode != null) {
            analyzeMethodBody(source, bodyNode, methodInfo, null);
        }
        
        return methodInfo;
    }
    
    private MethodInfo analyzeArrowFunction(String source, TSNode declarator, TSNode nameNode, TSNode arrowFunc) {
        MethodInfo methodInfo = new MethodInfo();
        
        // Get function name from the variable declarator
        methodInfo.name = getNodeText(source, nameNode);
        
        // Check for type annotation on the variable (e.g., const foo: React.FC<Props> = ...)
        TSNode typeAnnotation = findFirstChild(declarator, "type_annotation");
        if (typeAnnotation != null) {
            methodInfo.returnType = getNodeText(source, typeAnnotation).replaceFirst("^:\\s*", "");
        } else {
            // Try to get return type from arrow function itself
            TSNode returnType = findFirstChild(arrowFunc, "type_annotation");
            if (returnType != null) {
                methodInfo.returnType = getNodeText(source, returnType).replaceFirst("^:\\s*", "");
            }
        }
        
        // Get parameters - can be formal_parameters or a direct pattern (e.g., object_pattern for destructuring)
        TSNode paramsNode = findFirstChild(arrowFunc, "formal_parameters");
        if (paramsNode != null) {
            // Check if formal_parameters contains a destructuring pattern
            int paramCount = paramsNode.getNamedChildCount();
            if (paramCount == 1) {
                TSNode firstParam = paramsNode.getNamedChild(0);
                if (firstParam != null) {
                    String paramType = firstParam.getType();
                    if ("object_pattern".equals(paramType) || "array_pattern".equals(paramType)) {
                        // Destructuring with parentheses: ({ a, b }) => ...
                        analyzeParameter(source, firstParam, methodInfo);
                    } else {
                        // Regular single parameter
                        analyzeParameters(source, paramsNode, methodInfo);
                    }
                }
            } else if (paramCount > 1) {
                // Multiple parameters - use regular parameter analysis
                analyzeParameters(source, paramsNode, methodInfo);
            }
            // If paramCount == 0, no parameters to process
        } else {
            // Check if first child is a pattern (object_pattern, array_pattern, identifier)
            TSNode firstChild = arrowFunc.getNamedChild(0);
            if (firstChild != null) {
                String firstChildType = firstChild.getType();
                if ("object_pattern".equals(firstChildType) || "array_pattern".equals(firstChildType) || "identifier".equals(firstChildType)) {
                    // Single parameter without parentheses (e.g., x => x * 2)
                    analyzeParameter(source, firstChild, methodInfo);
                }
            }
        }
        
        // Get function body (can be statement_block or expression)
        TSNode bodyNode = findFirstChild(arrowFunc, "statement_block");
        if (bodyNode != null) {
            analyzeMethodBody(source, bodyNode, methodInfo, null);
        }
        
        return methodInfo;
    }
    
    private void analyzeParameter(String source, TSNode paramNode, MethodInfo methodInfo) {
        if (paramNode == null) return;
        
        String paramType = paramNode.getType();
        
        if ("identifier".equals(paramType)) {
            // Simple parameter: x => ...
            String name = getNodeText(source, paramNode);
            methodInfo.parameters.add(new Parameter(name, null));
        } else if ("object_pattern".equals(paramType)) {
            // Destructured object parameter: ({ a, b }) => ...
            // Look for all identifiers within the object pattern
            List<TSNode> identifiers = findAllDescendants(paramNode, "identifier");
            for (TSNode id : identifiers) {
                String name = getNodeText(source, id);
                methodInfo.parameters.add(new Parameter(name, null));
            }
        } else if ("array_pattern".equals(paramType)) {
            // Destructured array parameter: ([a, b]) => ...
            List<TSNode> identifiers = findAllDescendants(paramNode, "identifier");
            for (TSNode id : identifiers) {
                String name = getNodeText(source, id);
                methodInfo.parameters.add(new Parameter(name, null));
            }
        }
    }
    
    private void analyzeParameters(String source, TSNode paramsNode, MethodInfo methodInfo) {
        // Iterate children by index to preserve source order
        int count = paramsNode.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            TSNode param = paramsNode.getNamedChild(i);
            if (param == null) continue;
            String type = param.getType();

            // Handle required and optional parameters uniformly
            if ("required_parameter".equals(type) || "optional_parameter".equals(type)) {
                analyzeRegularParameter(source, param, methodInfo);
            } else if ("rest_parameter".equals(type) || "rest_pattern".equals(type)) {
                analyzeRestParameter(source, param, methodInfo);
            }
        }
        
        // Fallback: Some TypeScript grammars may not expose rest as named children.
        // If we didn't capture any rest parameter above, scan descendants once.
        boolean hasRestAlready = methodInfo.parameters.stream().anyMatch(p -> p.name != null && p.name.startsWith("..."));
        if (!hasRestAlready) {
            List<TSNode> restParams = findAllDescendants(paramsNode, "rest_parameter");
            if (restParams.isEmpty()) {
                restParams = findAllDescendants(paramsNode, "rest_pattern");
            }
            if (!restParams.isEmpty()) {
                analyzeRestParameter(source, restParams.get(restParams.size() - 1), methodInfo);
            }
        }
    }
    
    private void analyzeRegularParameter(String source, TSNode param, MethodInfo methodInfo) {
        TSNode paramName = findFirstChild(param, "identifier");
        String name = paramName != null ? getNodeText(source, paramName) : null;

        // Get type annotation if present
        TSNode typeAnnotation = findFirstChild(param, "type_annotation");
        String annType = null;
        if (typeAnnotation != null) {
            annType = getNodeText(source, typeAnnotation).replaceFirst("^:\\s*", "");
        }

        if (name != null) {
            methodInfo.parameters.add(new Parameter(name, annType));
        }
    }
    
    private void analyzeRestParameter(String source, TSNode param, MethodInfo methodInfo) {
        // ...rest: capture name and type; identifier may be nested
        TSNode nameNode = findFirstChild(param, "identifier");
        if (nameNode == null) {
            nameNode = findFirstDescendant(param, "identifier");
        }
        String name = nameNode != null ? ("..." + getNodeText(source, nameNode)) : null;
        String annType = extractRestParameterType(source, param);
        
        if (name != null) {
            methodInfo.parameters.add(new Parameter(name, annType));
        }
    }
    
    private String extractRestParameterType(String source, TSNode param) {
        TSNode typeAnnotation = findFirstChild(param, "type_annotation");
        if (typeAnnotation == null) {
            // Type annotation may not be a direct child; search descendants
            typeAnnotation = findFirstDescendant(param, "type_annotation");
        }
        
        if (typeAnnotation != null) {
            return getNodeText(source, typeAnnotation).replaceFirst("^:\\s*", "");
        }
        
        // Fallbacks: look for common type node kinds
        TSNode typeNode = findFirstDescendant(param, "array_type");
        if (typeNode == null) typeNode = findFirstDescendant(param, "union_type");
        if (typeNode == null) typeNode = findFirstDescendant(param, "type_identifier");
        if (typeNode == null) typeNode = findFirstDescendant(param, "predefined_type");
        
        if (typeNode != null) {
            return getNodeText(source, typeNode).replaceFirst("^:\\s*", "");
        }
        
        // Last resort: parse from raw text after ':'
        String raw = getNodeText(source, param);
        if (raw != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(":\\s*(.+)$").matcher(raw.trim());
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        
        return null;
    }
    
    private void analyzeMethodBody(String source, TSNode bodyNode, MethodInfo methodInfo, String className) {
        // Build a map of variable names to their types
        Map<String, String> localTypes = new HashMap<>();
        
        // Find variable declarations (const, let, var) and extract types
        List<TSNode> varDecls = findAllDescendants(bodyNode, "variable_declarator");
        for (TSNode varDecl : varDecls) {
            TSNode varName = findFirstChild(varDecl, "identifier");
            if (varName != null) {
                String name = getNodeText(source, varName);
                methodInfo.localVariables.add(name);
                
                // Try to get type from type annotation
                TSNode typeAnnotation = findFirstChild(varDecl, "type_annotation");
                if (typeAnnotation != null) {
                    String type = getNodeText(source, typeAnnotation).replaceFirst("^:\\s*", "");
                    localTypes.put(name, type);
                } else {
                    // Try to infer type from initializer
                    TSNode initializer = varDecl.getNamedChild(1); // Usually the value after =
                    if (initializer != null) {
                        String inferredType = inferTypeFromExpression(source, initializer);
                        if (inferredType != null) {
                            localTypes.put(name, inferredType);
                        }
                    }
                }
            }
        }
        
        // Find call expressions
        List<TSNode> callExprs = findAllDescendants(bodyNode, "call_expression");
        for (TSNode callExpr : callExprs) {
            // Get the function being called
            TSNode functionNode = callExpr.getNamedChild(0);
            if (functionNode != null) {
                String callText = null;
                String objectName = null;
                String objectType = null;
                
                if ("member_expression".equals(functionNode.getType())) {
                    // obj.method() or obj.prop.method()
                    TSNode propNode = findFirstChild(functionNode, "property_identifier");
                    
                    if (propNode != null) {
                        callText = getNodeText(source, propNode);
                    }
                    
                    TSNode objectExpr = functionNode.getNamedChild(0);
                    if (objectExpr != null) {
                        objectName = renderObjectName(source, objectExpr, TS_LITERAL_TYPES);
                        
                        // Look up type from local variables (use base identifier for lookup)
                        if ("identifier".equals(objectExpr.getType())) {
                            objectType = localTypes.get(objectName);
                        } else {
                            // For complex expressions, try to get the leftmost identifier for type lookup
                            TSNode leftmost = getLeftmostIdentifier(objectExpr);
                            if (leftmost != null) {
                                objectType = localTypes.get(getNodeText(source, leftmost));
                            }
                        }
                        
                        // Check if this is 'this'
                        if ("this".equals(objectName) && className != null) {
                            objectType = className;
                        }
                    }
                } else if ("identifier".equals(functionNode.getType())) {
                    // Direct function call
                    callText = getNodeText(source, functionNode);
                }
                
                if (callText != null) {
                    collectMethodCall(methodInfo, callText, objectType, objectName);
                }
            }
        }
        
        // Sort method calls alphabetically
        methodInfo.methodCalls.sort(METHOD_CALL_COMPARATOR);
    }
    
    private List<FieldInfo> collectFieldsFromBody(String source, TSNode classBody) {
        List<FieldInfo> fields = new ArrayList<>();
        if (classBody == null) return fields;
        
        // Only direct field_definition children of this class body
        List<TSNode> fieldDecls = new ArrayList<>();
        fieldDecls.addAll(findAllChildren(classBody, "field_definition"));
        // Support TS/JS grammar variants
        fieldDecls.addAll(findAllChildren(classBody, "public_field_definition"));
        fieldDecls.addAll(findAllChildren(classBody, "private_field_definition"));
        
        for (TSNode field : fieldDecls) {
            FieldInfo fieldInfo = new FieldInfo();
            
            extractModifiersAndVisibility(source, field, fieldInfo.modifiers, fieldInfo);
            
            // Extract decorators
            extractDecorators(source, field, fieldInfo.annotations);
            
            // Get field name
            TSNode nameNode = findFirstChild(field, "property_identifier");
            if (nameNode == null) {
                nameNode = findFirstChild(field, "private_property_identifier");
            }
            if (nameNode != null) {
                fieldInfo.name = getNodeText(source, nameNode);
            }
            
            // Get type annotation
            TSNode typeAnnotation = findFirstChild(field, "type_annotation");
            if (typeAnnotation != null) {
                fieldInfo.type = getNodeText(source, typeAnnotation).replaceFirst("^:\\s*", "");
            } else {
                // Try to infer type from initializer expression, if present
                int childCount = field.getNamedChildCount();
                if (childCount > 0) {
                    TSNode last = field.getNamedChild(childCount - 1);
                    if (last != null) {
                        String lt = last.getType();
                        // Avoid using the name or the annotation nodes as initializer
                        if (!"property_identifier".equals(lt) && !"private_property_identifier".equals(lt)
                                && !"type_annotation".equals(lt)) {
                            String inferred = inferTypeFromExpression(source, last);
                            if (inferred != null) {
                                fieldInfo.type = inferred;
                            }
                        }
                    }
                }
            }
            
            fields.add(fieldInfo);
        }
        
        return fields;
    }
    
    /**
     * Extracts fields from constructor parameter properties.
     * In TypeScript, constructor parameters with visibility modifiers (public, private, protected)
     * or readonly are automatically converted to class fields.
     * Example: constructor(public x: number, private y: string, readonly z: boolean)
     */
    private List<FieldInfo> collectConstructorParameterProperties(String source, TSNode classBody) {
        List<FieldInfo> fields = new ArrayList<>();
        if (classBody == null) return fields;
        
        // Find the constructor method
        List<TSNode> methods = findAllChildren(classBody, "method_definition");
        for (TSNode method : methods) {
            TSNode nameNode = findFirstChild(method, "property_identifier");
            if (nameNode != null && "constructor".equals(getNodeText(source, nameNode))) {
                // Found constructor, analyze its parameters
                TSNode paramsNode = findFirstChild(method, "formal_parameters");
                if (paramsNode != null) {
                    int count = paramsNode.getNamedChildCount();
                    for (int i = 0; i < count; i++) {
                        TSNode param = paramsNode.getNamedChild(i);
                        if (param == null) continue;
                        
                        String paramType = param.getType();
                        if (!"required_parameter".equals(paramType) && !"optional_parameter".equals(paramType)) {
                            continue;
                        }
                        
                        // Check if this parameter has visibility modifier or readonly
                        String paramText = getNodeText(source, param);
                        if (paramText == null) continue;
                        
                        String trimmed = paramText.trim();
                        boolean hasPublic = trimmed.startsWith("public ");
                        boolean hasPrivate = trimmed.startsWith("private ");
                        boolean hasProtected = trimmed.startsWith("protected ");
                        boolean hasReadonly = trimmed.contains("readonly ");
                        
                        // Only create field if parameter has visibility modifier or readonly
                        if (hasPublic || hasPrivate || hasProtected || hasReadonly) {
                            FieldInfo fieldInfo = new FieldInfo();
                            
                            // Extract parameter name
                            TSNode paramNameNode = findFirstChild(param, "identifier");
                            if (paramNameNode != null) {
                                fieldInfo.name = getNodeText(source, paramNameNode);
                            }
                            
                            // Extract type annotation
                            TSNode typeAnnotation = findFirstChild(param, "type_annotation");
                            if (typeAnnotation != null) {
                                fieldInfo.type = getNodeText(source, typeAnnotation).replaceFirst("^:\\s*", "");
                            }
                            
                            // Set visibility and modifiers
                            if (hasPublic) {
                                fieldInfo.visibility = "public";
                                fieldInfo.modifiers.add("public");
                            } else if (hasPrivate) {
                                fieldInfo.visibility = "private";
                                fieldInfo.modifiers.add("private");
                            } else if (hasProtected) {
                                fieldInfo.visibility = "protected";
                                fieldInfo.modifiers.add("protected");
                            }
                            
                            if (hasReadonly) {
                                fieldInfo.modifiers.add("readonly");
                            }
                            
                            if (fieldInfo.name != null) {
                                fields.add(fieldInfo);
                            }
                        }
                    }
                }
                break; // Only one constructor per class
            }
        }
        
        return fields;
    }
    
    private void extractModifiersAndVisibility(String source, TSNode node, List<String> modifiers, Object target) {
        // TypeScript modifiers: public, private, protected, static, readonly, abstract, async, export
        // Check ALL children (not just named) since modifiers may be anonymous nodes
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child == null || child.isNull()) continue;
            
            String type = child.getType();
            
            // Check for modifier node types
            if ("accessibility_modifier".equals(type) || "readonly".equals(type) || 
                "static".equals(type) || "abstract".equals(type) || "async".equals(type) ||
                "override".equals(type)) {
                String modText = getNodeText(source, child);
                if (modText != null && !modifiers.contains(modText)) {
                    modifiers.add(modText);
                }
                
                // Set visibility only when explicitly specified
                if ("public".equals(modText) || "private".equals(modText) || "protected".equals(modText)) {
                    if (target instanceof TypeInfo) {
                        ((TypeInfo) target).visibility = modText;
                    } else if (target instanceof MethodInfo) {
                        ((MethodInfo) target).visibility = modText;
                    } else if (target instanceof FieldInfo) {
                        ((FieldInfo) target).visibility = modText;
                    }
                }
            }
            
            // Also check for literal keyword matches (some grammars expose them directly)
            if ("public".equals(type) || "private".equals(type) || "protected".equals(type)) {
                if (!modifiers.contains(type)) {
                    modifiers.add(type);
                    if (target instanceof TypeInfo) {
                        ((TypeInfo) target).visibility = type;
                    } else if (target instanceof MethodInfo) {
                        ((MethodInfo) target).visibility = type;
                    } else if (target instanceof FieldInfo) {
                        ((FieldInfo) target).visibility = type;
                    }
                }
            }
        }
        
        // Check parent for export
        TSNode parent = node.getParent();
        if (parent != null && "export_statement".equals(parent.getType())) {
            modifiers.add("export");
        }
    }
    
    private void extractDecorators(String source, TSNode node, List<String> annotations) {
        // Look for decorators as siblings before the declaration
        TSNode parent = node.getParent();
        if (parent != null) {
            int nodeIndex = -1;
            int siblingCount = parent.getNamedChildCount();
            
            // Find the index of our node
            for (int i = 0; i < siblingCount; i++) {
                if (parent.getNamedChild(i) == node) {
                    nodeIndex = i;
                    break;
                }
            }
            
            // Look backwards for decorators
            if (nodeIndex > 0) {
                for (int i = nodeIndex - 1; i >= 0; i--) {
                    TSNode sibling = parent.getNamedChild(i);
                    if ("decorator".equals(sibling.getType())) {
                        annotations.add(0, getNodeText(source, sibling));
                    } else {
                        break; // Stop at first non-decorator
                    }
                }
            }
        }
    }
    
    private String inferTypeFromExpression(String source, TSNode expr) {
        if (expr == null || expr.isNull()) return null;
        
        String exprType = expr.getType();
        
        // TypeScript-specific: Handle 'as' type assertions: (expr as Type)
        if ("as_expression".equals(exprType)) {
            TSNode typeNode = findFirstDescendant(expr, "type_identifier");
            if (typeNode != null) {
                return getNodeText(source, typeNode);
            }
        }
        
        // TypeScript-specific: Primitive literals use lowercase types
        if ("true".equals(exprType) || "false".equals(exprType)) {
            return "boolean";
        }
        if ("number".equals(exprType)) {
            return "number";
        }
        if ("string".equals(exprType) || "template_string".equals(exprType)) {
            return "string";
        }
        if ("null".equals(exprType)) {
            return "null";
        }
        
        // TypeScript-specific: React hooks handling for call expressions
        if ("call_expression".equals(exprType)) {
            TSNode callee = expr.getNamedChild(0);
            if (callee != null && "identifier".equals(callee.getType())) {
                String funcName = getNodeText(source, callee);
                // Common React hooks and their return types
                if ("useState".equals(funcName)) return "State";
                if ("useRef".equals(funcName)) return "Ref";
                if ("useMemo".equals(funcName)) return "Memoized";
                if ("useCallback".equals(funcName)) return "Callback";
            }
        }
        
        // Delegate to common inference for shared patterns (new, array, object, function, call)
        return inferCommonExpressionType(source, expr);
    }
    
}
