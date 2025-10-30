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

public class PHPAnalyzer implements LanguageAnalyzer {
    
    @Override
    public FileAnalysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        FileAnalysis analysis = new FileAnalysis();
        analysis.filePath = filePath;
        analysis.language = "php";
        
        try {
            if (rootNode == null || rootNode.isNull()) {
                System.err.println("Warning: Root node is null for PHP file: " + filePath);
                return analysis;
            }
            
            // Collect namespace and use statements (imports)
            extractImports(sourceCode, rootNode, analysis);
            
            // Find all class declarations and identify nested ones
            List<TSNode> allClasses = findAllDescendants(rootNode, "class_declaration");
            Set<Integer> nestedClassIds = identifyNestedClasses(allClasses);
            
            // Process only top-level classes recursively
            for (TSNode classDecl : allClasses) {
                try {
                    if (classDecl == null || classDecl.isNull()) continue;
                    if (!nestedClassIds.contains(classDecl.getStartByte())) {
                        analyzeClassRecursively(sourceCode, classDecl, analysis);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to analyze class in " + filePath + ": " + e.getMessage());
                }
            }
            
            // Find all interface declarations
            List<TSNode> interfaceDecls = findAllDescendants(rootNode, "interface_declaration");
            for (TSNode interfaceDecl : interfaceDecls) {
                try {
                    if (interfaceDecl == null || interfaceDecl.isNull()) continue;
                    
                    TypeInfo typeInfo = analyzeInterface(sourceCode, interfaceDecl);
                    if (typeInfo.name != null && !typeInfo.name.isEmpty()) {
                        analysis.types.add(typeInfo);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to analyze interface in " + filePath + ": " + e.getMessage());
                }
            }
            
            // Find standalone functions
            List<TSNode> functionDecls = findAllDescendants(rootNode, "function_definition");
            for (TSNode funcDecl : functionDecls) {
                try {
                    if (funcDecl == null || funcDecl.isNull()) continue;
                    if (!isInsideClass(funcDecl)) {
                        MethodInfo methodInfo = analyzeFunction(sourceCode, funcDecl);
                        if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                            analysis.methods.add(methodInfo);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to analyze function in " + filePath + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error analyzing PHP file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return analysis;
    }
    
    private void extractImports(String source, TSNode rootNode, FileAnalysis analysis) {
        try {
            // Extract namespace declaration
            List<TSNode> namespaceDecls = findAllDescendants(rootNode, "namespace_definition");
            for (TSNode ns : namespaceDecls) {
                if (ns == null || ns.isNull()) continue;
                String text = getNodeText(source, ns);
                if (text != null && !text.trim().isEmpty()) {
                    analysis.imports.add(text.trim());
                }
            }
            
            // Extract use statements
            List<TSNode> useDecls = findAllDescendants(rootNode, "namespace_use_declaration");
            for (TSNode use : useDecls) {
                if (use == null || use.isNull()) continue;
                String text = getNodeText(source, use);
                if (text != null && !text.trim().isEmpty()) {
                    analysis.imports.add(text.trim());
                }
            }
        } catch (Exception e) {
            // Silently skip import extraction if it fails
            System.err.println("Warning: Failed to extract imports from PHP file: " + e.getMessage());
        }
    }
    
    private boolean isInsideClass(TSNode node) {
        if (node == null || node.isNull()) return false;
        
        try {
            TSNode parent = node.getParent();
            while (parent != null && !parent.isNull()) {
                String type = parent.getType();
                if (type != null && "class_declaration".equals(type)) {
                    return true;
                }
                parent = parent.getParent();
            }
        } catch (Exception e) {
            // If we can't determine, assume it's not inside a class
            return false;
        }
        return false;
    }
    
    private Set<Integer> identifyNestedClasses(List<TSNode> allClasses) {
        Set<Integer> nestedClassIds = new HashSet<>();
        for (TSNode classDecl : allClasses) {
            try {
                if (classDecl == null || classDecl.isNull()) continue;
                TSNode classBody = findFirstChild(classDecl, "declaration_list");
                if (classBody != null) {
                    List<TSNode> nested = findAllDescendants(classBody, "class_declaration");
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
        TypeInfo typeInfo = analyzeClass(source, classDecl);
        if (typeInfo.name == null || typeInfo.name.isEmpty()) {
            return;
        }
        targetList.add(typeInfo);
        
        TSNode classBody = findFirstChild(classDecl, "declaration_list");
        if (classBody == null) {
            return;
        }
        
        // Collect fields for this class only
        List<FieldInfo> fields = collectFieldsFromBody(source, classBody);
        typeInfo.fields.addAll(fields);
        
        // Analyze methods within this class only
        List<TSNode> methods = findAllChildren(classBody, "method_declaration");
        for (TSNode method : methods) {
            try {
                if (method == null || method.isNull()) continue;
                MethodInfo methodInfo = analyzeMethod(source, method, typeInfo.name);
                if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                    typeInfo.methods.add(methodInfo);
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to analyze method: " + e.getMessage());
            }
        }
        
        // Recursively process nested classes
        List<TSNode> nestedClasses = findAllChildren(classBody, "class_declaration");
        for (TSNode nested : nestedClasses) {
            try {
                if (nested != null && !nested.isNull()) {
                    analyzeClassRecursivelyInto(source, nested, typeInfo.types);
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to analyze nested class: " + e.getMessage());
            }
        }
    }
    
    private TypeInfo analyzeClass(String source, TSNode classDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "class";
        
        // Extract modifiers (abstract, final)
        extractModifiersAndVisibility(source, classDecl, typeInfo);
        
        // Get class name
        TSNode nameNode = findFirstChild(classDecl, "name");
        if (nameNode != null && !nameNode.isNull()) {
            typeInfo.name = getNodeText(source, nameNode);
        }
        
        // Get base class
        TSNode baseClause = findFirstChild(classDecl, "base_clause");
        if (baseClause != null && !baseClause.isNull()) {
            TSNode baseName = findFirstChild(baseClause, "name");
            if (baseName != null && !baseName.isNull()) {
                typeInfo.extendsType = getNodeText(source, baseName);
            }
        }
        
        // Get implemented interfaces
        TSNode interfaceClause = findFirstChild(classDecl, "class_interface_clause");
        if (interfaceClause != null && !interfaceClause.isNull()) {
            List<TSNode> interfaceNames = findAllDescendants(interfaceClause, "name");
            for (TSNode interfaceName : interfaceNames) {
                if (interfaceName != null && !interfaceName.isNull()) {
                    typeInfo.implementsInterfaces.add(getNodeText(source, interfaceName));
                }
            }
        }
        
        return typeInfo;
    }
    
    private TypeInfo analyzeInterface(String source, TSNode interfaceDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "interface";
        
        // Extract modifiers and visibility (defaults to public if not specified)
        extractModifiersAndVisibility(source, interfaceDecl, typeInfo);

        TSNode nameNode = findFirstChild(interfaceDecl, "name");
        if (nameNode != null && !nameNode.isNull()) {
            typeInfo.name = getNodeText(source, nameNode);
        }
        
        // Interfaces can extend other interfaces
        TSNode baseClause = findFirstChild(interfaceDecl, "base_clause");
        if (baseClause != null && !baseClause.isNull()) {
            List<TSNode> baseNames = findAllDescendants(baseClause, "name");
            for (TSNode baseName : baseNames) {
                if (baseName != null && !baseName.isNull()) {
                    typeInfo.implementsInterfaces.add(getNodeText(source, baseName));
                }
            }
        }
        
        // Collect interface methods (implicitly public and abstract)
        List<TSNode> ifaceMethods = findAllDescendants(interfaceDecl, "method_declaration");
        for (TSNode m : ifaceMethods) {
            if (m == null || m.isNull()) continue;
            MethodInfo mi = new MethodInfo();
            
            // Name
            TSNode mName = findFirstChild(m, "name");
            if (mName != null && !mName.isNull()) {
                mi.name = getNodeText(source, mName);
            }
            
            // Return type (optional_type, named_type, primitive_type, union_type)
            TSNode rt = findFirstChild(m, "optional_type");
            if (rt == null || rt.isNull()) rt = findFirstChild(m, "named_type");
            if (rt == null || rt.isNull()) rt = findFirstChild(m, "primitive_type");
            if (rt == null || rt.isNull()) rt = findFirstChild(m, "union_type");
            if (rt != null && !rt.isNull()) {
                String t = getNodeText(source, rt);
                if (t != null) mi.returnType = t.replaceFirst("^:\\s*", "").trim();
            }
            
            // Parameters
            TSNode fp = findFirstChild(m, "formal_parameters");
            if (fp != null && !fp.isNull()) {
                analyzeParameters(source, fp, mi);
            }
            
            // Visibility and modifiers as per PHP interface method defaults
            mi.visibility = "public";
            mi.modifiers.add("abstract");
            
            // No body analysis for interface methods
            if (mi.name != null && !mi.name.isEmpty()) {
                typeInfo.methods.add(mi);
            }
        }
        
        return typeInfo;
    }
    
    private MethodInfo analyzeMethod(String source, TSNode methodDecl, String className) {
        MethodInfo methodInfo = new MethodInfo();
        
        // Extract modifiers and visibility (public, private, protected, static, final, abstract)
        extractModifiersAndVisibility(source, methodDecl, methodInfo);
        
        // Get method name
        TSNode nameNode = findFirstChild(methodDecl, "name");
        if (nameNode != null && !nameNode.isNull()) {
            methodInfo.name = getNodeText(source, nameNode);
        }
        
        // Get return type if specified. In tree-sitter PHP, the "return_type" is a field,
        // and the child node type is one of: optional_type, named_type, primitive_type, union_type.
        TSNode returnTypeNode = findFirstChild(methodDecl, "optional_type");
        if (returnTypeNode == null || returnTypeNode.isNull()) {
            returnTypeNode = findFirstChild(methodDecl, "named_type");
        }
        if (returnTypeNode == null || returnTypeNode.isNull()) {
            returnTypeNode = findFirstChild(methodDecl, "primitive_type");
        }
        if (returnTypeNode == null || returnTypeNode.isNull()) {
            returnTypeNode = findFirstChild(methodDecl, "union_type");
        }
        if (returnTypeNode != null && !returnTypeNode.isNull()) {
            String typeText = getNodeText(source, returnTypeNode);
            if (typeText != null) {
                // Strip an eventual leading colon if present, keep nullable marker if any
                methodInfo.returnType = typeText.replaceFirst("^:\\s*", "").trim();
            }
        }
        
        // Get parameters
        TSNode paramsNode = findFirstChild(methodDecl, "formal_parameters");
        if (paramsNode != null) {
            analyzeParameters(source, paramsNode, methodInfo);
        }
        
        // Get method body
        TSNode bodyNode = findFirstChild(methodDecl, "compound_statement");
        if (bodyNode != null) {
            analyzeMethodBody(source, bodyNode, methodInfo);
        }
        
        return methodInfo;
    }
    
    private MethodInfo analyzeFunction(String source, TSNode funcDef) {
        MethodInfo methodInfo = new MethodInfo();
        
        // Get function name
        TSNode nameNode = findFirstChild(funcDef, "name");
        if (nameNode != null && !nameNode.isNull()) {
            methodInfo.name = getNodeText(source, nameNode);
        }
        
        // Get return type if specified (same logic as for methods)
        TSNode returnTypeNode = findFirstChild(funcDef, "optional_type");
        if (returnTypeNode == null || returnTypeNode.isNull()) {
            returnTypeNode = findFirstChild(funcDef, "named_type");
        }
        if (returnTypeNode == null || returnTypeNode.isNull()) {
            returnTypeNode = findFirstChild(funcDef, "primitive_type");
        }
        if (returnTypeNode == null || returnTypeNode.isNull()) {
            returnTypeNode = findFirstChild(funcDef, "union_type");
        }
        if (returnTypeNode != null && !returnTypeNode.isNull()) {
            String typeText = getNodeText(source, returnTypeNode);
            if (typeText != null) {
                methodInfo.returnType = typeText.replaceFirst("^:\\s*", "").trim();
            }
        }
        
        // Get parameters
        TSNode paramsNode = findFirstChild(funcDef, "formal_parameters");
        if (paramsNode != null) {
            analyzeParameters(source, paramsNode, methodInfo);
        }
        
        // Get function body
        TSNode bodyNode = findFirstChild(funcDef, "compound_statement");
        if (bodyNode != null) {
            analyzeMethodBody(source, bodyNode, methodInfo);
        }
        
        return methodInfo;
    }
    
    private void analyzeParameters(String source, TSNode paramsNode, MethodInfo methodInfo) {
        // Iterate children by index to preserve source order across kinds
        int count = paramsNode.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = paramsNode.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            String kind = child.getType();

            if ("simple_parameter".equals(kind) || "property_promotion_parameter".equals(kind)) {
                TSNode varName = findFirstChild(child, "variable_name");
                String paramType = null;

                // Get type hint if present (can be named_type, primitive_type, optional_type, union_type)
                TSNode typeNode = findFirstChild(child, "named_type");
                if (typeNode == null || typeNode.isNull()) typeNode = findFirstChild(child, "primitive_type");
                if (typeNode == null || typeNode.isNull()) typeNode = findFirstChild(child, "optional_type");
                if (typeNode == null || typeNode.isNull()) typeNode = findFirstChild(child, "union_type");
                if (typeNode != null && !typeNode.isNull()) {
                    paramType = getNodeText(source, typeNode);
                }

                if (varName != null && !varName.isNull()) {
                    String name = getNodeText(source, varName);
                    // Remove $ prefix from PHP variables
                    if (name != null && name.startsWith("$")) {
                        name = name.substring(1);
                    }
                    // Validate parameter name
                    if (name != null && name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                        methodInfo.parameters.add(new Parameter(name, paramType));
                    }
                }
            }
        }
    }
    
    private void analyzeMethodBody(String source, TSNode bodyNode, MethodInfo methodInfo) {
        // Build a map of variable names to their types
        Map<String, String> localTypes = new HashMap<>();
        
        // Find variable assignments (PHP doesn't have explicit declarations)
        List<TSNode> assignments = findAllDescendants(bodyNode, "assignment_expression");
        for (TSNode assignment : assignments) {
            if (assignment == null || assignment.isNull()) continue;
            TSNode leftSide = assignment.getNamedChild(0);
            if (leftSide != null && !leftSide.isNull() && "variable_name".equals(leftSide.getType())) {
                String varName = getNodeText(source, leftSide);
                // Remove $ prefix and avoid $this
                if (varName != null && varName.startsWith("$") && !"$this".equals(varName)) {
                    varName = varName.substring(1);
                    // Validate variable name
                    if (varName.matches("[a-zA-Z_][a-zA-Z0-9_]*") && !methodInfo.localVariables.contains(varName)) {
                        methodInfo.localVariables.add(varName);
                    }
                    // Minimal type inference: $var = new ClassName(...);
                    TSNode rightSide = assignment.getNamedChild(1);
                    if (rightSide != null && !rightSide.isNull() && "object_creation_expression".equals(rightSide.getType())) {
                        // object_creation_expression -> class_type_designator -> (qualified_name | name)
                        TSNode classType = findFirstChild(rightSide, "class_type_designator");
                        if (classType != null && !classType.isNull()) {
                            TSNode typeName = findFirstChild(classType, "qualified_name");
                            if (typeName == null || typeName.isNull()) {
                                typeName = findFirstChild(classType, "name");
                            }
                            if (typeName != null && !typeName.isNull()) {
                                String inferredType = getNodeText(source, typeName);
                                if (inferredType != null && !inferredType.isBlank()) {
                                    localTypes.put(varName, inferredType);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Find function calls (PHP uses multiple node types)
        List<TSNode> functionCalls = findAllDescendants(bodyNode, "function_call_expression");
        List<TSNode> memberCalls = findAllDescendants(bodyNode, "member_call_expression");
        
        // Process regular function calls
        for (TSNode callExpr : functionCalls) {
            if (callExpr == null || callExpr.isNull()) continue;
            TSNode functionNode = callExpr.getNamedChild(0);
            if (functionNode != null && !functionNode.isNull()) {
                String methodName = null;
                
                if ("name".equals(functionNode.getType())) {
                    // Direct function call
                    methodName = getNodeText(source, functionNode);
                }
                
                // Validate method name
                if (methodName != null && methodName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                    // Check if we already have this call, if so increment count
                    boolean found = false;
                    for (MethodCall existingCall : methodInfo.methodCalls) {
                        if (existingCall.matches(methodName, null, null, null)) {
                            existingCall.callCount++;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        methodInfo.methodCalls.add(new MethodCall(methodName, null, null));
                    }
                }
            }
        }
        
        // Process member calls ($obj->method())
        for (TSNode memberCall : memberCalls) {
            if (memberCall == null || memberCall.isNull()) continue;
            
            String methodName = null;
            String objectName = null;
            String objectType = null;
            
            // Get the object (left side of ->)
            TSNode objNode = memberCall.getNamedChild(0);
            // Method name is the second named child (index 1) of member_call_expression
            TSNode memberNode = memberCall.getNamedChild(1);
            
            if (objNode != null && !objNode.isNull()) {
                if ("variable_name".equals(objNode.getType())) {
                    objectName = getNodeText(source, objNode);
                    if (objectName != null && objectName.startsWith("$")) {
                        objectName = objectName.substring(1);
                        // We avoid setting objectType blindly to the enclosing class for $this.
                        // If we can infer localTypes for variables, use it; otherwise leave null.
                        if (!"this".equals(objectName)) {
                            objectType = localTypes.get(objectName);
                        }
                    }
                } else if ("member_access_expression".equals(objNode.getType())) {
                    // Example: $this->cache->get() => objectName should be the property name 'cache'.
                    // member_access_expression children: base (0), name (1)
                    TSNode propNameNode = objNode.getNamedChild(1);
                    if (propNameNode != null && !propNameNode.isNull() && "name".equals(propNameNode.getType())) {
                        objectName = getNodeText(source, propNameNode);
                    }
                    // Do not assign objectType to enclosing class for $this; leave null unless inferable.
                }
            }
            if (memberNode != null && !memberNode.isNull()) {
                methodName = getNodeText(source, memberNode);
            }
            
            // Validate method name
            if (methodName != null && methodName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                // Check if we already have this call, if so increment count
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
        
        // Also check for scoped calls (Class::method())
        List<TSNode> scopedCalls = findAllDescendants(bodyNode, "scoped_call_expression");
        for (TSNode scopedCall : scopedCalls) {
            if (scopedCall == null || scopedCall.isNull()) continue;
            // Positional: scope at index 0, method name at index 1
            TSNode scopeNode = scopedCall.getNamedChild(0);
            TSNode methodNode = scopedCall.getNamedChild(1);
            String objectType = null;
            String methodName = null;
            if (scopeNode != null && !scopeNode.isNull()) {
                objectType = getNodeText(source, scopeNode);
            }
            if (methodNode != null && !methodNode.isNull()) {
                methodName = getNodeText(source, methodNode);
            }
            
            // Validate method name
            if (methodName != null && methodName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                // Check if we already have this call, if so increment count
                boolean found = false;
                for (MethodCall existingCall : methodInfo.methodCalls) {
                    if (existingCall.matches(methodName, objectType, null, null)) {
                        existingCall.callCount++;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    methodInfo.methodCalls.add(new MethodCall(methodName, objectType, null));
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
        
        // Only direct property_declaration children of this class body
        List<TSNode> propertyDecls = findAllChildren(classBody, "property_declaration");
        for (TSNode propDecl : propertyDecls) {
            if (propDecl == null || propDecl.isNull()) continue;
            
            // Get visibility modifier
            TSNode visibilityNode = findFirstChild(propDecl, "visibility_modifier");
            String visibility = null;
            if (visibilityNode != null && !visibilityNode.isNull()) {
                visibility = getNodeText(source, visibilityNode);
            }
            
            // Get static modifier
            TSNode staticNode = findFirstChild(propDecl, "static_modifier");
            boolean isStatic = staticNode != null && !staticNode.isNull();
            
            // Get type (can be named_type or primitive_type)
            String typeText = null;
            TSNode typeNode = findFirstChild(propDecl, "named_type");
            if (typeNode == null || typeNode.isNull()) {
                typeNode = findFirstChild(propDecl, "primitive_type");
            }
            if (typeNode != null && !typeNode.isNull()) {
                typeText = getNodeText(source, typeNode);
            }
            
            // Each property_declaration can have multiple property_element children
            List<TSNode> propertyElements = findAllChildren(propDecl, "property_element");
            for (TSNode propElement : propertyElements) {
                if (propElement == null || propElement.isNull()) continue;
                
                FieldInfo fieldInfo = new FieldInfo();
                fieldInfo.visibility = visibility;
                fieldInfo.type = typeText;
                
                // Add modifiers
                if (visibility != null) {
                    fieldInfo.modifiers.add(visibility);
                }
                if (isStatic) {
                    fieldInfo.modifiers.add("static");
                }
                
                // Get property name (PHP properties start with $)
                TSNode varNode = findFirstChild(propElement, "variable_name");
                if (varNode != null && !varNode.isNull()) {
                    String name = getNodeText(source, varNode);
                    if (name != null && name.startsWith("$")) {
                        name = name.substring(1);
                    }
                    fieldInfo.name = name;
                }
                
                if (fieldInfo.name != null && fieldInfo.name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                    fields.add(fieldInfo);
                }
            }
        }
        
        return fields;
    }
    
    private void extractModifiersAndVisibility(String source, TSNode node, Object target) {
        // PHP modifiers: public, private, protected, static, final, abstract
        int childCount = node.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            String type = child.getType();
            
            if ("visibility_modifier".equals(type) || "static_modifier".equals(type) || 
                "final_modifier".equals(type) || "abstract_modifier".equals(type)) {
                String modText = getNodeText(source, child);
                
                if (target instanceof TypeInfo) {
                    TypeInfo typeInfo = (TypeInfo) target;
                    typeInfo.modifiers.add(modText);
                    // Set visibility for types
                    if ("public".equals(modText) || "private".equals(modText) || "protected".equals(modText)) {
                        typeInfo.visibility = modText;
                    }
                } else if (target instanceof MethodInfo) {
                    MethodInfo methodInfo = (MethodInfo) target;
                    methodInfo.modifiers.add(modText);
                    // Set visibility for methods
                    if ("public".equals(modText) || "private".equals(modText) || "protected".equals(modText)) {
                        methodInfo.visibility = modText;
                    }
                } else if (target instanceof FieldInfo) {
                    FieldInfo fieldInfo = (FieldInfo) target;
                    fieldInfo.modifiers.add(modText);
                    // Set visibility for fields
                    if ("public".equals(modText) || "private".equals(modText) || "protected".equals(modText)) {
                        fieldInfo.visibility = modText;
                    }
                }
            }
        }
        
        // PHP default visibility is public for classes, public for methods/properties if not specified
        if (target instanceof TypeInfo && ((TypeInfo) target).visibility == null) {
            ((TypeInfo) target).visibility = "public";
        } else if (target instanceof MethodInfo && ((MethodInfo) target).visibility == null) {
            ((MethodInfo) target).visibility = "public";
        } else if (target instanceof FieldInfo && ((FieldInfo) target).visibility == null) {
            ((FieldInfo) target).visibility = "public";
        }
    }
}
