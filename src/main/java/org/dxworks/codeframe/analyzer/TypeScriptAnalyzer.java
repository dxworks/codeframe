package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.*;
import org.treesitter.TSNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.dxworks.codeframe.analyzer.TreeSitterHelper.*;

public class TypeScriptAnalyzer implements LanguageAnalyzer {
    
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
        
        // Find all class declarations
        List<TSNode> classDecls = findAllDescendants(rootNode, "class_declaration");
        for (TSNode classDecl : classDecls) {
            TypeInfo typeInfo = analyzeClass(sourceCode, classDecl);
            analysis.types.add(typeInfo);
            
            // Collect fields for this class
            List<FieldInfo> fields = collectFields(sourceCode, classDecl);
            typeInfo.fields.addAll(fields);  // Add to type, not to file-level fields
            
            // Analyze methods within this class
            List<TSNode> methods = findAllDescendants(classDecl, "method_definition");
            for (TSNode method : methods) {
                MethodInfo methodInfo = analyzeMethod(sourceCode, method, typeInfo.name);
                typeInfo.methods.add(methodInfo);  // Add to type, not to file-level methods
            }
        }
        
        // Find all interface declarations
        List<TSNode> interfaceDecls = findAllDescendants(rootNode, "interface_declaration");
        for (TSNode interfaceDecl : interfaceDecls) {
            TypeInfo typeInfo = analyzeInterface(sourceCode, interfaceDecl);
            analysis.types.add(typeInfo);
        }
        
        // Find standalone functions
        List<TSNode> functionDecls = findAllDescendants(rootNode, "function_declaration");
        for (TSNode funcDecl : functionDecls) {
            MethodInfo methodInfo = analyzeFunction(sourceCode, funcDecl);
            analysis.methods.add(methodInfo);
        }
        
