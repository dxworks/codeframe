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
            
            // Find all trait declarations
            List<TSNode> traitDecls = findAllDescendants(rootNode, "trait_declaration");
            for (TSNode traitDecl : traitDecls) {
                try {
                    if (traitDecl == null || traitDecl.isNull()) continue;
                    
                    TypeInfo typeInfo = analyzeTrait(sourceCode, traitDecl);
                    if (typeInfo.name != null && !typeInfo.name.isEmpty()) {
                        analysis.types.add(typeInfo);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to analyze trait in " + filePath + ": " + e.getMessage());
                }
            }
            
            // Find all enum declarations (PHP 8.1+)
            List<TSNode> enumDecls = findAllDescendants(rootNode, "enum_declaration");
            for (TSNode enumDecl : enumDecls) {
                try {
                    if (enumDecl == null || enumDecl.isNull()) continue;
                    
                    TypeInfo typeInfo = analyzeEnum(sourceCode, enumDecl);
                    if (typeInfo.name != null && !typeInfo.name.isEmpty()) {
                        analysis.types.add(typeInfo);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to analyze enum in " + filePath + ": " + e.getMessage());
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
            
            // Extract file-level constants and variables
            extractFileLevelFields(sourceCode, rootNode, analysis);
            
            // Extract file-level method calls
            extractFileLevelMethodCalls(sourceCode, rootNode, analysis);
        } catch (Exception e) {
            System.err.println("Error analyzing PHP file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return analysis;
    }
    
    /**
     * Extract file-level constants (define) and global variables.
     */
    private void extractFileLevelFields(String source, TSNode rootNode, FileAnalysis analysis) {
        // Find const declarations at file level
        List<TSNode> constDecls = findAllDescendants(rootNode, "const_declaration");
        for (TSNode constDecl : constDecls) {
            if (constDecl == null || constDecl.isNull()) continue;
            if (isInsideClass(constDecl)) continue;
            
            // Extract const elements
            for (TSNode elem : findAllChildren(constDecl, "const_element")) {
                String name = extractName(source, elem);
                if (name == null) continue;
                
                FieldInfo field = new FieldInfo();
                field.name = name;
                field.modifiers.add("const");
                
                // Try to infer type from value
                TSNode valueNode = elem.getNamedChildCount() > 1 ? elem.getNamedChild(1) : null;
                if (valueNode != null) {
                    field.type = inferTypeFromValue(source, valueNode);
                }
                
                if (field.name != null && !field.name.isEmpty()) {
                    analysis.fields.add(field);
                }
            }
        }
        
        // Find define() calls at file level (treated as constants)
        // These are captured in methodCalls, but we could also extract them as fields
    }
    
    /**
     * Extract file-level method calls (outside any class or function).
     * Reuses extractCallsFromNode for consistency with method body analysis.
     */
    private void extractFileLevelMethodCalls(String source, TSNode rootNode, FileAnalysis analysis) {
        int childCount = rootNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            
            // Only process expression_statement at top level
            if ("expression_statement".equals(child.getType())) {
                extractCallsFromNode(source, child, analysis.methodCalls);
            }
        }
        analysis.methodCalls.sort(METHOD_CALL_COMPARATOR);
    }
    
    /**
     * Extract all method calls from a node into a list.
     * Handles function_call_expression, member_call_expression, and scoped_call_expression.
     */
    private void extractCallsFromNode(String source, TSNode node, List<MethodCall> calls) {
        // Function calls: func()
        for (TSNode callExpr : findAllDescendants(node, "function_call_expression")) {
            TSNode funcNode = callExpr.getNamedChild(0);
            if (funcNode != null && "name".equals(funcNode.getType())) {
                String methodName = getNodeText(source, funcNode);
                if (isValidIdentifier(methodName)) {
                    int paramCount = countArguments(callExpr);
                    collectMethodCall(calls, methodName, null, null, paramCount);
                }
            }
        }
        
        // Member calls: $obj->method()
        for (TSNode callExpr : findAllDescendants(node, "member_call_expression")) {
            String[] result = extractMemberCallInfo(source, callExpr);
            if (result[0] != null && isValidIdentifier(result[0])) {
                int paramCount = countArguments(callExpr);
                collectMethodCall(calls, result[0], null, result[1], paramCount);
            }
        }
        
        // Scoped calls: Class::method()
        for (TSNode callExpr : findAllDescendants(node, "scoped_call_expression")) {
            String[] result = extractScopedCallInfo(source, callExpr);
            if (result[0] != null && isValidIdentifier(result[0])) {
                int paramCount = countArguments(callExpr);
                collectMethodCall(calls, result[0], result[1], null, paramCount);
            }
        }
    }
    
    /** Extract [methodName, objectName] from member_call_expression */
    private String[] extractMemberCallInfo(String source, TSNode callExpr) {
        String methodName = null;
        String objectName = null;
        
        TSNode objNode = callExpr.getNamedChild(0);
        TSNode memberNode = callExpr.getNamedChild(1);
        
        if (objNode != null) {
            if ("variable_name".equals(objNode.getType())) {
                objectName = stripPhpVarPrefix(getNodeText(source, objNode));
            } else if ("member_access_expression".equals(objNode.getType())) {
                TSNode propNode = objNode.getNamedChild(1);
                if (propNode != null) objectName = getNodeText(source, propNode);
            }
        }
        if (memberNode != null) {
            methodName = getNodeText(source, memberNode);
        }
        
        return new String[] { methodName, objectName };
    }
    
    /** Extract [methodName, objectType] from scoped_call_expression */
    private String[] extractScopedCallInfo(String source, TSNode callExpr) {
        TSNode scopeNode = callExpr.getNamedChild(0);
        TSNode methodNode = callExpr.getNamedChild(1);
        
        String objectType = scopeNode != null ? getNodeText(source, scopeNode) : null;
        String methodName = methodNode != null ? getNodeText(source, methodNode) : null;
        
        return new String[] { methodName, objectType };
    }
    
    /**
     * Count arguments in a PHP call expression.
     * Looks for "arguments" child node and counts its named children.
     */
    private int countArguments(TSNode callExpr) {
        TSNode argsNode = findFirstChild(callExpr, "arguments");
        if (argsNode == null || argsNode.isNull()) return 0;
        
        int count = 0;
        for (int i = 0; i < argsNode.getNamedChildCount(); i++) {
            TSNode child = argsNode.getNamedChild(i);
            if (child != null && !child.isNull()) {
                // Each argument node counts as one argument
                count++;
            }
        }
        return count;
    }
    
    /**
     * Find the type node from a method/function declaration.
     * PHP types can be: optional_type (?Type), named_type, primitive_type, or union_type.
     */
    private TSNode findTypeNode(TSNode node) {
        TSNode typeNode = findFirstChild(node, "optional_type");
        if (typeNode == null || typeNode.isNull()) typeNode = findFirstChild(node, "named_type");
        if (typeNode == null || typeNode.isNull()) typeNode = findFirstChild(node, "primitive_type");
        if (typeNode == null || typeNode.isNull()) typeNode = findFirstChild(node, "union_type");
        return typeNode;
    }
    
    /**
     * Extract return type string from a type node, stripping leading colon if present.
     */
    private String extractReturnType(String source, TSNode typeNode) {
        if (typeNode == null || typeNode.isNull()) return null;
        String typeText = getNodeText(source, typeNode);
        if (typeText != null) {
            return typeText.replaceFirst("^:\\s*", "").trim();
        }
        return null;
    }
    
    /**
     * Strip the $ prefix from PHP variable names.
     */
    private String stripPhpVarPrefix(String name) {
        if (name != null && name.startsWith("$")) {
            return name.substring(1);
        }
        return name;
    }
    
    /**
     * Extract the name from a node that has a "name" child.
     */
    private String extractName(String source, TSNode node) {
        return TreeSitterHelper.extractName(source, node, "name");
    }
    
    /**
     * Extract methods from a body node (class, trait, or enum body) into a TypeInfo.
     */
    private void extractMethodsFromBody(String source, TSNode bodyNode, TypeInfo typeInfo) {
        for (TSNode method : findAllChildren(bodyNode, "method_declaration")) {
            if (method == null || method.isNull()) continue;
            MethodInfo methodInfo = analyzeMethod(source, method, typeInfo.name);
            if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                typeInfo.methods.add(methodInfo);
            }
        }
    }
    
    /**
     * Extract enum cases as fields from an enum body.
     */
    private void extractEnumCases(String source, TSNode enumBody, TypeInfo typeInfo) {
        for (TSNode enumCase : findAllChildren(enumBody, "enum_case")) {
            if (enumCase == null || enumCase.isNull()) continue;
            
            FieldInfo field = new FieldInfo();
            field.modifiers.add("case");
            field.name = extractName(source, enumCase);
            
            // Get case value type if backed enum (e.g., case Red = 'red')
            TSNode valueNode = findEnumCaseValue(enumCase);
            if (valueNode != null) {
                field.type = inferTypeFromValue(source, valueNode);
            }
            
            if (field.name != null && !field.name.isEmpty()) {
                typeInfo.fields.add(field);
            }
        }
    }
    
    /**
     * Find the value node in an enum case (string, integer, or encapsed_string).
     */
    private TSNode findEnumCaseValue(TSNode enumCase) {
        for (int i = 0; i < enumCase.getNamedChildCount(); i++) {
            TSNode child = enumCase.getNamedChild(i);
            if (child != null && !child.isNull()) {
                String childType = child.getType();
                if ("string".equals(childType) || "integer".equals(childType) || 
                    "encapsed_string".equals(childType)) {
                    return child;
                }
            }
        }
        return null;
    }
    
    /**
     * Extract the class name from an object_creation_expression (new ClassName()).
     */
    private String extractCreatedTypeName(String source, TSNode objectCreation) {
        TSNode classType = findFirstChild(objectCreation, "class_type_designator");
        if (classType == null || classType.isNull()) return null;
        
        TSNode typeName = findFirstChild(classType, "qualified_name");
        if (typeName == null || typeName.isNull()) {
            typeName = findFirstChild(classType, "name");
        }
        return (typeName != null && !typeName.isNull()) ? getNodeText(source, typeName) : null;
    }
    
    private String inferTypeFromValue(String source, TSNode valueNode) {
        if (valueNode == null) return null;
        String type = valueNode.getType();
        switch (type) {
            case "string": return "string";
            case "integer": return "int";
            case "float": return "float";
            case "boolean": return "bool";
            case "array_creation_expression": return "array";
            default: return null;
        }
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
                if (type != null && ("class_declaration".equals(type) || "trait_declaration".equals(type) || "enum_declaration".equals(type))) {
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
        return identifyNestedNodes(allClasses, "declaration_list", "class_declaration");
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
        
        // Extract trait usage, fields, and methods
        extractTraitUsage(source, classBody, typeInfo);
        typeInfo.fields.addAll(collectFieldsFromBody(source, classBody));
        extractMethodsFromBody(source, classBody, typeInfo);
        
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
        typeInfo.name = extractName(source, classDecl);
        
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
        typeInfo.name = extractName(source, interfaceDecl);
        
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
            
            mi.name = extractName(source, m);
            
            // Return type
            mi.returnType = extractReturnType(source, findTypeNode(m));
            
            // Parameters
            TSNode fp = findFirstChild(m, "formal_parameters");
            if (fp != null && !fp.isNull()) {
                analyzeParameters(source, fp, mi);
            }
            
            // Interface methods are implicitly public and abstract
            mi.visibility = "public";
            mi.modifiers.add("abstract");
            
            if (mi.name != null && !mi.name.isEmpty()) {
                typeInfo.methods.add(mi);
            }
        }
        
        return typeInfo;
    }
    
    private TypeInfo analyzeTrait(String source, TSNode traitDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "trait";
        
        typeInfo.name = extractName(source, traitDecl);
        
        // Get the declaration_list (trait body)
        TSNode traitBody = findFirstChild(traitDecl, "declaration_list");
        if (traitBody != null && !traitBody.isNull()) {
            extractTraitUsage(source, traitBody, typeInfo);
            typeInfo.fields.addAll(collectFieldsFromBody(source, traitBody));
            extractMethodsFromBody(source, traitBody, typeInfo);
        }
        
        return typeInfo;
    }
    
    /**
     * Analyze a PHP 8.1+ enum declaration.
     * Enums can be unit enums (no backing type) or backed enums (string or int).
     * They can implement interfaces and use traits.
     */
    private TypeInfo analyzeEnum(String source, TSNode enumDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "enum";
        
        typeInfo.name = extractName(source, enumDecl);
        
        // Get backing type if present (: string or : int)
        // In tree-sitter PHP, this might be represented as a type node
        TSNode backingType = findFirstChild(enumDecl, "primitive_type");
        if (backingType == null || backingType.isNull()) {
            backingType = findFirstChild(enumDecl, "named_type");
        }
        if (backingType != null && !backingType.isNull()) {
            typeInfo.extendsType = getNodeText(source, backingType);
        }
        
        // Get implemented interfaces
        TSNode interfaceClause = findFirstChild(enumDecl, "class_interface_clause");
        if (interfaceClause != null && !interfaceClause.isNull()) {
            List<TSNode> interfaceNames = findAllDescendants(interfaceClause, "name");
            for (TSNode interfaceName : interfaceNames) {
                if (interfaceName != null && !interfaceName.isNull()) {
                    typeInfo.implementsInterfaces.add(getNodeText(source, interfaceName));
                }
            }
        }
        
        // Get the enum body (enum_declaration_list)
        TSNode enumBody = findFirstChild(enumDecl, "enum_declaration_list");
        if (enumBody != null && !enumBody.isNull()) {
            extractTraitUsage(source, enumBody, typeInfo);
            extractEnumCases(source, enumBody, typeInfo);
            extractMethodsFromBody(source, enumBody, typeInfo);
        }
        
        return typeInfo;
    }
    
    /**
     * Extract trait usage (use statements) from a class or trait body.
     * In PHP: use TraitName; or use Trait1, Trait2 { ... }
     * 
     * We need to be careful to only extract trait names, not method names
     * from conflict resolution blocks (insteadof, as clauses).
     */
    private void extractTraitUsage(String source, TSNode bodyNode, TypeInfo typeInfo) {
        if (bodyNode == null || bodyNode.isNull()) return;
        
        // Find use_declaration nodes (PHP's trait usage syntax)
        List<TSNode> useDecls = findAllChildren(bodyNode, "use_declaration");
        for (TSNode useDecl : useDecls) {
            if (useDecl == null || useDecl.isNull()) continue;
            
            // Extract trait names from direct children only (not from use_list adaptations)
            // The structure is: use_declaration -> name | qualified_name | use_list
            // We want to avoid names inside use_list which contains conflict resolution
            int childCount = useDecl.getNamedChildCount();
            for (int i = 0; i < childCount; i++) {
                TSNode child = useDecl.getNamedChild(i);
                if (child == null || child.isNull()) continue;
                
                String childType = child.getType();
                
                // Direct trait name reference
                if ("name".equals(childType) || "qualified_name".equals(childType)) {
                    String traitName = getNodeText(source, child);
                    if (traitName != null && !traitName.isEmpty()) {
                        typeInfo.mixins.add(traitName);
                    }
                }
                // use_list contains the trait names before the { } block
                // Structure: use_list -> name, name, ... (comma-separated trait names)
                else if ("use_list".equals(childType)) {
                    // Get only direct name/qualified_name children of use_list
                    int listChildCount = child.getNamedChildCount();
                    for (int j = 0; j < listChildCount; j++) {
                        TSNode listChild = child.getNamedChild(j);
                        if (listChild == null || listChild.isNull()) continue;
                        
                        String listChildType = listChild.getType();
                        if ("name".equals(listChildType) || "qualified_name".equals(listChildType)) {
                            String traitName = getNodeText(source, listChild);
                            if (traitName != null && !traitName.isEmpty()) {
                                typeInfo.mixins.add(traitName);
                            }
                        }
                    }
                }
            }
        }
    }
    
    private MethodInfo analyzeMethod(String source, TSNode methodDecl, String className) {
        MethodInfo methodInfo = new MethodInfo();
        
        // Extract modifiers and visibility (public, private, protected, static, final, abstract)
        extractModifiersAndVisibility(source, methodDecl, methodInfo);
        
        // Common analysis for name, return type, parameters, and body
        analyzeMethodOrFunction(source, methodDecl, methodInfo);
        
        return methodInfo;
    }
    
    private MethodInfo analyzeFunction(String source, TSNode funcDef) {
        MethodInfo methodInfo = new MethodInfo();
        
        // Standalone functions have no modifiers/visibility
        analyzeMethodOrFunction(source, funcDef, methodInfo);
        
        return methodInfo;
    }
    
    /**
     * Common analysis for both methods and standalone functions.
     * Extracts name, return type, parameters, and analyzes body.
     */
    private void analyzeMethodOrFunction(String source, TSNode node, MethodInfo methodInfo) {
        methodInfo.name = extractName(source, node);
        methodInfo.returnType = extractReturnType(source, findTypeNode(node));
        
        // Get parameters
        TSNode paramsNode = findFirstChild(node, "formal_parameters");
        if (paramsNode != null) {
            analyzeParameters(source, paramsNode, methodInfo);
        }
        
        // Get body
        TSNode bodyNode = findFirstChild(node, "compound_statement");
        if (bodyNode != null) {
            analyzeMethodBody(source, bodyNode, methodInfo);
        }
    }
    
    private void analyzeParameters(String source, TSNode paramsNode, MethodInfo methodInfo) {
        // Iterate children by index to preserve source order across kinds
        int count = paramsNode.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = paramsNode.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            String kind = child.getType();

            if ("simple_parameter".equals(kind) || "property_promotion_parameter".equals(kind)) {
                TSNode varNameNode = findFirstChild(child, "variable_name");
                if (varNameNode == null || varNameNode.isNull()) continue;
                
                String name = stripPhpVarPrefix(getNodeText(source, varNameNode));
                if (!isValidIdentifier(name)) continue;
                
                TSNode typeNode = findTypeNode(child);
                String paramType = (typeNode != null && !typeNode.isNull()) ? getNodeText(source, typeNode) : null;
                methodInfo.parameters.add(new Parameter(name, paramType));
            }
        }
    }
    
    private void analyzeMethodBody(String source, TSNode bodyNode, MethodInfo methodInfo) {
        // Build a map of variable names to their types
        Map<String, String> localTypes = new HashMap<>();
        
        // Find variable assignments (PHP doesn't have explicit declarations)
        for (TSNode assignment : findAllDescendants(bodyNode, "assignment_expression")) {
            if (assignment == null || assignment.isNull()) continue;
            TSNode leftSide = assignment.getNamedChild(0);
            if (leftSide == null || leftSide.isNull() || !"variable_name".equals(leftSide.getType())) continue;
            
            String rawName = getNodeText(source, leftSide);
            if (rawName == null || "$this".equals(rawName)) continue;
            
            String varName = stripPhpVarPrefix(rawName);
            if (isValidIdentifier(varName) && !methodInfo.localVariables.contains(varName)) {
                methodInfo.localVariables.add(varName);
            }
            
            // Minimal type inference: $var = new ClassName(...);
            TSNode rightSide = assignment.getNamedChild(1);
            if (rightSide != null && !rightSide.isNull() && "object_creation_expression".equals(rightSide.getType())) {
                String inferredType = extractCreatedTypeName(source, rightSide);
                if (inferredType != null && !inferredType.isBlank()) {
                    localTypes.put(varName, inferredType);
                }
            }
        }
        
        // Process function calls: func()
        for (TSNode callExpr : findAllDescendants(bodyNode, "function_call_expression")) {
            if (callExpr == null || callExpr.isNull()) continue;
            TSNode functionNode = callExpr.getNamedChild(0);
            if (functionNode != null && "name".equals(functionNode.getType())) {
                String methodName = getNodeText(source, functionNode);
                if (isValidIdentifier(methodName)) {
                    int paramCount = countArguments(callExpr);
                    collectMethodCall(methodInfo, methodName, null, null, paramCount);
                }
            }
        }
        
        // Process member calls: $obj->method()
        for (TSNode memberCall : findAllDescendants(bodyNode, "member_call_expression")) {
            if (memberCall == null || memberCall.isNull()) continue;
            
            String[] callInfo = extractMemberCallInfo(source, memberCall);
            String methodName = callInfo[0];
            String objectName = callInfo[1];
            String objectType = (objectName != null && !"this".equals(objectName)) ? localTypes.get(objectName) : null;
            
            if (isValidIdentifier(methodName)) {
                int paramCount = countArguments(memberCall);
                collectMethodCall(methodInfo, methodName, objectType, objectName, paramCount);
            }
        }
        
        // Process scoped calls: Class::method()
        for (TSNode scopedCall : findAllDescendants(bodyNode, "scoped_call_expression")) {
            if (scopedCall == null || scopedCall.isNull()) continue;
            
            String[] callInfo = extractScopedCallInfo(source, scopedCall);
            if (isValidIdentifier(callInfo[0])) {
                int paramCount = countArguments(scopedCall);
                collectMethodCall(methodInfo, callInfo[0], callInfo[1], null, paramCount);
            }
        }
        
        // Sort method calls alphabetically
        methodInfo.methodCalls.sort(METHOD_CALL_COMPARATOR);
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
            
            // Get type using helper
            TSNode typeNode = findTypeNode(propDecl);
            String typeText = (typeNode != null && !typeNode.isNull()) ? getNodeText(source, typeNode) : null;
            
            // Each property_declaration can have multiple property_element children
            for (TSNode propElement : findAllChildren(propDecl, "property_element")) {
                if (propElement == null || propElement.isNull()) continue;
                
                FieldInfo fieldInfo = new FieldInfo();
                fieldInfo.visibility = visibility;
                fieldInfo.type = typeText;
                
                if (visibility != null) fieldInfo.modifiers.add(visibility);
                if (isStatic) fieldInfo.modifiers.add("static");
                
                // Get property name (PHP properties start with $)
                TSNode varNode = findFirstChild(propElement, "variable_name");
                if (varNode != null && !varNode.isNull()) {
                    fieldInfo.name = stripPhpVarPrefix(getNodeText(source, varNode));
                }
                
                if (isValidIdentifier(fieldInfo.name)) {
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
