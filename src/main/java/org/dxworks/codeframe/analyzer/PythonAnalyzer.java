package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.*;
import org.treesitter.TSNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.dxworks.codeframe.analyzer.TreeSitterHelper.*;

/**
 * Python language analyzer using Tree-sitter.
 * 
 * Extracts classes, methods, fields, type aliases, and method calls from Python source files.
 * Uses Python naming conventions for visibility (underscore prefix = protected, double underscore = private).
 */

public class PythonAnalyzer implements LanguageAnalyzer {
    
    /**
     * Determines visibility based on Python naming conventions.
     * - Names starting with __ (but not ending with __) are private (name mangling)
     * - Names starting with _ are protected (convention)
     * - Dunder names (__name__) and regular names are public
     */
    private static String determineVisibility(String name) {
        if (name == null) return "public";
        if (name.startsWith("__") && name.endsWith("__")) return "public";  // dunder methods
        if (name.startsWith("__")) return "private";  // name mangled
        if (name.startsWith("_")) return "protected";
        return "public";
    }
    
    @Override
    public FileAnalysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        FileAnalysis analysis = new FileAnalysis();
        analysis.filePath = filePath;
        analysis.language = "python";
        
        try {
            // Extract imports
            extractImports(sourceCode, rootNode, analysis);
        
        // Find all class definitions and identify nested ones
        List<TSNode> allClasses = findAllDescendants(rootNode, "class_definition");
        Set<Integer> nestedClassIds = identifyNestedNodes(allClasses, "block", "class_definition");
        
        // Process only top-level classes recursively
        for (TSNode classDecl : allClasses) {
            if (classDecl == null || classDecl.isNull()) continue;
            if (!nestedClassIds.contains(classDecl.getStartByte())) {
                analyzeClassRecursively(sourceCode, classDecl, analysis);
            }
        }
        
        // Extract module-level elements
        extractTypeAliases(sourceCode, rootNode, analysis);
        extractFileLevelFields(sourceCode, rootNode, analysis);
        extractFileLevelMethodCalls(sourceCode, rootNode, analysis);
        
        // Find standalone functions (not inside classes or other functions)
        // Nested functions are NOT extracted as separate methods - their calls/variables
        // are captured in the parent function via findAllDescendants in analyzeMethodBody
        List<TSNode> allFunctions = findAllDescendants(rootNode, "function_definition");
        for (TSNode funcDecl : allFunctions) {
            if (funcDecl == null || funcDecl.isNull()) {
                continue;
            }
            
            // Check if this function is not inside a class and not nested in another function
            if (!isInsideClass(funcDecl) && !isNestedFunction(funcDecl)) {
                // Check if function is wrapped in decorated_definition
                TSNode funcParent = funcDecl.getParent();
                TSNode funcNodeToAnalyze = funcDecl;
                if (funcParent != null && !funcParent.isNull() && "decorated_definition".equals(funcParent.getType())) {
                    funcNodeToAnalyze = funcParent;
                }
                
                MethodInfo methodInfo = analyzeMethod(sourceCode, funcDecl, funcNodeToAnalyze, null);
                // Only add methods with valid names
                if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                    analysis.methods.add(methodInfo);
                } else {
                    System.err.println("Warning: Skipping standalone function with null or empty name in " + filePath);
                }
            }
        }
        } catch (Exception e) {
            System.err.println("Error during Python analysis: " + e.getMessage());
            e.printStackTrace();
        }
        
        return analysis;
    }
    
    private void extractImports(String source, TSNode rootNode, FileAnalysis analysis) {
        // Extract all import types: import, from...import, and from __future__ import
        List<TSNode> allImports = findAllDescendantsOfTypes(rootNode, 
            "import_statement", "import_from_statement", "future_import_statement");
        for (TSNode importNode : allImports) {
            String importText = getNodeText(source, importNode);
            if (importText != null) {
                analysis.imports.add(importText.trim());
            }
        }
    }
    
    /**
     * Extract type aliases (both old-style TypeAlias annotation and PEP 695 style).
     */
    private void extractTypeAliases(String source, TSNode rootNode, FileAnalysis analysis) {
        // PEP 695 style: type Name = SomeType
        for (TSNode typeAliasStmt : findAllDescendants(rootNode, "type_alias_statement")) {
            TypeInfo typeAlias = analyzeTypeAliasStatement(source, typeAliasStmt);
            if (typeAlias != null && typeAlias.name != null) {
                analysis.types.add(typeAlias);
            }
        }
        
        // Old-style: Name: TypeAlias = SomeType
        for (int i = 0; i < rootNode.getNamedChildCount(); i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            if (!"expression_statement".equals(child.getType())) continue;
            
            TSNode expr = child.getNamedChild(0);
            if (expr != null && "assignment".equals(expr.getType())) {
                TypeInfo typeAlias = analyzeOldStyleTypeAlias(source, expr);
                if (typeAlias != null && typeAlias.name != null) {
                    analysis.types.add(typeAlias);
                }
            }
        }
    }
    
    /**
     * Extract file-level constants and variables (excluding type aliases).
     */
    private void extractFileLevelFields(String source, TSNode rootNode, FileAnalysis analysis) {
        for (int i = 0; i < rootNode.getNamedChildCount(); i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            if (!"expression_statement".equals(child.getType())) continue;
            
            TSNode expr = child.getNamedChild(0);
            if (expr == null || !"assignment".equals(expr.getType())) continue;
            
            // Skip type aliases
            TSNode typeNode = getChildByFieldName(expr, "type");
            if (typeNode != null && !typeNode.isNull()) {
                String typeText = getNodeText(source, typeNode);
                if (typeText != null && typeText.contains("TypeAlias")) continue;
            }
            
            FieldInfo field = extractFieldFromAssignment(source, expr);
            if (field != null && field.name != null) {
                analysis.fields.add(field);
            }
        }
    }
    
    /**
     * Extract file-level method calls (all function calls at module level, outside any named function or class).
     * This includes calls inside callbacks passed to top-level calls.
     */
    private void extractFileLevelMethodCalls(String source, TSNode rootNode, FileAnalysis analysis) {
        for (int i = 0; i < rootNode.getNamedChildCount(); i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            if (!"expression_statement".equals(child.getType())) continue;
            
            // Extract ALL calls within the expression statement (including nested callbacks)
            List<TSNode> allCalls = findAllDescendants(child, "call");
            for (TSNode callExpr : allCalls) {
                extractCallIntoList(source, callExpr, analysis.methodCalls);
            }
        }
        analysis.methodCalls.sort(TreeSitterHelper.METHOD_CALL_COMPARATOR);
    }
    
    /**
     * Analyze PEP 695 type alias statement: type Name = SomeType
     */
    private TypeInfo analyzeTypeAliasStatement(String source, TSNode typeAliasStmt) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "type_alias";
        typeInfo.visibility = "public";
        
        // Get the name (type_identifier or identifier)
        TSNode nameNode = getChildByFieldName(typeAliasStmt, "name");
        if (nameNode != null && !nameNode.isNull()) {
            typeInfo.name = getNodeText(source, nameNode);
        }
        
        // Get the aliased type (the value after =)
        TSNode valueNode = getChildByFieldName(typeAliasStmt, "value");
        if (valueNode != null && !valueNode.isNull()) {
            typeInfo.extendsType = getNodeText(source, valueNode);
        }
        
        // Check for type parameters (generics)
        TSNode typeParams = getChildByFieldName(typeAliasStmt, "type_parameters");
        if (typeParams != null && !typeParams.isNull() && typeInfo.name != null) {
            typeInfo.name = typeInfo.name + getNodeText(source, typeParams);
        }
        
        return typeInfo;
    }
    
    /**
     * Analyze old-style type alias: Name: TypeAlias = SomeType
     * Returns null if this is not a type alias assignment.
     */
    private TypeInfo analyzeOldStyleTypeAlias(String source, TSNode assignment) {
        // Structure: assignment has left (identifier with type annotation) and right (the aliased type)
        // The annotation should contain "TypeAlias"
        
        TSNode leftNode = getChildByFieldName(assignment, "left");
        TSNode rightNode = getChildByFieldName(assignment, "right");
        
        if (leftNode == null || leftNode.isNull() || rightNode == null || rightNode.isNull()) {
            return null;
        }
        
        // Check if left is an identifier with a type annotation containing "TypeAlias"
        String leftType = leftNode.getType();
        String name = null;
        String annotation = null;
        
        if ("identifier".equals(leftType)) {
            // Simple case: Name: TypeAlias = ...
            // The type annotation is a sibling in the assignment
            TSNode typeNode = getChildByFieldName(assignment, "type");
            if (typeNode != null && !typeNode.isNull()) {
                annotation = getNodeText(source, typeNode);
                name = getNodeText(source, leftNode);
            }
        }
        
        // Check if the annotation contains "TypeAlias"
        if (annotation == null || !annotation.contains("TypeAlias")) {
            return null;
        }
        
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "type_alias";
        typeInfo.name = name;
        typeInfo.visibility = determineVisibility(name);
        typeInfo.extendsType = getNodeText(source, rightNode);
        return typeInfo;
    }
    
    /** Extract a single call expression into a method call list. */
    private void extractCallIntoList(String source, TSNode callNode, List<MethodCall> calls) {
        TSNode functionNode = callNode.getNamedChild(0);
        if (functionNode == null || functionNode.isNull()) return;
        
        String methodName = null;
        String objectName = null;
        
        if ("attribute".equals(functionNode.getType())) {
            TSNode objNode = functionNode.getNamedChild(0);
            List<TSNode> identifiers = findAllChildren(functionNode, "identifier");
            if (!identifiers.isEmpty()) {
                methodName = getNodeText(source, identifiers.get(identifiers.size() - 1));
            }
            if (objNode != null && !objNode.isNull()) {
                objectName = getNodeText(source, objNode);
            }
        } else if ("identifier".equals(functionNode.getType())) {
            methodName = getNodeText(source, functionNode);
        }
        
        if (methodName != null && isValidPythonIdentifier(methodName)) {
            collectMethodCall(calls, methodName, null, objectName);
        }
    }
    
    private boolean isInsideClass(TSNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        
        TSNode parent = node.getParent();
        while (parent != null && !parent.isNull()) {
            String parentType = parent.getType();
            
            // If we hit a class_definition, the function is inside a class
            if ("class_definition".equals(parentType)) {
                return true;
            }
            
            // If we hit a module (root), we're at the top level
            if ("module".equals(parentType)) {
                return false;
            }
            parent = parent.getParent();
        }
        return false;
    }
    
    /**
     * Check if a function is nested inside another function.
     * Nested functions are not extracted as separate methods - their calls/variables
     * are captured in the parent function.
     */
    private boolean isNestedFunction(TSNode funcNode) {
        if (funcNode == null || funcNode.isNull()) {
            return false;
        }
        
        TSNode parent = funcNode.getParent();
        // Skip decorated_definition wrapper if present
        if (parent != null && "decorated_definition".equals(parent.getType())) {
            parent = parent.getParent();
        }
        
        while (parent != null && !parent.isNull()) {
            String parentType = parent.getType();
            
            // If we hit another function_definition, this is nested
            if ("function_definition".equals(parentType)) {
                return true;
            }
            
            // If we hit module (root), we're at the top level
            if ("module".equals(parentType)) {
                return false;
            }
            
            parent = parent.getParent();
        }
        return false;
    }
    
    private void analyzeClassRecursively(String source, TSNode classDecl, FileAnalysis analysis) {
        analyzeClassRecursivelyInto(source, classDecl, analysis.types);
    }
    
    private void analyzeClassRecursivelyInto(String source, TSNode classDecl, List<TypeInfo> targetList) {
        // Check if this class is wrapped in a decorated_definition
        TSNode classNodeToAnalyze = classDecl;
        TSNode parent = classDecl.getParent();
        if (parent != null && !parent.isNull() && "decorated_definition".equals(parent.getType())) {
            classNodeToAnalyze = parent;
        }
        
        TypeInfo typeInfo = analyzeClass(source, classDecl, classNodeToAnalyze);
        if (typeInfo.name == null || typeInfo.name.isEmpty()) {
            return;
        }
        targetList.add(typeInfo);
        
        TSNode classBody = findFirstChild(classDecl, "block");
        if (classBody == null) {
            return;
        }
        
        // Collect fields/attributes from this class only
        List<FieldInfo> fields = collectClassAttributesFromBody(source, classBody);
        typeInfo.fields.addAll(fields);
        
        // Analyze methods within this class only
        // We need to look for both direct function_definition AND decorated_definition children
        List<TSNode> methods = findAllChildren(classBody, "function_definition");
        List<TSNode> decoratedDefs = findAllChildren(classBody, "decorated_definition");
        
        // Process undecorated methods
        for (TSNode method : methods) {
            if (method == null || method.isNull()) continue;
            
            MethodInfo methodInfo = analyzeMethod(source, method, method, typeInfo.name);
            if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                typeInfo.methods.add(methodInfo);
            }
        }
        
        // Process decorated methods (including @staticmethod, @classmethod, @property)
        for (TSNode decoratedDef : decoratedDefs) {
            if (decoratedDef == null || decoratedDef.isNull()) continue;
            
            // Find the function_definition inside the decorated_definition
            TSNode funcDef = findFirstChild(decoratedDef, "function_definition");
            if (funcDef == null || funcDef.isNull()) continue;
            
            MethodInfo methodInfo = analyzeMethod(source, funcDef, decoratedDef, typeInfo.name);
            if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                typeInfo.methods.add(methodInfo);
            }
        }
        
        // Recursively process nested classes (both decorated and undecorated)
        List<TSNode> nestedClasses = findAllChildren(classBody, "class_definition");
        for (TSNode nested : nestedClasses) {
            if (nested != null && !nested.isNull()) {
                analyzeClassRecursivelyInto(source, nested, typeInfo.types);
            }
        }
        
        // Also check for decorated nested classes (e.g., @dataclass inside a class)
        for (TSNode decoratedDef : decoratedDefs) {
            if (decoratedDef == null || decoratedDef.isNull()) continue;
            
            // Check if this decorated_definition contains a class_definition (not function)
            TSNode nestedClass = findFirstChild(decoratedDef, "class_definition");
            if (nestedClass != null && !nestedClass.isNull()) {
                analyzeClassRecursivelyInto(source, nestedClass, typeInfo.types);
            }
        }
    }
    
    private TypeInfo analyzeClass(String source, TSNode classDefNode, TSNode decoratedNode) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "class";
        
        if (classDefNode == null || classDefNode.isNull()) {
            return typeInfo;
        }
        
        // Extract decorators (Python's annotations)
        extractDecorators(source, decoratedNode, typeInfo.annotations);
        
        // Get class name
        typeInfo.name = extractName(source, classDefNode, "identifier");
        typeInfo.visibility = determineVisibility(typeInfo.name);
        
        // Get base classes (Python supports multiple inheritance)
        // Base classes can be: identifier (e.g., "Repository") or attribute (e.g., "abc.ABC")
        TSNode argumentList = findFirstChild(classDefNode, "argument_list");
        if (argumentList != null && !argumentList.isNull()) {
            List<String> baseClasses = new ArrayList<>();
            for (int i = 0; i < argumentList.getNamedChildCount(); i++) {
                TSNode child = argumentList.getNamedChild(i);
                if (child == null || child.isNull()) continue;
                
                String childType = child.getType();
                if ("identifier".equals(childType) || "attribute".equals(childType)) {
                    // For both simple names and qualified names (abc.ABC), get full text
                    baseClasses.add(getNodeText(source, child));
                }
            }
            
            for (int i = 0; i < baseClasses.size(); i++) {
                if (i == 0) {
                    typeInfo.extendsType = baseClasses.get(i);
                } else {
                    typeInfo.implementsInterfaces.add(baseClasses.get(i));
                }
            }
        }
        
        return typeInfo;
    }
    
    private List<FieldInfo> collectClassAttributesFromBody(String source, TSNode classBody) {
        List<FieldInfo> fields = new ArrayList<>();
        if (classBody == null) return fields;
        
        // In Python, class attributes are assignment statements at the class level
        // Look for assignment statements at class level (not inside methods)
        for (int i = 0; i < classBody.getNamedChildCount(); i++) {
            TSNode child = classBody.getNamedChild(i);
            if (child == null || child.isNull()) {
                continue;
            }
            
            // Skip function definitions
            if ("function_definition".equals(child.getType()) || "decorated_definition".equals(child.getType())) {
                continue;
            }
            
            // Look for assignments in expression_statement
            if ("expression_statement".equals(child.getType())) {
                // Try regular assignment first
                TSNode assignment = findFirstChild(child, "assignment");
                if (assignment != null && !assignment.isNull()) {
                    FieldInfo fieldInfo = extractFieldFromAssignment(source, assignment);
                    if (fieldInfo != null) {
                        fields.add(fieldInfo);
                    }
                }
            }
            
            // Also handle typed class attributes without assignment (e.g., "name: str")
            // These appear as "type" nodes directly in the class body
            if ("type".equals(child.getType())) {
                FieldInfo fieldInfo = extractFieldFromTypeAnnotation(source, child);
                if (fieldInfo != null) {
                    fields.add(fieldInfo);
                }
            }
        }
        
        return fields;
    }
    
    /**
     * Extracts field information from an assignment node.
     * Handles both simple assignments (x = 1) and typed assignments (x: int = 1).
     */
    private FieldInfo extractFieldFromAssignment(String source, TSNode assignment) {
        if (assignment == null || assignment.isNull()) {
            return null;
        }
        
        // Get field components using tree-sitter field names
        TSNode leftNode = getChildByFieldName(assignment, "left");
        TSNode typeNode = getChildByFieldName(assignment, "type");
        TSNode rightNode = getChildByFieldName(assignment, "right");
        
        // Extract field name from left side (must be a simple identifier)
        String fieldName = null;
        if (leftNode != null && !leftNode.isNull() && "identifier".equals(leftNode.getType())) {
            fieldName = getNodeText(source, leftNode);
        }
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        
        // Get type from annotation, or infer from value
        String fieldType = null;
        if (typeNode != null && !typeNode.isNull()) {
            fieldType = getNodeText(source, typeNode);
        } else if (rightNode != null && !rightNode.isNull()) {
            fieldType = inferType(source, rightNode);
        }
        
        FieldInfo fieldInfo = new FieldInfo();
        fieldInfo.name = fieldName;
        fieldInfo.type = fieldType;
        fieldInfo.visibility = determineVisibility(fieldName);
        return fieldInfo;
    }
    
    /**
     * Extracts field information from a standalone type annotation (e.g., "name: str" without assignment).
     */
    private FieldInfo extractFieldFromTypeAnnotation(String source, TSNode typeNode) {
        if (typeNode == null || typeNode.isNull()) {
            return null;
        }
        
        // Type annotation node structure: identifier followed by type
        TSNode identNode = findFirstChild(typeNode, "identifier");
        if (identNode == null || identNode.isNull()) {
            return null;
        }
        
        String fieldName = getNodeText(source, identNode);
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        
        // Get the type - it's usually the text after the colon
        String fullText = getNodeText(source, typeNode);
        String fieldType = null;
        if (fullText != null && fullText.contains(":")) {
            fieldType = fullText.substring(fullText.indexOf(":") + 1).trim();
        }
        
        FieldInfo fieldInfo = new FieldInfo();
        fieldInfo.name = fieldName;
        fieldInfo.type = fieldType;
        fieldInfo.visibility = determineVisibility(fieldName);
        return fieldInfo;
    }
    
    private String inferType(String source, TSNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        
        String nodeType = node.getType();
        
        switch (nodeType) {
            case "integer":
                return "int";
            case "float":
                return "float";
            case "string":
                return "str";
            case "true":
            case "false":
                return "bool";
            case "list":
                return "list";
            case "dictionary":
                return "dict";
            case "tuple":
                return "tuple";
            case "set":
                return "set";
            case "none":
                return "None";
            case "call":
                // Can't determine return type from a call without type inference
                return null;
        }
        
        return null;
    }
    
    private MethodInfo analyzeMethod(String source, TSNode funcDef, TSNode decoratedNode, String className) {
        MethodInfo methodInfo = new MethodInfo();
        
        if (funcDef == null || funcDef.isNull()) {
            return methodInfo;
        }
        
        // Extract decorators
        extractDecorators(source, decoratedNode, methodInfo.annotations);
        
        // Get function name
        methodInfo.name = extractName(source, funcDef, "identifier");
        methodInfo.visibility = determineVisibility(methodInfo.name);
        
        // Check if function is async by looking for the async keyword before the function
        // In Python tree-sitter, check if there's text "async" before the function definition
        int funcStart = funcDef.getStartByte();
        // Look back up to 10 characters for "async " keyword
        int lookbackStart = Math.max(0, funcStart - 10);
        String precedingText = source.substring(lookbackStart, Math.min(funcStart + 6, source.length()));
        if (precedingText.contains("async ")) {
            methodInfo.modifiers.add("async");
        }
        
        // Check for special decorators that affect modifiers
        for (String annotation : methodInfo.annotations) {
            if (annotation.contains("@staticmethod")) {
                methodInfo.modifiers.add("static");
            } else if (annotation.contains("@classmethod")) {
                methodInfo.modifiers.add("classmethod");
            } else if (annotation.contains("@property")) {
                methodInfo.modifiers.add("property");
            }
        }
        
        // Get parameters with type hints
        TSNode paramsNode = findFirstChild(funcDef, "parameters");
        if (paramsNode != null && !paramsNode.isNull()) {
            extractParameters(source, paramsNode, methodInfo);
        }
        
        // Get return type from type hint using field name
        TSNode returnTypeNode = getChildByFieldName(funcDef, "return_type");
        if (returnTypeNode != null && !returnTypeNode.isNull()) {
            methodInfo.returnType = getNodeText(source, returnTypeNode);
        }
        
        // Get function body
        TSNode bodyNode = findFirstChild(funcDef, "block");
        if (bodyNode != null && !bodyNode.isNull()) {
            analyzeMethodBody(source, bodyNode, methodInfo, className);
        }
        
        return methodInfo;
    }
    
    private void extractParameters(String source, TSNode paramsNode, MethodInfo methodInfo) {
        if (paramsNode == null || paramsNode.isNull()) {
            return;
        }
        
        // Python parameters can be: identifier, typed_parameter, default_parameter,
        // list_splat_pattern (*args), dictionary_splat_pattern (**kwargs), etc.
        for (int i = 0; i < paramsNode.getNamedChildCount(); i++) {
            TSNode paramNode = paramsNode.getNamedChild(i);
            if (paramNode == null || paramNode.isNull()) {
                continue;
            }
            
            String paramType = paramNode.getType();
            
            String paramName = null;
            String paramTypeHint = null;
            
            if ("identifier".equals(paramType)) {
                paramName = getNodeText(source, paramNode);
            } else if ("typed_parameter".equals(paramType) || "typed_default_parameter".equals(paramType)) {
                // Could be regular typed param or typed variadic (*args: Any, **kwargs: Any)
                TSNode splatNode = findFirstChild(paramNode, "list_splat_pattern");
                TSNode dictSplatNode = findFirstChild(paramNode, "dictionary_splat_pattern");
                TSNode typeNode = findFirstChild(paramNode, "type");
                
                if (splatNode != null && !splatNode.isNull()) {
                    // Typed *args
                    TSNode nameNode = findFirstChild(splatNode, "identifier");
                    if (nameNode != null && !nameNode.isNull()) {
                        paramName = "*" + getNodeText(source, nameNode);
                    }
                } else if (dictSplatNode != null && !dictSplatNode.isNull()) {
                    // Typed **kwargs
                    TSNode nameNode = findFirstChild(dictSplatNode, "identifier");
                    if (nameNode != null && !nameNode.isNull()) {
                        paramName = "**" + getNodeText(source, nameNode);
                    }
                } else {
                    // Regular typed parameter
                    TSNode nameNode = findFirstChild(paramNode, "identifier");
                    if (nameNode != null && !nameNode.isNull()) {
                        paramName = getNodeText(source, nameNode);
                    }
                }
                
                if (typeNode != null && !typeNode.isNull()) {
                    paramTypeHint = getNodeText(source, typeNode);
                }
            } else if ("default_parameter".equals(paramType)) {
                TSNode nameNode = findFirstChild(paramNode, "identifier");
                if (nameNode != null && !nameNode.isNull()) {
                    paramName = getNodeText(source, nameNode);
                }
            } else if ("list_splat_pattern".equals(paramType)) {
                // *args - variadic positional parameter (untyped)
                TSNode nameNode = findFirstChild(paramNode, "identifier");
                if (nameNode != null && !nameNode.isNull()) {
                    paramName = "*" + getNodeText(source, nameNode);
                }
            } else if ("dictionary_splat_pattern".equals(paramType)) {
                // **kwargs - variadic keyword parameter (untyped)
                TSNode nameNode = findFirstChild(paramNode, "identifier");
                if (nameNode != null && !nameNode.isNull()) {
                    paramName = "**" + getNodeText(source, nameNode);
                }
            }
            
            // Skip 'self' and 'cls' parameters, and validate parameter name
            if (paramName != null && !"self".equals(paramName) && !"cls".equals(paramName)) {
                // Validate parameter name (strip * or ** prefix for variadics before checking)
                String cleanName = paramName.replaceFirst("^\\*\\*?", "");
                if (isValidPythonIdentifier(cleanName)) {
                    methodInfo.parameters.add(new Parameter(paramName, paramTypeHint));
                }
            }
        }
    }
    
    /** Checks if a name is a valid Python identifier. */
    private static boolean isValidPythonIdentifier(String name) {
        return name != null && name.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }
    
    private void analyzeMethodBody(String source, TSNode bodyNode, MethodInfo methodInfo, String className) {
        if (bodyNode == null || bodyNode.isNull()) {
            return;
        }
        
        // Track local variable types
        Map<String, String> localTypes = new HashMap<>();
        
        // Find assignment statements for local variables
        List<TSNode> assignments = findAllDescendants(bodyNode, "assignment");
        for (TSNode assignment : assignments) {
            if (assignment == null || assignment.isNull()) {
                continue;
            }
            
            TSNode leftSide = assignment.getNamedChild(0);
            if (leftSide != null && !leftSide.isNull() && "identifier".equals(leftSide.getType())) {
                String varName = getNodeText(source, leftSide);
                // Avoid adding 'self' attributes and validate variable name
                if (varName != null && !varName.startsWith("self.") 
                    && !methodInfo.localVariables.contains(varName)
                    && isValidPythonIdentifier(varName)) {
                    methodInfo.localVariables.add(varName);
                    
                    // Try to infer type
                    if (assignment.getNamedChildCount() > 1) {
                        TSNode rightSide = assignment.getNamedChild(1);
                        if (rightSide != null && !rightSide.isNull()) {
                            String inferredType = inferType(source, rightSide);
                            if (inferredType != null) {
                                localTypes.put(varName, inferredType);
                            }
                        }
                    }
                }
            }
        }
        
        // Find call expressions
        List<TSNode> callExprs = findAllDescendants(bodyNode, "call");
        for (TSNode callExpr : callExprs) {
            if (callExpr == null || callExpr.isNull()) {
                continue;
            }
            
            TSNode functionNode = callExpr.getNamedChild(0);
            if (functionNode != null && !functionNode.isNull()) {
                String methodName = null;
                String objectName = null;
                String objectType = null;
                
                if ("attribute".equals(functionNode.getType())) {
                    // obj.method() call
                    TSNode objNode = functionNode.getNamedChild(0);
                    // The attribute name is the last identifier child
                    List<TSNode> identifiers = findAllChildren(functionNode, "identifier");
                    if (!identifiers.isEmpty()) {
                        TSNode attrNode = identifiers.get(identifiers.size() - 1);
                        if (attrNode != null && !attrNode.isNull()) {
                            methodName = getNodeText(source, attrNode);
                        }
                    }
                    
                    if (objNode != null && !objNode.isNull()) {
                        String objType = objNode.getType();
                        
                        if ("identifier".equals(objType)) {
                            objectName = getNodeText(source, objNode);
                            objectType = localTypes.get(objectName);
                            
                            // Check for 'self'
                            if ("self".equals(objectName) && className != null) {
                                objectType = className;
                            }
                        } else if ("attribute".equals(objType)) {
                            // Chained call like obj.prop.method() - get the full chain
                            objectName = getNodeText(source, objNode);
                        }
                    }
                } else if ("identifier".equals(functionNode.getType())) {
                    // Direct function call
                    methodName = getNodeText(source, functionNode);
                }
                
                if (methodName != null && !methodName.isEmpty() && isValidPythonIdentifier(methodName)) {
                    collectMethodCall(methodInfo, methodName, objectType, objectName);
                }
            }
        }
        
        // Sort method calls consistently with other analyzers
        methodInfo.methodCalls.sort(TreeSitterHelper.METHOD_CALL_COMPARATOR);
    }
    
    private void extractDecorators(String source, TSNode decoratedNode, List<String> annotations) {
        if (decoratedNode == null || decoratedNode.isNull()) {
            return;
        }
        
        // If this is a decorated_definition, extract decorators
        if ("decorated_definition".equals(decoratedNode.getType())) {
            List<TSNode> decorators = findAllChildren(decoratedNode, "decorator");
            for (TSNode decorator : decorators) {
                if (decorator != null && !decorator.isNull()) {
                    annotations.add(getNodeText(source, decorator));
                }
            }
        }
    }
}