        return analysis;
    }
    
    private TypeInfo analyzeClass(String source, TSNode classDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "class";
        
        // Extract modifiers and visibility (export, abstract, etc.)
        extractModifiersAndVisibility(source, classDecl, typeInfo.modifiers, typeInfo);
        
        // Extract decorators (TypeScript's version of annotations)
        extractDecorators(source, classDecl, typeInfo.annotations);
        
        // Get class name
        TSNode nameNode = findFirstChild(classDecl, "type_identifier");
        if (nameNode != null) {
            typeInfo.name = getNodeText(source, nameNode);
        }
        
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
        
        TSNode nameNode = findFirstChild(interfaceDecl, "type_identifier");
        if (nameNode != null) {
            typeInfo.name = getNodeText(source, nameNode);
        }
        
        // Interfaces can extend other interfaces
        TSNode extendsNode = findFirstChild(interfaceDecl, "extends_clause");
        if (extendsNode != null) {
            List<TSNode> typeIds = findAllDescendants(extendsNode, "type_identifier");
            for (TSNode typeId : typeIds) {
                typeInfo.implementsInterfaces.add(getNodeText(source, typeId));
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
        TSNode nameNode = findFirstChild(methodDef, "property_identifier");
        if (nameNode != null) {
            methodInfo.name = getNodeText(source, nameNode);
        }
        
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
        
        // Get function name
        TSNode nameNode = findFirstChild(funcDecl, "identifier");
        if (nameNode != null) {
            methodInfo.name = getNodeText(source, nameNode);
        }
        
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
    
    private void analyzeParameters(String source, TSNode paramsNode, MethodInfo methodInfo) {
        List<TSNode> params = findAllChildren(paramsNode, "required_parameter");
        for (TSNode param : params) {
            TSNode paramName = findFirstChild(param, "identifier");
            String name = paramName != null ? getNodeText(source, paramName) : null;
            
            // Get type annotation
            TSNode typeAnnotation = findFirstChild(param, "type_annotation");
            String type = null;
            if (typeAnnotation != null) {
                type = getNodeText(source, typeAnnotation).replaceFirst("^:\\s*", "");
            }
            
            if (name != null) {
                methodInfo.parameters.add(new Parameter(name, type));
            }
        }
        
        // Also check for optional parameters
        List<TSNode> optionalParams = findAllChildren(paramsNode, "optional_parameter");
        for (TSNode param : optionalParams) {
            TSNode paramName = findFirstChild(param, "identifier");
            String name = paramName != null ? getNodeText(source, paramName) : null;
            
            // Get type annotation
            TSNode typeAnnotation = findFirstChild(param, "type_annotation");
            String type = null;
            if (typeAnnotation != null) {
                type = getNodeText(source, typeAnnotation).replaceFirst("^:\\s*", "");
            }
            
            if (name != null) {
                methodInfo.parameters.add(new Parameter(name, type));
            }
        }
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
                    TSNode objNode = findFirstChild(functionNode, "identifier");
                    TSNode propNode = findFirstChild(functionNode, "property_identifier");
                    
                    if (propNode != null) {
                        callText = getNodeText(source, propNode);
                    }
                    
                    if (objNode != null) {
                        objectName = getNodeText(source, objNode);
                        
                        // Look up type from local variables
                        objectType = localTypes.get(objectName);
                        
                        // Check if this is 'this'
                        if ("this".equals(objectName) && className != null) {
                            objectType = className;
                        }
                    } else {
                        // Handle chained calls: a.b.method() - get the base object
                        TSNode baseExpr = functionNode.getNamedChild(0);
                        if (baseExpr != null) {
                            if ("identifier".equals(baseExpr.getType())) {
                                objectName = getNodeText(source, baseExpr);
                                objectType = localTypes.get(objectName);
                            } else if ("member_expression".equals(baseExpr.getType())) {
                                // Get the leftmost identifier in the chain
                                TSNode leftmost = getLeftmostIdentifier(baseExpr);
                                if (leftmost != null) {
                                    objectName = getNodeText(source, leftmost);
                                    objectType = localTypes.get(objectName);
                                }
                            }
                        }
                    }
                } else if ("identifier".equals(functionNode.getType())) {
                    // Direct function call
                    callText = getNodeText(source, functionNode);
                }
                
                if (callText != null) {
                    // Check if we already have this call, if so increment count
                    boolean found = false;
                    for (MethodCall existingCall : methodInfo.methodCalls) {
                        if (existingCall.matches(callText, objectType, objectName)) {
                            existingCall.callCount++;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        methodInfo.methodCalls.add(new MethodCall(callText, objectType, objectName));
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
    
    private List<FieldInfo> collectFields(String source, TSNode classDecl) {
        List<FieldInfo> fields = new ArrayList<>();
        List<TSNode> fieldDecls = findAllDescendants(classDecl, "public_field_definition");
        fieldDecls.addAll(findAllDescendants(classDecl, "field_definition"));
        
        for (TSNode field : fieldDecls) {
            FieldInfo fieldInfo = new FieldInfo();
            
            // Extract modifiers and visibility
            extractModifiersAndVisibility(source, field, fieldInfo.modifiers, fieldInfo);
            
            // Extract decorators
            extractDecorators(source, field, fieldInfo.annotations);
            
            // Get field name
            TSNode nameNode = findFirstChild(field, "property_identifier");
            if (nameNode != null) {
                fieldInfo.name = getNodeText(source, nameNode);
            }
            
            // Get type annotation
            TSNode typeAnnotation = findFirstChild(field, "type_annotation");
            if (typeAnnotation != null) {
                fieldInfo.type = getNodeText(source, typeAnnotation).replaceFirst("^:\\s*", "");
            }
            
            fields.add(fieldInfo);
        }
        
        return fields;
    }
    
    private void extractModifiersAndVisibility(String source, TSNode node, List<String> modifiers, Object target) {
        // TypeScript modifiers: public, private, protected, static, readonly, abstract, async, export
        int childCount = node.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getNamedChild(i);
            String type = child.getType();
            
            if ("accessibility_modifier".equals(type) || "readonly".equals(type) || 
                "static".equals(type) || "abstract".equals(type) || "async".equals(type) ||
                "export_statement".equals(type)) {
                String modText = getNodeText(source, child);
                modifiers.add(modText);
                
                // Set visibility
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
        }
        
        // Check parent for export
        TSNode parent = node.getParent();
        if (parent != null && "export_statement".equals(parent.getType())) {
            modifiers.add("export");
        }
        
        // Default visibility is public if not specified
        if (target instanceof TypeInfo && ((TypeInfo) target).visibility == null) {
            ((TypeInfo) target).visibility = "public";
        } else if (target instanceof MethodInfo && ((MethodInfo) target).visibility == null) {
            ((MethodInfo) target).visibility = "public";
        } else if (target instanceof FieldInfo && ((FieldInfo) target).visibility == null) {
            ((FieldInfo) target).visibility = "public";
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
        if (expr == null) return null;
        
        String exprType = expr.getType();
        
        // Handle 'as' type assertions: (expr as Type)
        if ("as_expression".equals(exprType)) {
            TSNode typeNode = findFirstDescendant(expr, "type_identifier");
            if (typeNode != null) {
                return getNodeText(source, typeNode);
            }
        }
        
        // Handle new expressions: new ClassName()
        if ("new_expression".equals(exprType)) {
            TSNode typeNode = expr.getNamedChild(0);
            if (typeNode != null && "identifier".equals(typeNode.getType())) {
                return getNodeText(source, typeNode);
            }
            // Handle new expressions with type_identifier
            typeNode = findFirstChild(expr, "type_identifier");
            if (typeNode != null) {
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
        
        // Handle call expressions - try to get the function name
        if ("call_expression".equals(exprType)) {
            TSNode callee = expr.getNamedChild(0);
            if (callee != null && "identifier".equals(callee.getType())) {
                String funcName = getNodeText(source, callee);
                // Common React hooks and their return types
                if ("useState".equals(funcName)) return "State";
                if ("useRef".equals(funcName)) return "Ref";
                if ("useMemo".equals(funcName)) return "Memoized";
                if ("useCallback".equals(funcName)) return "Callback";
                // Return the function name as a hint
                return funcName + "Result";
            }
        }
        
        return null;
    }
    
    private TSNode getLeftmostIdentifier(TSNode memberExpr) {
        if (memberExpr == null) return null;
        
        // Traverse left in member_expression chain to find the base identifier
        TSNode current = memberExpr;
        while (current != null && "member_expression".equals(current.getType())) {
            TSNode left = current.getNamedChild(0);
            if (left != null && "identifier".equals(left.getType())) {
                return left;
            }
            current = left;
        }
        
        return null;
    }
}
