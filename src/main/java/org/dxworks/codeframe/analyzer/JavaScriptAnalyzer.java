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
    // Node type constants
    private static final String NT_FORMAL_PARAMETER = "formal_parameter";
    private static final String NT_REQUIRED_PARAMETER = "required_parameter";
    private static final String NT_OPTIONAL_PARAMETER = "optional_parameter";
    private static final String NT_PARENTHESIZED = "parenthesized_expression";
    private static final String NT_ASSIGNMENT_PATTERN = "assignment_pattern";
    private static final String NT_OBJECT_PATTERN = "object_pattern";
    private static final String NT_ARRAY_PATTERN = "array_pattern";
    private static final String NT_OBJECT = "object";
    private static final String NT_IDENTIFIER = "identifier";
    private static final String NT_MEMBER_EXPRESSION = "member_expression";
    private static final String NT_PROPERTY_IDENTIFIER = "property_identifier";
    private static final String NT_SUPER = "super";
    private static final String NT_ARRAY = "array";
    private static final String NT_STRING = "string";
    private static final String NT_NUMBER = "number";
    
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
        Set<Integer> nestedClassIds = identifyNestedNodes(allClasses, "class_body", "class_declaration");
        
        // Process only top-level classes recursively
        for (TSNode classDecl : allClasses) {
            if (!nestedClassIds.contains(classDecl.getStartByte())) {
                analyzeClassRecursively(sourceCode, classDecl, analysis);
            }
        }
        
        // Find class expressions (const Point = class { ... })
        extractClassExpressions(sourceCode, rootNode, analysis);
        
        // Find standalone functions (regular and generator)
        List<TSNode> functionDecls = findAllDescendants(rootNode, "function_declaration");
        List<TSNode> genFunctionDecls = findAllDescendants(rootNode, "generator_function_declaration");
        for (TSNode funcDecl : functionDecls) {
            MethodInfo methodInfo = analyzeFunction(sourceCode, funcDecl);
            if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                analysis.methods.add(methodInfo);
            }
        }
        for (TSNode genDecl : genFunctionDecls) {
            MethodInfo methodInfo = analyzeFunction(sourceCode, genDecl);
            if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                methodInfo.modifiers.add("function*");
                analysis.methods.add(methodInfo);
            }
        }

        // Find exported variable-declared functions (arrow functions or function expressions)
        // Only process top-level declarations (not nested inside functions/classes)
        List<TSNode> varDeclarators = findAllDescendants(rootNode, "variable_declarator");
        for (TSNode declarator : varDeclarators) {
            // Skip nested declarators (inside functions, classes, etc.)
            if (!isTopLevelDeclarator(declarator)) continue;
            
            TSNode nameId = findFirstChild(declarator, "identifier");
            if (nameId == null) continue;
            TSNode initializer = null;
            // initializer is typically the second named child
            if (declarator.getNamedChildCount() > 1) {
                initializer = declarator.getNamedChild(1);
            }
            if (initializer == null) continue;
            String initType = initializer.getType();
            boolean isFunc = "arrow_function".equals(initType) || "function".equals(initType) || "function_expression".equals(initType) || "generator_function".equals(initType);
            if (!isFunc) continue;

            MethodInfo mi = new MethodInfo();
            mi.name = getNodeText(sourceCode, nameId);
            // Detect async on initializer (arrow/function expression) by inspecting declarator text
            String declaratorText = getNodeText(sourceCode, declarator);
            int eqIdx = declaratorText.indexOf('=');
            int lparenIdx = declaratorText.indexOf('(');
            if (eqIdx >= 0 && lparenIdx > eqIdx) {
                String betweenEqAndParen = declaratorText.substring(eqIdx, lparenIdx);
                if (betweenEqAndParen.contains("async")) {
                    mi.modifiers.add("async");
                }
            }
            // Detect declaration kind: const/let/var via nearest declaration ancestor
            TSNode anc = declarator.getParent();
            TSNode declNode = null;
            while (anc != null && !anc.isNull()) {
                String at = anc.getType();
                if ("lexical_declaration".equals(at) || "variable_declaration".equals(at)) {
                    declNode = anc;
                    break;
                }
                anc = anc.getParent();
            }
            if (declNode != null) {
                String dtext = getNodeText(sourceCode, declNode);
                if (dtext != null) {
                    if (dtext.trim().startsWith("const ") || dtext.contains(" const ")) mi.modifiers.add("const");
                    else if (dtext.trim().startsWith("let ") || dtext.contains(" let ")) mi.modifiers.add("let");
                    else if (dtext.trim().startsWith("var ") || dtext.contains(" var ")) mi.modifiers.add("var");
                }
            }

            // Detect export via ancestors: variable_declarator -> lexical_declaration -> export_statement
            anc = declarator.getParent();
            while (anc != null && !anc.isNull()) {
                String at = anc.getType();
                if ("export_statement".equals(at) || "export_default_declaration".equals(at)) {
                    mi.modifiers.add("export");
                    String ptxt = getNodeText(sourceCode, anc);
                    if (ptxt != null && ptxt.contains("export default")) {
                        mi.modifiers.add("default");
                    }
                    break;
                }
                anc = anc.getParent();
            }
            mi.visibility = null;
            // Parameters (when present)
            TSNode paramsNode = findFirstChild(initializer, "formal_parameters");
            if (paramsNode != null) {
                analyzeParameters(sourceCode, paramsNode, mi);
            }
            // Body (only if block body present)
            TSNode bodyNode = findFirstChild(initializer, "statement_block");
            if (bodyNode != null) {
                analyzeMethodBody(sourceCode, bodyNode, mi, null);
            }
            if (mi.name != null && !mi.name.isEmpty()) {
                analysis.methods.add(mi);
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
        // Find lexical_declaration (const, let) and variable_declaration (var) at top level
        int childCount = rootNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            
            String nodeType = child.getType();
            TSNode declNode = null;
            
            // Handle export statements wrapping declarations
            if ("export_statement".equals(nodeType)) {
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
                TSNode nameNode = findFirstChild(declarator, NT_IDENTIFIER);
                if (nameNode == null) continue;
                
                // Check if this is a function or class expression
                TSNode initializer = declarator.getNamedChildCount() > 1 ? declarator.getNamedChild(1) : null;
                if (initializer != null) {
                    String initType = initializer.getType();
                    if ("arrow_function".equals(initType) || "function".equals(initType) || 
                        "function_expression".equals(initType) || "generator_function".equals(initType)) {
                        continue; // Skip functions - they're already captured as methods
                    }
                    if ("class".equals(initType)) {
                        continue; // Skip class expressions - they're captured as types
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
                if ("export_statement".equals(nodeType)) {
                    field.modifiers.add("export");
                }
                
                // Infer type from initializer
                if (initializer != null) {
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
     * Extract class expressions (const Point = class { ... }) as types.
     */
    private void extractClassExpressions(String source, TSNode rootNode, FileAnalysis analysis) {
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
                TSNode nameNode = findFirstChild(declarator, NT_IDENTIFIER);
                if (nameNode == null) continue;
                
                // Check if this is a class expression
                TSNode initializer = declarator.getNamedChildCount() > 1 ? declarator.getNamedChild(1) : null;
                if (initializer == null || !"class".equals(initializer.getType())) {
                    continue;
                }
                
                // Analyze the class expression
                String varName = getNodeText(source, nameNode);
                TypeInfo typeInfo = analyzeClassExpression(source, initializer, varName);
                
                if (isExported) {
                    typeInfo.modifiers.add("export");
                }
                
                if (typeInfo.name != null && !typeInfo.name.isEmpty()) {
                    analysis.types.add(typeInfo);
                }
            }
        }
    }
    
    /**
     * Analyze a class expression node (the 'class' node itself, not class_declaration).
     */
    private TypeInfo analyzeClassExpression(String source, TSNode classExpr, String variableName) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "class";
        
        // For class expressions, use the variable name as the class name
        // e.g., const Point = class { ... } -> name is "Point"
        // e.g., const Rectangle = class RectangleClass { ... } -> name is "Rectangle" (variable name takes precedence)
        typeInfo.name = variableName;
        
        // Get heritage clause (extends)
        TSNode heritageNode = findFirstChild(classExpr, "class_heritage");
        if (heritageNode != null) {
            String heritageText = getNodeText(source, heritageNode);
            if (heritageText != null) {
                String trimmed = heritageText.trim();
                trimmed = trimmed.replaceFirst("^(?i)extends\\s+", "").trim();
                if (!trimmed.isEmpty()) {
                    typeInfo.extendsType = trimmed;
                }
            }
        }
        
        // Get class body and extract fields/methods
        TSNode classBody = findFirstChild(classExpr, "class_body");
        if (classBody != null) {
            // Collect fields
            List<FieldInfo> fields = collectFieldsFromBody(source, classBody);
            typeInfo.fields.addAll(fields);
            
            // Analyze methods
            List<TSNode> methods = findAllChildren(classBody, "method_definition");
            for (TSNode method : methods) {
                MethodInfo methodInfo = analyzeMethod(source, method, typeInfo.name);
                if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                    typeInfo.methods.add(methodInfo);
                }
            }
        }
        
        return typeInfo;
    }
    
    /**
     * Extract a single call expression into a method call list.
     */
    private void extractCallIntoList(String source, TSNode callNode, List<MethodCall> calls) {
        TSNode functionNode = callNode.getNamedChild(0);
        if (functionNode == null || functionNode.isNull()) return;
        
        String methodName = null;
        String objectName = null;
        
        if (NT_MEMBER_EXPRESSION.equals(functionNode.getType())) {
            TSNode propNode = findFirstChild(functionNode, NT_PROPERTY_IDENTIFIER);
            if (propNode != null) {
                methodName = getNodeText(source, propNode);
            }
            TSNode objNode = functionNode.getNamedChild(0);
            if (objNode != null && !objNode.isNull()) {
                objectName = renderObjectName(source, objNode, JS_LITERAL_TYPES);
            }
        } else if (NT_IDENTIFIER.equals(functionNode.getType())) {
            methodName = getNodeText(source, functionNode);
        }
        
        if (methodName != null && isValidIdentifier(methodName)) {
            collectMethodCall(calls, methodName, null, objectName);
        }
    }
    
    /**
     * Check if a variable_declarator is at module level (not nested inside a function or class).
     */
    private boolean isTopLevelDeclarator(TSNode declarator) {
        TSNode parent = declarator.getParent();
        while (parent != null && !parent.isNull()) {
            String type = parent.getType();
            // If we hit program, it's top-level
            if ("program".equals(type)) {
                return true;
            }
            // If we hit a function body or class body, it's nested
            if ("statement_block".equals(type) || "class_body".equals(type) || 
                "function_declaration".equals(type) || "function".equals(type) ||
                "arrow_function".equals(type) || "method_definition".equals(type)) {
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

    // Record all identifier names found within a destructuring parameter pattern
    private void addDestructuredParams(String source, TSNode patternNode, MethodInfo methodInfo) {
        // Build a set of existing names to avoid duplicates
        Set<String> existing = new HashSet<>();
        for (Parameter p : methodInfo.parameters) {
            if (p != null && p.name != null) existing.add(p.name);
        }
        // Collect into a linked set to preserve order and dedupe within this pattern
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();

        // Collect plain identifiers
        List<TSNode> ids = findAllDescendants(patternNode, "identifier");
        for (TSNode id : ids) {
            String name = getNodeText(source, id);
            if (name != null && name.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
                names.add(name);
            }
        }
        // Collect shorthand property identifiers specific to object patterns
        List<TSNode> sids = findAllDescendants(patternNode, "shorthand_property_identifier");
        for (TSNode sid : sids) {
            String name = getNodeText(source, sid);
            if (name != null && name.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
                names.add(name);
            }
        }
        // Collect shorthand property identifier patterns (as used in parameter patterns)
        List<TSNode> spids = findAllDescendants(patternNode, "shorthand_property_identifier_pattern");
        for (TSNode spid : spids) {
            String name = getNodeText(source, spid);
            if (name != null && name.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
                names.add(name);
            }
        }
        // Exclude non-binding outer keys: do not collect generic property_identifier here
        // Collect from object_assignment_pattern: left may be a shorthand_property_identifier_pattern
        List<TSNode> assigns = findAllDescendants(patternNode, "object_assignment_pattern");
        for (TSNode assign : assigns) {
            TSNode left = assign.getNamedChildCount() > 0 ? assign.getNamedChild(0) : null;
            if (left != null) {
                String ltype = left.getType();
                if ("shorthand_property_identifier_pattern".equals(ltype)
                    || "identifier".equals(ltype)
                    || "property_identifier".equals(ltype)) {
                    String name = getNodeText(source, left);
                    if (name != null && name.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
                        names.add(name);
                    }
                }
            }
        }

        // Append unique names not already present on the method
        for (String n : names) {
            if (!existing.contains(n)) {
                methodInfo.parameters.add(new Parameter(n, null));
                existing.add(n);
            }
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
        
        // Get heritage clause (extends) and capture full expression (e.g., React.Component)
        TSNode heritageNode = findFirstChild(classDecl, "class_heritage");
        if (heritageNode != null) {
            // Primary approach: use the raw text of the heritage and strip the 'extends' keyword
            String heritageText = getNodeText(source, heritageNode);
            if (heritageText != null) {
                String trimmed = heritageText.trim();
                // Remove a leading 'extends' token and following whitespace
                trimmed = trimmed.replaceFirst("^(?i)extends\\s+", "").trim();
                if (!trimmed.isEmpty()) {
                    typeInfo.extendsType = trimmed;
                }
            }
            // Fallbacks if needed
            if (typeInfo.extendsType == null || typeInfo.extendsType.isEmpty()) {
                TSNode extendsClause = findFirstChild(heritageNode, "extends_clause");
                if (extendsClause != null) {
                    TSNode target = findFirstDescendant(extendsClause, "member_expression");
                    if (target == null) target = findFirstDescendant(extendsClause, "identifier");
                    if (target == null && extendsClause.getNamedChildCount() > 0) {
                        target = extendsClause.getNamedChild(extendsClause.getNamedChildCount() - 1);
                    }
                    if (target != null) {
                        typeInfo.extendsType = getNodeText(source, target);
                    }
                }
            }
        }
        
        // Mark exported classes via parent export nodes
        TSNode parent = classDecl.getParent();
        if (parent != null && !parent.isNull()) {
            String ptype = parent.getType();
            if ("export_statement".equals(ptype) || "export_default_declaration".equals(ptype)) {
                typeInfo.modifiers.add("export");
                String parentText = getNodeText(source, parent);
                if (parentText != null && parentText.contains("export default")) {
                    typeInfo.modifiers.add("default");
                }
            }
        }
        
        return typeInfo;
    }
    
    private MethodInfo analyzeMethod(String source, TSNode methodDef, String className) {
        MethodInfo methodInfo = new MethodInfo();
        
        // Get method name (property or private property identifier)
        TSNode nameNode = findFirstChild(methodDef, "property_identifier");
        if (nameNode == null) {
            nameNode = findFirstChild(methodDef, "private_property_identifier");
            if (nameNode != null) {
                methodInfo.visibility = "private";
            }
        }
        if (nameNode != null) {
            methodInfo.name = getNodeText(source, nameNode);
        }
        // Detect async/static/generator by inspecting the source prefix before the method name
        // Only look at text BEFORE the method name (not the body) to avoid false positives
        if (methodInfo.name != null && nameNode != null) {
            // Get only the text from start of method_definition to start of name node
            int methodStart = methodDef.getStartByte();
            int nameStart = nameNode.getStartByte();
            String beforeName = "";
            if (nameStart > methodStart) {
                beforeName = source.substring(methodStart, nameStart);
            }
            // Generator methods have * before the name (e.g., *myGenerator())
            if (beforeName.contains("*")) {
                methodInfo.modifiers.add("*");
            }
            if (beforeName.contains("async")) {
                methodInfo.modifiers.add("async");
            }
            if (beforeName.contains("static")) {
                methodInfo.modifiers.add("static");
            }
            // Accessors: get/set
            // Check tokens before name to avoid matching identifiers named 'get'/'set'
            String beforeTrim = beforeName.trim();
            if (beforeTrim.startsWith("get ") || beforeTrim.equals("get")) {
                methodInfo.modifiers.add("get");
            } else if (beforeTrim.startsWith("set ") || beforeTrim.equals("set")) {
                methodInfo.modifiers.add("set");
            }
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
        
        // Get function name, then detect async by inspecting prefix before the name
        TSNode nameNode = findFirstChild(funcDecl, "identifier");
        if (nameNode != null) {
            methodInfo.name = getNodeText(source, nameNode);
        }
        String funcText = getNodeText(source, funcDecl);
        if (methodInfo.name != null) {
            int fnameIdx = funcText.indexOf(methodInfo.name);
            String beforeName = fnameIdx > 0 ? funcText.substring(0, fnameIdx) : funcText;
            if (beforeName.contains("async")) {
                methodInfo.modifiers.add("async");
            }
        }
        
        // Mark exported standalone functions via parent export nodes
        TSNode parent = funcDecl.getParent();
        if (parent != null && !parent.isNull()) {
            String ptype = parent.getType();
            if ("export_statement".equals(ptype) || "export_default_declaration".equals(ptype)) {
                methodInfo.modifiers.add("export");
                String parentText = getNodeText(source, parent);
                if (parentText != null && parentText.contains("export default")) {
                    methodInfo.modifiers.add("default");
                }
            }
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
        // Handle direct pattern child case: formal_parameters -> object_pattern/array_pattern/object
        if (count == 1) {
            TSNode only = paramsNode.getNamedChild(0);
            String otype = only.getType();
            if (NT_OBJECT_PATTERN.equals(otype) || NT_ARRAY_PATTERN.equals(otype) || NT_OBJECT.equals(otype)) {
                addDestructuredParams(source, only, methodInfo);
                return;
            }
        }
        for (int i = 0; i < count; i++) {
            TSNode param = paramsNode.getNamedChild(i);
            String paramName = null;

            // Unwrap common wrappers and assignment LHS when present
            TSNode target = unwrapParameterNode(param);
            String ptype = target.getType();

            // Global fallback: if the parameter subtree contains a destructuring pattern anywhere, record identifiers from it
            TSNode anyPattern = findFirstDescendant(target, NT_OBJECT_PATTERN);
            if (anyPattern == null) anyPattern = findFirstDescendant(target, NT_ARRAY_PATTERN);
            if (anyPattern == null) anyPattern = findFirstDescendant(target, NT_OBJECT);
            if (anyPattern != null) {
                addDestructuredParams(source, anyPattern, methodInfo);
                continue;
            }
            
            if (NT_IDENTIFIER.equals(ptype)) {
                paramName = getNodeText(source, target);
            } else if (NT_ASSIGNMENT_PATTERN.equals(ptype)) {
                // Default parameter: param = value
                TSNode nameNode = findFirstChild(target, NT_IDENTIFIER);
                if (nameNode != null) {
                    paramName = getNodeText(source, nameNode);
                } else {
                    // Destructuring with defaults: ({ a, b } = {}) or ([x, y] = [])
                    TSNode lhs = target.getNamedChildCount() > 0 ? target.getNamedChild(0) : null;
                    if (lhs != null) {
                        String ltype = lhs.getType();
                        if (NT_OBJECT_PATTERN.equals(ltype) || NT_ARRAY_PATTERN.equals(ltype)) {
                            addDestructuredParams(source, lhs, methodInfo);
                            continue;
                        }
                    }
                }
            } else if (NT_OBJECT_PATTERN.equals(ptype) || NT_ARRAY_PATTERN.equals(ptype)) {
                // Destructured parameters: record all bound identifiers
                addDestructuredParams(source, target, methodInfo);
                continue;
            } else if ("rest_pattern".equals(ptype)) {
                // Rest parameter: ...args
                TSNode nameNode = findFirstChild(target, NT_IDENTIFIER);
                if (nameNode != null) {
                    paramName = "..." + getNodeText(source, nameNode);
                }
            }
            
            // Validate parameter name
            if (isValidIdentifier(paramName) || (paramName != null && paramName.startsWith("...") && isValidIdentifier(paramName.substring(3)))) {
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
                if (name != null && name.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
                    methodInfo.localVariables.add(name);
                    TSNode initializer = varDecl.getNamedChild(1);
                    if (initializer != null && !initializer.isNull()) {
                        String inferredType = inferTypeFromExpression(source, initializer);
                        if (inferredType != null) {
                            localTypes.put(name, inferredType);
                        }
                    }
                }
            }
        }

        // Dedupe locals while preserving order
        if (!methodInfo.localVariables.isEmpty()) {
            java.util.LinkedHashSet<String> uniqLocals = new java.util.LinkedHashSet<>(methodInfo.localVariables);
            methodInfo.localVariables.clear();
            methodInfo.localVariables.addAll(uniqLocals);
        }

        // Find call expressions
        List<TSNode> callExprs = findAllDescendants(bodyNode, "call_expression");
        for (TSNode callExpr : callExprs) {
            TSNode functionNode = callExpr.getNamedChild(0);
            if (functionNode == null) continue;

            String methodName = null;
            String objectName = null;
            String objectType = null;

            if (NT_MEMBER_EXPRESSION.equals(functionNode.getType())) {
                // obj.method() or obj.prop.method(); capture full object expression text
                TSNode propNode = findFirstChild(functionNode, NT_PROPERTY_IDENTIFIER);
                if (propNode != null) methodName = getNodeText(source, propNode);

                TSNode objectExpr = functionNode.getNamedChild(0);
                if (objectExpr != null) {
                    objectName = renderObjectName(source, objectExpr, JS_LITERAL_TYPES);
                    if (NT_IDENTIFIER.equals(objectExpr.getType())) {
                        objectType = localTypes.get(objectName);
                    }
                    if ("this".equals(objectName) && className != null) {
                        objectType = className;
                    }
                }
            } else if (NT_IDENTIFIER.equals(functionNode.getType())) {
                methodName = getNodeText(source, functionNode);
            } else if (NT_SUPER.equals(functionNode.getType())) {
                methodName = "super";
            }

            if (methodName != null && isValidIdentifier(methodName)) {
                collectMethodCall(methodInfo, methodName, objectType, objectName);
            }
        }

        // Sort method calls for stable output
        methodInfo.methodCalls.sort(TreeSitterHelper.METHOD_CALL_COMPARATOR);
    }

    // Helpers introduced by refactor (no behavior change)
    private TSNode unwrapParameterNode(TSNode node) {
        TSNode target = node;
        String type = target.getType();
        boolean changed = true;
        while (changed) {
            changed = false;
            if (NT_FORMAL_PARAMETER.equals(type) || NT_REQUIRED_PARAMETER.equals(type) || NT_OPTIONAL_PARAMETER.equals(type) || NT_PARENTHESIZED.equals(type)) {
                if (target.getNamedChildCount() > 0) {
                    target = target.getNamedChild(0);
                    type = target.getType();
                    changed = true;
                }
            }
            if (NT_ASSIGNMENT_PATTERN.equals(type)) {
                if (target.getNamedChildCount() > 0) {
                    target = target.getNamedChild(0); // left-hand side
                    type = target.getType();
                    changed = true;
                }
            }
        }
        return target;
    }

    // Literal types for renderObjectName
    private static final String[] JS_LITERAL_TYPES = {NT_ARRAY, NT_OBJECT, NT_STRING, NT_NUMBER};

    
    private List<FieldInfo> collectFieldsFromBody(String source, TSNode classBody) {
        List<FieldInfo> fields = new ArrayList<>();
        if (classBody == null) return fields;

        // Only direct field_definition children of this class body
        List<TSNode> fieldDecls = findAllChildren(classBody, "field_definition");

        for (TSNode field : fieldDecls) {
            FieldInfo fieldInfo = new FieldInfo();

            // Detect static via child or text prefix
            boolean isStatic = false;
            for (int i = 0; i < field.getNamedChildCount(); i++) {
                TSNode child = field.getNamedChild(i);
                if ("static".equals(child.getType())) { isStatic = true; break; }
            }
            if (!isStatic) {
                String text = getNodeText(source, field);
                if (text != null && text.trim().startsWith("static ")) isStatic = true;
            }
            if (isStatic) fieldInfo.modifiers.add("static");

            // Name: property_identifier or private_property_identifier
            TSNode nameNode = findFirstChild(field, "property_identifier");
            if (nameNode == null) {
                nameNode = findFirstChild(field, "private_property_identifier");
                if (nameNode != null) fieldInfo.visibility = "private";
            }
            if (nameNode != null) fieldInfo.name = getNodeText(source, nameNode);

            if (fieldInfo.name != null && fieldInfo.name.startsWith("#")) {
                fieldInfo.visibility = "private";
            }

            // Infer type from initializer (if present)
            TSNode initializer = null;
            int childCount = field.getNamedChildCount();
            if (childCount > 0) {
                initializer = field.getNamedChild(childCount - 1);
            }
            if (initializer != null && !initializer.isNull() && !NT_PROPERTY_IDENTIFIER.equals(initializer.getType())) {
                fieldInfo.type = inferTypeFromExpression(source, initializer);
            }

            if (fieldInfo.name != null) fields.add(fieldInfo);
        }

        return fields;
    }
    
    private String inferTypeFromExpression(String source, TSNode expr) {
        if (expr == null || expr.isNull()) return null;
        
        String exprType = expr.getType();
        
        // JavaScript-specific: Primitive literals use capitalized types
        if ("number".equals(exprType)) return "Number";
        if ("string".equals(exprType)) return "String";
        if ("true".equals(exprType) || "false".equals(exprType)) return "Boolean";
        
        // Delegate to common inference for shared patterns (new, array, object, function, call)
        return inferCommonExpressionType(source, expr);
    }
}
