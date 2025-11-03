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

public class PythonAnalyzer implements LanguageAnalyzer {
    
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
        Set<Integer> nestedClassIds = identifyNestedClasses(allClasses);
        
        // Process only top-level classes recursively
        for (TSNode classDecl : allClasses) {
            if (classDecl == null || classDecl.isNull()) continue;
            if (!nestedClassIds.contains(classDecl.getStartByte())) {
                analyzeClassRecursively(sourceCode, classDecl, analysis);
            }
        }
        
        // Find standalone functions (not inside classes)
        List<TSNode> allFunctions = findAllDescendants(rootNode, "function_definition");
        for (TSNode funcDecl : allFunctions) {
            if (funcDecl == null || funcDecl.isNull()) {
                continue;
            }
            
            // Check if this function is not inside a class
            if (!isInsideClass(funcDecl)) {
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
        // Extract import statements
        List<TSNode> importStmts = findAllDescendants(rootNode, "import_statement");
        for (TSNode importStmt : importStmts) {
            analysis.imports.add(getNodeText(source, importStmt).trim());
        }
        
        // Extract from...import statements
        List<TSNode> importFromStmts = findAllDescendants(rootNode, "import_from_statement");
        for (TSNode importFromStmt : importFromStmts) {
            analysis.imports.add(getNodeText(source, importFromStmt).trim());
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
    
    private Set<Integer> identifyNestedClasses(List<TSNode> allClasses) {
        Set<Integer> nestedClassIds = new HashSet<>();
        for (TSNode classDecl : allClasses) {
            try {
                if (classDecl == null || classDecl.isNull()) continue;
                TSNode classBody = findFirstChild(classDecl, "block");
                if (classBody != null) {
                    List<TSNode> nested = findAllDescendants(classBody, "class_definition");
                    for (TSNode n : nested) {
                        if (n != null && !n.isNull()) {
                            nestedClassIds.add(n.getStartByte());
                        }
                    }
                }
            } catch (Exception e) {
                // Skip on error
            }
        }
        return nestedClassIds;
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
        List<TSNode> methods = findAllChildren(classBody, "function_definition");
        for (TSNode method : methods) {
            if (method == null || method.isNull()) continue;
            
            // Check if method is wrapped in decorated_definition
            TSNode methodParent = method.getParent();
            TSNode methodNodeToAnalyze = method;
            if (methodParent != null && !methodParent.isNull() && "decorated_definition".equals(methodParent.getType())) {
                methodNodeToAnalyze = methodParent;
            }
            
            MethodInfo methodInfo = analyzeMethod(source, method, methodNodeToAnalyze, typeInfo.name);
            if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                typeInfo.methods.add(methodInfo);
            }
        }
        
        // Recursively process nested classes
        List<TSNode> nestedClasses = findAllChildren(classBody, "class_definition");
        for (TSNode nested : nestedClasses) {
            if (nested != null && !nested.isNull()) {
                analyzeClassRecursivelyInto(source, nested, typeInfo.types);
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
        
        // Get class name - should be an identifier child
        TSNode nameNode = findFirstChild(classDefNode, "identifier");
        if (nameNode != null && !nameNode.isNull()) {
            typeInfo.name = getNodeText(source, nameNode);
        }
        
        // Determine visibility based on naming convention
        // Python uses naming conventions for visibility (not explicit keywords)
        if (typeInfo.name != null) {
            // Special methods (dunder methods like __init__, __str__) are public by convention
            if (typeInfo.name.startsWith("__") && typeInfo.name.endsWith("__")) {
                typeInfo.visibility = "public";
            } else if (typeInfo.name.startsWith("__")) {
                // Name mangled private methods
                typeInfo.visibility = "private";
            } else if (typeInfo.name.startsWith("_")) {
                typeInfo.visibility = "protected";
            } else {
                typeInfo.visibility = "public";
            }
        }
        
        // Get base classes (Python supports multiple inheritance)
        TSNode argumentList = findFirstChild(classDefNode, "argument_list");
        if (argumentList != null && !argumentList.isNull()) {
            List<TSNode> identifiers = findAllDescendants(argumentList, "identifier");
            for (int i = 0; i < identifiers.size(); i++) {
                String baseName = getNodeText(source, identifiers.get(i));
                if (i == 0) {
                    typeInfo.extendsType = baseName;
                } else {
                    typeInfo.implementsInterfaces.add(baseName);
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
            
            // Look for assignments
            if ("expression_statement".equals(child.getType())) {
                TSNode assignment = findFirstChild(child, "assignment");
                if (assignment != null && !assignment.isNull()) {
                    TSNode leftSide = assignment.getNamedChild(0);
                    if (leftSide != null && !leftSide.isNull() && "identifier".equals(leftSide.getType())) {
                        FieldInfo fieldInfo = new FieldInfo();
                        fieldInfo.name = getNodeText(source, leftSide);
                        
                        // Check for type annotation (e.g., name: str)
                        TSNode typeNode = getChildByFieldName(assignment, "type");
                        
                        if (typeNode != null && !typeNode.isNull()) {
                            fieldInfo.type = getNodeText(source, typeNode);
                        } else {
                            // Try to infer type from the right side
                            TSNode rightSide = getChildByFieldName(assignment, "right");
                            if (rightSide != null && !rightSide.isNull()) {
                                fieldInfo.type = inferType(source, rightSide);
                            }
                        }
                        
                        // Python uses naming conventions for visibility (not explicit keywords)
                        if (fieldInfo.name != null) {
                            if (fieldInfo.name.startsWith("__") && !fieldInfo.name.endsWith("__")) {
                                fieldInfo.visibility = "private";
                            } else if (fieldInfo.name.startsWith("_")) {
                                fieldInfo.visibility = "protected";
                            } else {
                                fieldInfo.visibility = "public";
                            }
                        }
                        
                        fields.add(fieldInfo);
                    }
                }
            }
        }
        
        return fields;
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
                // Try to get the function name being called
                TSNode funcNode = node.getNamedChild(0);
                if (funcNode != null && !funcNode.isNull()) {
                    return getNodeText(source, funcNode);
                }
                break;
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
        
        // Get function name - should be an identifier child
        TSNode nameNode = findFirstChild(funcDef, "identifier");
        if (nameNode != null && !nameNode.isNull()) {
            methodInfo.name = getNodeText(source, nameNode);
        }
        
        // Determine visibility based on naming convention
        // Python uses naming conventions for visibility (not explicit keywords)
        if (methodInfo.name != null) {
            // Special methods (dunder methods like __init__, __str__) are public by convention
            if (methodInfo.name.startsWith("__") && methodInfo.name.endsWith("__")) {
                methodInfo.visibility = "public";
            } else if (methodInfo.name.startsWith("__")) {
                // Name mangled private methods
                methodInfo.visibility = "private";
            } else if (methodInfo.name.startsWith("_")) {
                methodInfo.visibility = "protected";
            } else {
                methodInfo.visibility = "public";
            }
        }
        
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
        
        // Python parameters can be: identifier, typed_parameter, default_parameter, etc.
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
                // Extract name and type
                TSNode nameNode = findFirstChild(paramNode, "identifier");
                TSNode typeNode = findFirstChild(paramNode, "type");
                
                if (nameNode != null && !nameNode.isNull()) {
                    paramName = getNodeText(source, nameNode);
                }
                if (typeNode != null && !typeNode.isNull()) {
                    paramTypeHint = getNodeText(source, typeNode);
                }
            } else if ("default_parameter".equals(paramType)) {
                TSNode nameNode = findFirstChild(paramNode, "identifier");
                if (nameNode != null && !nameNode.isNull()) {
                    paramName = getNodeText(source, nameNode);
                }
            }
            
            // Skip 'self' and 'cls' parameters, and validate parameter name
            if (paramName != null && !"self".equals(paramName) && !"cls".equals(paramName)) {
                // Validate parameter name is a valid identifier
                if (paramName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                    methodInfo.parameters.add(new Parameter(paramName, paramTypeHint));
                }
            }
        }
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
                if (varName != null && !varName.startsWith("self.") && !methodInfo.localVariables.contains(varName)
                    && varName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
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
                
                if (methodName != null && !methodName.isEmpty()) {
                    // Validate method name - should be a valid identifier (letters, numbers, underscores)
                    if (methodName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                        // Aggregate method calls
                        boolean found = false;
                        for (MethodCall existingCall : methodInfo.methodCalls) {
                            if (existingCall.matches(methodName, objectType, objectName, null)) {
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
