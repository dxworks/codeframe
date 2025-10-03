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

public class JavaScriptAnalyzer implements LanguageAnalyzer {
    
    @Override
    public FileAnalysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        FileAnalysis analysis = new FileAnalysis();
        analysis.filePath = filePath;
        analysis.language = "javascript";
        
        // Collect imports
        List<TSNode> importStmts = findAllDescendants(rootNode, "import_statement");
        for (TSNode imp : importStmts) {
            String text = getNodeText(sourceCode, imp).trim();
            analysis.imports.add(text);
        }
        
        // Find all class declarations and identify nested ones
        List<TSNode> allClasses = findAllDescendants(rootNode, "class_declaration");
        Set<Integer> nestedClassIds = identifyNestedClasses(allClasses);
        
        // Process only top-level classes recursively
        for (TSNode classDecl : allClasses) {
            if (!nestedClassIds.contains(classDecl.getStartByte())) {
                analyzeClassRecursively(sourceCode, classDecl, analysis);
            }
        }
        
        // Find standalone functions
        List<TSNode> functionDecls = findAllDescendants(rootNode, "function_declaration");
        for (TSNode funcDecl : functionDecls) {
            MethodInfo methodInfo = analyzeFunction(sourceCode, funcDecl);
            if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                analysis.methods.add(methodInfo);
            }
        }
        
        return analysis;
    }
    
    private Set<Integer> identifyNestedClasses(List<TSNode> allClasses) {
        Set<Integer> nestedClassIds = new HashSet<>();
        for (TSNode classDecl : allClasses) {
            TSNode classBody = findFirstChild(classDecl, "class_body");
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
        
        TSNode classBody = findFirstChild(classDecl, "class_body");
        if (classBody == null) {
            return;
        }
        
        // Collect fields for this class only
        List<FieldInfo> fields = collectFieldsFromBody(source, classBody);
        typeInfo.fields.addAll(fields);
        
        // Analyze methods within this class only
        List<TSNode> methods = findAllChildren(classBody, "method_definition");
        for (TSNode method : methods) {
            MethodInfo methodInfo = analyzeMethod(source, method, typeInfo.name);
            if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                typeInfo.methods.add(methodInfo);
            }
        }
        
        // Recursively process nested classes
        List<TSNode> nestedClasses = findAllChildren(classBody, "class_declaration");
        for (TSNode nested : nestedClasses) {
            analyzeClassRecursivelyInto(source, nested, typeInfo.types);
        }
    }
    
    private TypeInfo analyzeClass(String source, TSNode classDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "class";
        
        // Get class name
        TSNode nameNode = findFirstChild(classDecl, "identifier");
        if (nameNode != null) {
            typeInfo.name = getNodeText(source, nameNode);
        }
        
        // Get heritage clause (extends)
        TSNode heritageNode = findFirstChild(classDecl, "class_heritage");
        if (heritageNode != null) {
            TSNode extendsClause = findFirstChild(heritageNode, "extends_clause");
            if (extendsClause != null) {
                TSNode typeId = findFirstDescendant(extendsClause, "identifier");
                if (typeId != null) {
                    typeInfo.extendsType = getNodeText(source, typeId);
                }
            }
        }
        
        return typeInfo;
    }
    
    private MethodInfo analyzeMethod(String source, TSNode methodDef, String className) {
        MethodInfo methodInfo = new MethodInfo();
        
        // Check for async modifier
        List<TSNode> children = new ArrayList<>();
        for (int i = 0; i < methodDef.getNamedChildCount(); i++) {
            children.add(methodDef.getNamedChild(i));
        }
        for (TSNode child : children) {
            if ("async".equals(child.getType())) {
                methodInfo.modifiers.add("async");
            } else if ("static".equals(child.getType())) {
                methodInfo.modifiers.add("static");
            }
        }
        
        // Get method name
        TSNode nameNode = findFirstChild(methodDef, "property_identifier");
        if (nameNode != null) {
            methodInfo.name = getNodeText(source, nameNode);
        }
        
        // JavaScript only has # prefix for private members (no explicit visibility keywords)
        if (methodInfo.name != null && methodInfo.name.startsWith("#")) {
            methodInfo.visibility = "private";
        }
        
        // Get parameters
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
        
        // Check for async modifier
        List<TSNode> children = new ArrayList<>();
        for (int i = 0; i < funcDecl.getNamedChildCount(); i++) {
            children.add(funcDecl.getNamedChild(i));
        }
        for (TSNode child : children) {
            if ("async".equals(child.getType())) {
                methodInfo.modifiers.add("async");
            }
        }
        
        // Get function name
        TSNode nameNode = findFirstChild(funcDecl, "identifier");
        if (nameNode != null) {
            methodInfo.name = getNodeText(source, nameNode);
        }
        
        // Standalone functions have no explicit visibility in JavaScript
        methodInfo.visibility = null;
        
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
    
    private void analyzeParameters(String source, TSNode paramsNode, MethodInfo methodInfo) {
        int count = paramsNode.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            TSNode param = paramsNode.getNamedChild(i);
            String paramName = null;
            
            if ("identifier".equals(param.getType())) {
                paramName = getNodeText(source, param);
            } else if ("assignment_pattern".equals(param.getType())) {
                // Default parameter: param = value
                TSNode nameNode = findFirstChild(param, "identifier");
                if (nameNode != null) {
                    paramName = getNodeText(source, nameNode);
                }
            } else if ("rest_pattern".equals(param.getType())) {
                // Rest parameter: ...args
                TSNode nameNode = findFirstChild(param, "identifier");
                if (nameNode != null) {
                    paramName = "..." + getNodeText(source, nameNode);
                }
            }
            
            // Validate parameter name
            if (paramName != null && paramName.matches("(\\.\\.\\.)?[a-zA-Z_$][a-zA-Z0-9_$]*")) {
                methodInfo.parameters.add(new Parameter(paramName, null));
            }
        }
    }
    
    private void analyzeMethodBody(String source, TSNode bodyNode, MethodInfo methodInfo, String className) {
        // Build a map of variable names to their inferred types
        Map<String, String> localTypes = new HashMap<>();
        
        // Find variable declarations (const, let, var)
        List<TSNode> varDecls = findAllDescendants(bodyNode, "variable_declarator");
        for (TSNode varDecl : varDecls) {
            TSNode varName = findFirstChild(varDecl, "identifier");
            if (varName != null) {
                String name = getNodeText(source, varName);
                // Validate variable name
                if (name != null && name.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
                    methodInfo.localVariables.add(name);
                    
                    // Try to infer type from initializer
                    TSNode initializer = varDecl.getNamedChild(1);
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
            TSNode functionNode = callExpr.getNamedChild(0);
            if (functionNode != null) {
                String methodName = null;
                String objectName = null;
                String objectType = null;
                
                if ("member_expression".equals(functionNode.getType())) {
                    // obj.method() or obj.prop.method()
                    TSNode objNode = findFirstChild(functionNode, "identifier");
                    TSNode propNode = findFirstChild(functionNode, "property_identifier");
                    
                    if (propNode != null) {
                        methodName = getNodeText(source, propNode);
                    }
                    
                    if (objNode != null) {
                        objectName = getNodeText(source, objNode);
                        objectType = localTypes.get(objectName);
                        
                        // Check if this is 'this'
                        if ("this".equals(objectName) && className != null) {
                            objectType = className;
                        }
                    } else {
                        // Handle chained calls: a.b.method() - get the base object
                        TSNode baseExpr = functionNode.getNamedChild(0);
                        if (baseExpr != null && "identifier".equals(baseExpr.getType())) {
                            objectName = getNodeText(source, baseExpr);
                            objectType = localTypes.get(objectName);
                        }
                    }
                } else if ("identifier".equals(functionNode.getType())) {
                    // Direct function call
                    methodName = getNodeText(source, functionNode);
                }
                
                // Validate method name
                if (methodName != null && methodName.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
                    // Check if we already have this call, if so increment count
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
    
    private List<FieldInfo> collectFieldsFromBody(String source, TSNode classBody) {
        List<FieldInfo> fields = new ArrayList<>();
        if (classBody == null) return fields;
        
        // Only direct field_definition children of this class body
        List<TSNode> fieldDecls = findAllChildren(classBody, "field_definition");
        
        for (TSNode field : fieldDecls) {
            FieldInfo fieldInfo = new FieldInfo();
            
            // Check for static modifier
            for (int i = 0; i < field.getNamedChildCount(); i++) {
                TSNode child = field.getNamedChild(i);
                if ("static".equals(child.getType())) {
                    fieldInfo.modifiers.add("static");
                }
            }
            
            // Get field name
            TSNode nameNode = findFirstChild(field, "property_identifier");
            if (nameNode != null) {
                fieldInfo.name = getNodeText(source, nameNode);
            }
            
            // Check for private field (# prefix)
            if (fieldInfo.name != null && fieldInfo.name.startsWith("#")) {
                fieldInfo.visibility = "private";
            }
            
            // Try to infer type from initializer
            TSNode initializer = field.getNamedChild(field.getNamedChildCount() - 1);
            if (initializer != null && !"property_identifier".equals(initializer.getType())) {
                fieldInfo.type = inferTypeFromExpression(source, initializer);
            }
            
            if (fieldInfo.name != null) {
                fields.add(fieldInfo);
            }
        }
        
        return fields;
    }
    
    private String inferTypeFromExpression(String source, TSNode expr) {
        if (expr == null) return null;
        
        String exprType = expr.getType();
        
        // Handle new expressions: new ClassName()
        if ("new_expression".equals(exprType)) {
            TSNode typeNode = expr.getNamedChild(0);
            if (typeNode != null && "identifier".equals(typeNode.getType())) {
                return getNodeText(source, typeNode);
            }
        }
        
        // Handle array literals: [...]
        if ("array".equals(exprType)) {
            return "Array";
        }
        
        // Handle object literals: {...}
        if ("object".equals(exprType)) {
            return "Object";
        }
        
        // Handle arrow functions and function expressions
        if ("arrow_function".equals(exprType) || "function".equals(exprType) || "function_expression".equals(exprType)) {
            return "Function";
        }
        
        // Handle call expressions - return the function name as a hint
        if ("call_expression".equals(exprType)) {
            TSNode callee = expr.getNamedChild(0);
            if (callee != null && "identifier".equals(callee.getType())) {
                String funcName = getNodeText(source, callee);
                return funcName + "Result";
            }
        }
        
        return null;
    }
}
