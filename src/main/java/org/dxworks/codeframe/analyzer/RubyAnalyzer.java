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

public class RubyAnalyzer implements LanguageAnalyzer {
    
    @Override
    public FileAnalysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        FileAnalysis analysis = new FileAnalysis();
        analysis.filePath = filePath;
        analysis.language = "ruby";
        
        try {
            // Extract requires (Ruby's imports)
            extractRequires(sourceCode, rootNode, analysis);
            
            // Find all class definitions and process only top-level ones (not inside any class/module)
            List<TSNode> allClasses = findAllDescendants(rootNode, "class");
            for (TSNode classDecl : allClasses) {
                if (classDecl == null || classDecl.isNull()) continue;
                if (!isInsideClassOrModule(classDecl)) {
                    analyzeClassRecursively(sourceCode, classDecl, analysis);
                }
            }
            
            // Find all module definitions and process only top-level ones (not inside any class/module)
            List<TSNode> allModules = findAllDescendants(rootNode, "module");
            for (TSNode moduleDecl : allModules) {
                if (moduleDecl == null || moduleDecl.isNull()) continue;
                if (!isInsideClassOrModule(moduleDecl)) {
                    TypeInfo moduleInfo = analyzeModule(sourceCode, moduleDecl);
                    if (moduleInfo.name != null && !moduleInfo.name.isEmpty()) {
                        analysis.types.add(moduleInfo);
                    }
                }
            }
            
            // Find standalone methods (not inside classes/modules)
            List<TSNode> allMethods = findAllDescendants(rootNode, "method");
            for (TSNode methodDecl : allMethods) {
                if (methodDecl == null || methodDecl.isNull()) {
                    continue;
                }
                
                // Check if this method is not inside a class or module
                if (!isInsideClassOrModule(methodDecl)) {
                    MethodInfo methodInfo = analyzeMethod(sourceCode, methodDecl, null);
                    if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                        analysis.methods.add(methodInfo);
                    }
                }
            }
            
            // Extract file-level constants
            extractFileLevelFields(sourceCode, rootNode, analysis);
            
            // Extract file-level method calls
            extractFileLevelMethodCalls(sourceCode, rootNode, analysis);
        } catch (Exception e) {
            System.err.println("Error during Ruby analysis: " + e.getMessage());
            e.printStackTrace();
        }
        
        return analysis;
    }
    
    /**
     * Extract file-level constants (CONSTANT = value at top level, outside classes/modules).
     */
    private void extractFileLevelFields(String source, TSNode rootNode, FileAnalysis analysis) {
        // Find assignment nodes at top level
        int childCount = rootNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            
            // Look for assignment (CONSTANT = value)
            if ("assignment".equals(child.getType())) {
                TSNode leftNode = child.getNamedChild(0);
                if (leftNode != null && "constant".equals(leftNode.getType())) {
                    FieldInfo field = new FieldInfo();
                    field.name = getNodeText(source, leftNode);
                    field.modifiers.add("const");
                    
                    // Try to infer type from value
                    TSNode valueNode = child.getNamedChildCount() > 1 ? child.getNamedChild(1) : null;
                    if (valueNode != null) {
                        field.type = inferTypeFromValue(source, valueNode);
                    }
                    
                    if (field.name != null && !field.name.isEmpty()) {
                        analysis.fields.add(field);
                    }
                }
            }
        }
    }
    
    /**
     * Extract file-level method calls (outside any class, module, or method).
     */
    private void extractFileLevelMethodCalls(String source, TSNode rootNode, FileAnalysis analysis) {
        int childCount = rootNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = rootNode.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            
            String nodeType = child.getType();
            
            // Skip class, module, method definitions - we only want top-level calls
            if ("class".equals(nodeType) || "module".equals(nodeType) || "method".equals(nodeType)) {
                continue;
            }
            
            // Extract ALL calls within this top-level node (excluding require/require_relative)
            extractCallsFromNode(source, child, analysis.methodCalls, true);
        }
        analysis.methodCalls.sort(METHOD_CALL_COMPARATOR);
    }
    
    /**
     * Extract all method calls from a node into a list.
     * @param skipRequires if true, skip require/require_relative calls (they're captured in imports)
     */
    private void extractCallsFromNode(String source, TSNode node, List<MethodCall> calls, boolean skipRequires) {
        for (TSNode callExpr : findAllDescendants(node, "call")) {
            String methodName = extractMethodName(source, callExpr);
            if (methodName == null || methodName.isEmpty()) continue;
            
            // Skip require/require_relative if requested
            if (skipRequires && ("require".equals(methodName) || "require_relative".equals(methodName))) {
                continue;
            }
            
            TSNode receiverNode = extractReceiverNode(callExpr);
            String objectName = receiverNode != null ? getNodeText(source, receiverNode) : null;
            
            // If objectName equals methodName, it's a standalone call (e.g., puts "...")
            if (methodName.equals(objectName)) {
                objectName = null;
            }
            
            // Count arguments
            int parameterCount = 0;
            TSNode argsNode = findArgsNode(callExpr);
            if (argsNode != null && !argsNode.isNull()) {
                parameterCount = countArgumentNodes(argsNode);
            }
            
            collectMethodCall(calls, methodName, null, objectName, parameterCount);
        }
    }
    
    private String inferTypeFromValue(String source, TSNode valueNode) {
        if (valueNode == null) return null;
        String type = valueNode.getType();
        switch (type) {
            case "string": return "String";
            case "integer": return "Integer";
            case "float": return "Float";
            case "true":
            case "false": return "Boolean";
            case "array": return "Array";
            case "hash": return "Hash";
            default: return null;
        }
    }
    
    private void extractRequires(String source, TSNode rootNode, FileAnalysis analysis) {
        // Extract require statements
        List<TSNode> calls = findAllDescendants(rootNode, "call");
        for (TSNode call : calls) {
            if (call == null || call.isNull()) continue;
            
            TSNode method = findFirstChild(call, "identifier");
            if (method != null && !method.isNull()) {
                String methodName = getNodeText(source, method);
                if ("require".equals(methodName) || "require_relative".equals(methodName)) {
                    analysis.imports.add(getNodeText(source, call).trim());
                }
            }
        }
    }
    
    // Helpers for call analysis (structural refactor, no behavior change)
    private String extractMethodName(String source, TSNode callExpr) {
        if (callExpr == null || callExpr.isNull()) return null;
        for (int i = 0; i < callExpr.getNamedChildCount(); i++) {
            try {
                String fieldName = callExpr.getFieldNameForChild(i);
                if ("operator".equals(fieldName) || "method".equals(fieldName) || "name".equals(fieldName)) {
                    TSNode n = callExpr.getNamedChild(i);
                    if (n != null && !n.isNull()) return getNodeText(source, n);
                    break;
                }
            } catch (Exception ignored) { }
        }
        TSNode first = callExpr.getNamedChild(0);
        return first != null ? getNodeText(source, first) : null;
    }

    private TSNode extractReceiverNode(TSNode callExpr) {
        if (callExpr == null || callExpr.isNull()) return null;
        // Explicit self first-child
        if (callExpr.getNamedChildCount() > 1) {
            TSNode firstChild = callExpr.getNamedChild(0);
            if (firstChild != null && "self".equals(firstChild.getType())) {
                return firstChild;
            }
        }
        // By field name 'receiver'
        for (int i = 0; i < callExpr.getNamedChildCount(); i++) {
            try {
                String fieldName = callExpr.getFieldNameForChild(i);
                if ("receiver".equals(fieldName)) {
                    return callExpr.getNamedChild(i);
                }
            } catch (Exception ignored) { }
        }
        // Positional heuristic (first child is a viable receiver)
        if (callExpr.getNamedChildCount() > 1) {
            TSNode potential = callExpr.getNamedChild(0);
            if (potential != null && !potential.isNull()) {
                String t = potential.getType();
                if ("self".equals(t) || "identifier".equals(t) || "call".equals(t) ||
                        "constant".equals(t) || "instance_variable".equals(t) ||
                        "class_variable".equals(t) || "global_variable".equals(t)) {
                    return potential;
                }
            }
        }
        return null;
    }

    private TSNode findArgsNode(TSNode callExpr) {
        return getArgumentListNode(callExpr);
    }

    private String normalizeSymbol(String s) {
        if (s == null) return null;
        return s.replace(":", "");
    }

    private void addMethodCall(MethodInfo methodInfo, String methodName, String objectType, String objectName, int parameterCount) {
        if (methodName == null || methodName.isEmpty()) return;
        MethodCall call = new MethodCall(methodName, objectType, objectName, parameterCount);
        methodInfo.methodCalls.add(call);
    }

    private String[] determineObjectNameAndType(String source, TSNode receiverNode, String className, Map<String, String> localTypes) {
        String objName = null;
        String objType = null;
        if (receiverNode == null || receiverNode.isNull()) return new String[] { null, null };

        String receiverType = receiverNode.getType();
        if ("self".equals(receiverType)) {
            objName = "self";
            if (className != null) objType = className;
        } else if ("identifier".equals(receiverType)) {
            objName = getNodeText(source, receiverNode);
            objType = localTypes != null ? localTypes.get(objName) : null;
            if ("self".equals(objName) && className != null) {
                objType = className;
                objName = "self";
            }
        } else if ("constant".equals(receiverType)) {
            // Simple constant (e.g., User.where) -> objectType = "User", objectName = null
            String constantName = getNodeText(source, receiverNode);
            objName = null;
            objType = constantName;
        } else if ("scope_resolution".equals(receiverType)) {
            // Namespaced constant (e.g., Combustion::Engine.new) -> objectName = full scope, objectType = null
            String scoped = getNodeText(source, receiverNode);
            objName = scoped;
            objType = null;
        } else if ("instance_variable".equals(receiverType) || "class_variable".equals(receiverType) || "global_variable".equals(receiverType)) {
            objName = getNodeText(source, receiverNode);
            objType = null;
        } else if ("call".equals(receiverType)) {
            // Chained call - unknown
            objName = null;
            objType = null;
        }
        return new String[] { objName, objType };
    }

    private boolean isBareCall(String source, String methodName, TSNode receiverNode, TSNode callExpr) {
        if (callExpr == null || callExpr.isNull()) return true;
        if (receiverNode == null || receiverNode.isNull()) return true;
        if (methodName == null) return false;
        String recvText = getNodeText(source, receiverNode);
        return methodName.equals(recvText) && callExpr.getNamedChildCount() > 0;
    }

    private void extractRailsDslAnnotations(String source, TSNode bodyStatement, TypeInfo typeInfo) {
        if (bodyStatement == null || bodyStatement.isNull()) return;

        // Find all relevant invocation nodes anywhere inside this body (including under statement_list)
        List<TSNode> calls = new ArrayList<>();
        calls.addAll(findAllDescendants(bodyStatement, "call"));
        calls.addAll(findAllDescendants(bodyStatement, "command"));
        for (TSNode call : calls) {
            if (call == null || call.isNull()) continue;

            String methodName = null;
            // Prefer field-named method/operator from tree-sitter fields
            for (int i = 0; i < call.getNamedChildCount(); i++) {
                try {
                    String fieldName = call.getFieldNameForChild(i);
                    if ("operator".equals(fieldName) || "method".equals(fieldName)) {
                        TSNode m = call.getNamedChild(i);
                        if (m != null && !m.isNull()) methodName = getNodeText(source, m);
                        break;
                    }
                } catch (Exception ignored) { }
            }
            if (methodName == null) {
                TSNode first = call.getNamedChild(0);
                if (first != null && ("identifier".equals(first.getType()) || "operator".equals(first.getType()))) {
                    methodName = getNodeText(source, first);
                }
            }
            if (methodName == null) {
                continue;
            }

            // Known Rails DSL entry points
            boolean isAssoc = "has_many".equals(methodName) || "belongs_to".equals(methodName) || "has_one".equals(methodName);
            boolean isValidate = "validates".equals(methodName);
            boolean isCallback = methodName.startsWith("before_") || methodName.startsWith("after_") || methodName.startsWith("around_");
            boolean isScope = "scope".equals(methodName);
            if (!(isAssoc || isValidate || isCallback || isScope)) continue;

            // Locate arguments
            TSNode argList = getArgumentListNode(call);

            // no debug logging

            // Associations
            if (isAssoc) {
                String target = extractFirstSymbolOrIdentifierArg(source, argList);
                if (target != null) typeInfo.annotations.add("@" + methodName + "(" + target + ")");
                continue;
            }

            // Validations: emit one annotation per attribute name
            if (isValidate && argList != null) {
                for (int i = 0; i < argList.getNamedChildCount(); i++) {
                    TSNode arg = argList.getNamedChild(i);
                    if (arg == null || arg.isNull()) continue;
                    String t = arg.getType();
                    if ("symbol".equals(t) || "simple_symbol".equals(t) || "identifier".equals(t) || "constant".equals(t)) {
                        String attr = getNodeText(source, arg);
                        if (attr != null) {
                            attr = normalizeSymbol(attr);
                            if (!attr.isEmpty()) typeInfo.annotations.add("@validates(" + attr + ")");
                        }
                    }
                }
                continue;
            }

            // Callbacks
            if (isCallback && argList != null) {
                for (int i = 0; i < argList.getNamedChildCount(); i++) {
                    TSNode arg = argList.getNamedChild(i);
                    if (arg == null || arg.isNull()) continue;
                    String t = arg.getType();
                    if ("symbol".equals(t) || "simple_symbol".equals(t) || "identifier".equals(t) || "constant".equals(t)) {
                        String cb = getNodeText(source, arg);
                        if (cb != null) {
                            cb = normalizeSymbol(cb);
                            if (!cb.isEmpty()) typeInfo.annotations.add("@" + methodName + "(" + cb + ")");
                        }
                    }
                }
                continue;
            }

            // Scopes: only keep the name (no code)
            if (isScope && argList != null && argList.getNamedChildCount() > 0) {
                TSNode nameNode = argList.getNamedChild(0);
                if (nameNode != null && !nameNode.isNull()) {
                    String t = nameNode.getType();
                    if ("symbol".equals(t) || "simple_symbol".equals(t) || "identifier".equals(t) || "constant".equals(t)) {
                        String name = getNodeText(source, nameNode);
                        if (name != null) {
                            name = normalizeSymbol(name);
                            if (!name.isEmpty()) typeInfo.annotations.add("@scope(" + name + ")");
                        }
                    }
                }
            }
        }
    }

    private String extractFirstSymbolOrIdentifierArg(String source, TSNode argList) {
        if (argList == null || argList.isNull()) return null;
        for (int i = 0; i < argList.getNamedChildCount(); i++) {
            TSNode c = argList.getNamedChild(i);
            if (c == null || c.isNull()) continue;
            String t = c.getType();
            if ("symbol".equals(t) || "simple_symbol".equals(t) || "identifier".equals(t) || "constant".equals(t)) {
                String txt = getNodeText(source, c);
                if (txt != null) return normalizeSymbol(txt);
            }
        }
        return null;
    }

    private boolean isInsideClassOrModule(TSNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        
        TSNode parent = node.getParent();
        while (parent != null && !parent.isNull()) {
            String parentType = parent.getType();
            
            if ("class".equals(parentType) || "module".equals(parentType)) {
                return true;
            }
            
            if ("program".equals(parentType)) {
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
        if (typeInfo.name == null || typeInfo.name.isEmpty()) {
            return;
        }
        targetList.add(typeInfo);
        
        // Collect instance variables (fields) from this class
        List<FieldInfo> fields = collectInstanceVariables(source, classDecl);
        typeInfo.fields.addAll(fields);
        
        // Collect class variables (fields starting with @@)
        List<FieldInfo> classVars = collectClassVariables(source, classDecl);
        typeInfo.fields.addAll(classVars);
        
        // Collect constants from this class
        List<FieldInfo> constants = collectConstants(source, classDecl);
        typeInfo.fields.addAll(constants);
        
        // Get the body_statement node which contains the class members
        TSNode bodyStatement = findFirstChild(classDecl, "body_statement");
        if (bodyStatement != null && !bodyStatement.isNull()) {
            // Extract mixins (include/extend statements)
            extractMixins(source, bodyStatement, typeInfo);
            
            // Extract properties from attr_* declarations
            extractProperties(source, bodyStatement, typeInfo);
            
            // Extract Rails DSL annotations (associations, validations, callbacks, scopes)
            extractRailsDslAnnotations(source, bodyStatement, typeInfo);
            
            // Analyze methods with visibility tracking
            analyzeMethodsWithVisibility(source, bodyStatement, typeInfo);
            
            // Apply visibility symbol lists like: private :foo, :bar; protected :baz; public :qux
            applyVisibilitySymbolLists(source, bodyStatement, typeInfo);
            
            // Extract alias/alias_method annotations
            extractAliasAnnotations(source, bodyStatement, typeInfo);
            
            // Recursively process nested classes from the body_statement
            List<TSNode> nestedClasses = findAllChildren(bodyStatement, "class");
            for (TSNode nested : nestedClasses) {
                if (nested != null && !nested.isNull()) {
                    analyzeClassRecursivelyInto(source, nested, typeInfo.types);
                }
            }

            // Recursively process nested modules from the body_statement
            List<TSNode> nestedModules = findAllChildren(bodyStatement, "module");
            for (TSNode nestedModule : nestedModules) {
                if (nestedModule != null && !nestedModule.isNull()) {
                    TypeInfo nestedInfo = analyzeModule(source, nestedModule);
                    if (nestedInfo.name != null && !nestedInfo.name.isEmpty()) {
                        typeInfo.types.add(nestedInfo);
                    }
                }
            }
        }
    }
    
    private TypeInfo analyzeClass(String source, TSNode classNode) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "class";
        
        if (classNode == null || classNode.isNull()) {
            return typeInfo;
        }
        
        // Get class name - can be constant or scope_resolution
        typeInfo.name = extractName(source, classNode, "constant");
        if (typeInfo.name == null) {
            typeInfo.name = extractName(source, classNode, "scope_resolution");
        }
        
        // Determine visibility - Ruby classes are public by default
        typeInfo.visibility = "public";
        
        // Get superclass
        TSNode superclassNode = findFirstChild(classNode, "superclass");
        if (superclassNode != null && !superclassNode.isNull()) {
            typeInfo.extendsType = extractName(source, superclassNode, "constant");
            if (typeInfo.extendsType == null) {
                typeInfo.extendsType = extractName(source, superclassNode, "scope_resolution");
            }
        }
        
        return typeInfo;
    }
    
    private TypeInfo analyzeModule(String source, TSNode moduleNode) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "module";
        
        if (moduleNode == null || moduleNode.isNull()) {
            return typeInfo;
        }
        
        // Get module name
        typeInfo.name = extractName(source, moduleNode, "constant");
        if (typeInfo.name == null) {
            typeInfo.name = extractName(source, moduleNode, "scope_resolution");
        }
        
        typeInfo.visibility = "public";
        
        // Collect constants from this module
        List<FieldInfo> constants = collectConstants(source, moduleNode);
        typeInfo.fields.addAll(constants);
        
        // Get the body_statement node which contains the module members
        TSNode bodyStatement = findFirstChild(moduleNode, "body_statement");
        if (bodyStatement != null && !bodyStatement.isNull()) {
            // Extract mixins (include/extend) within module
            extractMixins(source, bodyStatement, typeInfo);

            // Extract properties if present (attr_* can appear in modules)
            extractProperties(source, bodyStatement, typeInfo);

            // Collect class variables under module context (module-level class vars)
            List<FieldInfo> classVars = collectClassVariables(source, moduleNode);
            typeInfo.fields.addAll(classVars);

            // Extract Rails DSL annotations (associations, validations, callbacks, scopes)
            extractRailsDslAnnotations(source, bodyStatement, typeInfo);
            // Analyze methods within this module body
            List<TSNode> methods = findAllChildren(bodyStatement, "method");
            for (TSNode method : methods) {
                if (method == null || method.isNull()) continue;
                
                MethodInfo methodInfo = analyzeMethod(source, method, typeInfo.name);
                if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                    typeInfo.methods.add(methodInfo);
                }
            }

            // Apply visibility symbol lists in modules as well
            applyVisibilitySymbolLists(source, bodyStatement, typeInfo);

            // Extract alias/alias_method annotations inside modules
            extractAliasAnnotations(source, bodyStatement, typeInfo);

            // Recursively process nested classes
            List<TSNode> nestedClasses = findAllChildren(bodyStatement, "class");
            for (TSNode nestedClass : nestedClasses) {
                if (nestedClass != null && !nestedClass.isNull()) {
                    analyzeClassRecursivelyInto(source, nestedClass, typeInfo.types);
                }
            }

            // Recursively process nested modules
            List<TSNode> nestedModules = findAllChildren(bodyStatement, "module");
            for (TSNode nestedModule : nestedModules) {
                if (nestedModule != null && !nestedModule.isNull()) {
                    TypeInfo nestedInfo = analyzeModule(source, nestedModule);
                    if (nestedInfo.name != null && !nestedInfo.name.isEmpty()) {
                        typeInfo.types.add(nestedInfo);
                    }
                }
            }
        }
        
        return typeInfo;
    }

    // No additional helpers

    private List<FieldInfo> collectClassVariables(String source, TSNode ownerNode) {
        List<FieldInfo> result = new ArrayList<>();
        if (ownerNode == null || ownerNode.isNull()) return result;
        // Direct class_variable tokens
        List<TSNode> classVarNodes = findAllDescendants(ownerNode, "class_variable");
        for (TSNode cv : classVarNodes) {
            if (cv == null || cv.isNull()) continue;
            String name = getNodeText(source, cv);
            if (name == null || name.isEmpty()) continue;
            FieldInfo fi = new FieldInfo();
            fi.name = name;
            fi.type = null;
            fi.visibility = "private";
            fi.modifiers = new ArrayList<>();
            fi.annotations = new ArrayList<>();
            result.add(fi);
        }
        // Assignments like @@var = ...
        List<TSNode> classVarAssigns = findAllDescendants(ownerNode, "class_variable_assignment");
        for (TSNode assign : classVarAssigns) {
            if (assign == null || assign.isNull()) continue;
            TSNode nameNode = findFirstChild(assign, "class_variable");
            if (nameNode == null || nameNode.isNull()) continue;
            String name = getNodeText(source, nameNode);
            if (name == null || name.isEmpty()) continue;
            FieldInfo fi = new FieldInfo();
            fi.name = name;
            fi.type = null;
            fi.visibility = "private";
            fi.modifiers = new ArrayList<>();
            fi.annotations = new ArrayList<>();
            result.add(fi);
        }
        return result;
    }

    private void applyVisibilitySymbolLists(String source, TSNode bodyStatement, TypeInfo typeInfo) {
        if (bodyStatement == null || bodyStatement.isNull()) return;
        List<TSNode> calls = new ArrayList<>();
        // Use descendants to capture calls within nested singleton-class blocks
        calls.addAll(findAllDescendants(bodyStatement, "call"));
        calls.addAll(findAllDescendants(bodyStatement, "command"));
        for (TSNode call : calls) {
            if (call == null || call.isNull()) continue;
            String methodName = extractMethodName(source, call);
            if (methodName == null) continue;
            boolean isVis = "private".equals(methodName) || "protected".equals(methodName) || "public".equals(methodName);
            if (!isVis) continue;

            TSNode argList = getArgumentListNode(call);
            if (argList == null || argList.isNull()) continue;

            for (int j = 0; j < argList.getNamedChildCount(); j++) {
                TSNode arg = argList.getNamedChild(j);
                if (arg == null || arg.isNull()) continue;
                String t = arg.getType();
                if ("symbol".equals(t) || "simple_symbol".equals(t) || "identifier".equals(t)) {
                    String name = getNodeText(source, arg);
                    if (name != null) name = normalizeSymbol(name);
                    if (name == null || name.isEmpty()) continue;
                    for (MethodInfo mi : typeInfo.methods) {
                        if (name.equals(mi.name)) {
                            mi.visibility = methodName;
                        }
                    }
                }
            }
        }
    }

    private void extractAliasAnnotations(String source, TSNode bodyStatement, TypeInfo typeInfo) {
        if (bodyStatement == null || bodyStatement.isNull()) return;
        List<TSNode> calls = new ArrayList<>();
        // Use descendants to capture alias/alias_method within nested singleton-class blocks
        calls.addAll(findAllDescendants(bodyStatement, "call"));
        calls.addAll(findAllDescendants(bodyStatement, "command"));
        for (TSNode call : calls) {
            if (call == null || call.isNull()) continue;
            String methodName = null;
            for (int i = 0; i < call.getNamedChildCount(); i++) {
                try {
                    String fieldName = call.getFieldNameForChild(i);
                    if ("operator".equals(fieldName) || "method".equals(fieldName)) {
                        TSNode m = call.getNamedChild(i);
                        if (m != null && !m.isNull()) methodName = getNodeText(source, m);
                        break;
                    }
                } catch (Exception ignored) { }
            }
            if (methodName == null) {
                TSNode first = call.getNamedChild(0);
                if (first != null && ("identifier".equals(first.getType()) || "operator".equals(first.getType()))) {
                    methodName = getNodeText(source, first);
                }
            }
            if (methodName == null) continue;

            boolean isAlias = "alias".equals(methodName) || "alias_method".equals(methodName);
            if (!isAlias) continue;

            TSNode argList = getArgumentListNode(call);
            if (argList == null || argList.isNull()) continue;

            String left = null, right = null;
            if ("alias".equals(methodName)) {
                // alias new old (identifiers or symbols)
                if (argList.getNamedChildCount() >= 2) {
                    left = getNodeText(source, argList.getNamedChild(0));
                    right = getNodeText(source, argList.getNamedChild(1));
                }
            } else {
                // alias_method :new, :old
                if (argList.getNamedChildCount() >= 2) {
                    left = getNodeText(source, argList.getNamedChild(0));
                    right = getNodeText(source, argList.getNamedChild(1));
                }
            }
            if (left != null) left = normalizeSymbol(left);
            if (right != null) right = normalizeSymbol(right);
            if (left != null && !left.isEmpty() && right != null && !right.isEmpty()) {
                typeInfo.annotations.add("@" + methodName + "(" + left + "=" + right + ")");
            }
        }

        // Also handle Ruby's alias statement nodes (not method calls)
        List<TSNode> aliasStmts = findAllDescendants(bodyStatement, "alias");
        for (TSNode aliasNode : aliasStmts) {
            if (aliasNode == null || aliasNode.isNull()) continue;
            // alias new_name old_name (identifiers or symbols)
            String left = null, right = null;
            int namedCount = aliasNode.getNamedChildCount();
            if (namedCount >= 2) {
                TSNode leftNode = aliasNode.getNamedChild(0);
                TSNode rightNode = aliasNode.getNamedChild(1);
                if (leftNode != null && !leftNode.isNull()) left = getNodeText(source, leftNode);
                if (rightNode != null && !rightNode.isNull()) right = getNodeText(source, rightNode);
            }
            if (left != null) left = normalizeSymbol(left);
            if (right != null) right = normalizeSymbol(right);
            if (left != null && !left.isEmpty() && right != null && !right.isEmpty()) {
                typeInfo.annotations.add("@alias(" + left + "=" + right + ")");
            }
        }
    }
    
    private void analyzeMethodsWithVisibility(String source, TSNode bodyStatement, TypeInfo typeInfo) {
        if (bodyStatement == null || bodyStatement.isNull()) {
            return;
        }
        
        // Track current visibility as we traverse the body
        String currentVisibility = "public"; // Ruby methods are public by default
        
        // Process all children in order to track visibility changes
        for (int i = 0; i < bodyStatement.getNamedChildCount(); i++) {
            TSNode child = bodyStatement.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            
            String nodeType = child.getType();
            
            // Check for visibility modifier calls (private, protected, public)
            // These can be either 'call' nodes or 'identifier' nodes
            if ("call".equals(nodeType) || "identifier".equals(nodeType)) {
                String text = getNodeText(source, child);
                if (text != null) {
                    text = text.trim();
                    if ("private".equals(text)) {
                        currentVisibility = "private";
                        continue;
                    } else if ("protected".equals(text)) {
                        currentVisibility = "protected";
                        continue;
                    } else if ("public".equals(text)) {
                        currentVisibility = "public";
                        continue;
                    }
                }
            }
            
            // Process method definitions
            if ("method".equals(nodeType)) {
                MethodInfo methodInfo = analyzeMethod(source, child, typeInfo.name);
                if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                    methodInfo.visibility = currentVisibility;
                    typeInfo.methods.add(methodInfo);
                }
            }
            
            // Process class methods (def self.method_name)
            if ("singleton_method".equals(nodeType)) {
                MethodInfo methodInfo = analyzeMethod(source, child, typeInfo.name);
                if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                    methodInfo.visibility = currentVisibility;
                    methodInfo.modifiers.add("static"); // Class methods are like static methods
                    typeInfo.methods.add(methodInfo);
                }
            }
            
            // Process class << self blocks
            if ("singleton_class".equals(nodeType)) {
                // Find methods inside the singleton_class block
                TSNode singletonBody = findFirstChild(child, "body_statement");
                if (singletonBody != null && !singletonBody.isNull()) {
                    List<TSNode> singletonMethods = findAllChildren(singletonBody, "method");
                    for (TSNode singletonMethod : singletonMethods) {
                        MethodInfo methodInfo = analyzeMethod(source, singletonMethod, typeInfo.name);
                        if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                            methodInfo.visibility = "public"; // Methods in class << self are public
                            methodInfo.modifiers.add("static");
                            typeInfo.methods.add(methodInfo);
                        }
                    }
                }
            }

            // Handle inline visibility wrappers like: private def foo; end  or  protected def bar; end
            // Do NOT traverse into nested classes/modules here to avoid duplicating their methods.
            if (!"method".equals(nodeType)
                    && !"singleton_method".equals(nodeType)
                    && !"singleton_class".equals(nodeType)
                    && !"class".equals(nodeType)
                    && !"module".equals(nodeType)) {
                List<TSNode> innerMethods = findAllDescendants(child, "method");
                if (!innerMethods.isEmpty()) {
                    String childText = getNodeText(source, child);
                    String inlineVis = null;
                    if (childText != null) {
                        String trimmed = childText.trim();
                        if (trimmed.startsWith("private def")) inlineVis = "private";
                        else if (trimmed.startsWith("protected def")) inlineVis = "protected";
                        else if (trimmed.startsWith("public def")) inlineVis = "public";
                    }
                    // Only treat this as an inline visibility wrapper if a visibility keyword was detected
                    if (inlineVis != null) {
                        for (TSNode m : innerMethods) {
                            MethodInfo methodInfo = analyzeMethod(source, m, typeInfo.name);
                            if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                                methodInfo.visibility = inlineVis;
                                typeInfo.methods.add(methodInfo);
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void extractProperties(String source, TSNode bodyStatement, TypeInfo typeInfo) {
        if (bodyStatement == null || bodyStatement.isNull()) {
            return;
        }
        
        // Find attr_reader, attr_writer, attr_accessor calls
        List<TSNode> calls = findAllChildren(bodyStatement, "call");
        for (TSNode call : calls) {
            if (call == null || call.isNull()) continue;
            String methodName = extractMethodName(source, call);
            if (methodName != null) {
                if ("attr_reader".equals(methodName) || "attr_writer".equals(methodName) || "attr_accessor".equals(methodName)) {
                    // Get the arguments (property names as symbols)
                    TSNode argList = getArgumentListNode(call);
                    if (argList != null && !argList.isNull()) {
                        // Process each argument (symbol representing a property name)
                        for (int i = 0; i < argList.getNamedChildCount(); i++) {
                            TSNode arg = argList.getNamedChild(i);
                            if (arg != null && !arg.isNull()) {
                                String propName = getNodeText(source, arg);
                                if (propName != null && propName.startsWith(":")) {
                                    // Remove the leading colon from symbol
                                    propName = propName.substring(1);
                                    
                                    PropertyInfo propInfo = new PropertyInfo();
                                    propInfo.name = propName;
                                    propInfo.type = null; // Ruby doesn't have type declarations
                                    propInfo.visibility = "public"; // attr_* creates public accessors
                                    
                                    // Add appropriate accessors based on attr_* type
                                    if ("attr_reader".equals(methodName) || "attr_accessor".equals(methodName)) {
                                        AccessorInfo getter = new AccessorInfo();
                                        getter.kind = "get";
                                        // Accessor visibility matches property visibility (public), leave null for clarity
                                        getter.visibility = null;
                                        propInfo.accessors.add(getter);
                                    }
                                    if ("attr_writer".equals(methodName) || "attr_accessor".equals(methodName)) {
                                        AccessorInfo setter = new AccessorInfo();
                                        setter.kind = "set";
                                        // Accessor visibility matches property visibility (public), leave null for clarity
                                        setter.visibility = null;
                                        propInfo.accessors.add(setter);
                                    }
                                    
                                    typeInfo.properties.add(propInfo);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void extractMixins(String source, TSNode bodyStatement, TypeInfo typeInfo) {
        if (bodyStatement == null || bodyStatement.isNull()) {
            return;
        }
        
        // Find include and extend calls (these are method calls in Ruby's tree-sitter grammar)
        List<TSNode> calls = findAllChildren(bodyStatement, "call");
        for (TSNode call : calls) {
            if (call == null || call.isNull()) continue;
            
            // Get the method name (should be "include" or "extend")
            TSNode methodNode = null;
            for (int i = 0; i < call.getNamedChildCount(); i++) {
                try {
                    String fieldName = call.getFieldNameForChild(i);
                    if ("operator".equals(fieldName) || "method".equals(fieldName)) {
                        methodNode = call.getNamedChild(i);
                        break;
                    }
                } catch (Exception e) {
                    // Skip if field name cannot be retrieved
                }
            }
            
            if (methodNode == null) {
                // Fallback: first child might be the method name
                methodNode = call.getNamedChild(0);
            }
            
            if (methodNode != null && !methodNode.isNull()) {
                String methodName = getNodeText(source, methodNode);
                if ("include".equals(methodName) || "extend".equals(methodName) || "prepend".equals(methodName)) {
                    // Get the argument (module name)
                    TSNode argList = findFirstChild(call, "argument_list");
                    if (argList != null && !argList.isNull()) {
                        // Get the first argument (constant/module name)
                        TSNode arg = argList.getNamedChild(0);
                        if (arg != null && !arg.isNull()) {
                            String moduleName = getNodeText(source, arg);
                            if (moduleName != null && !moduleName.isEmpty()) {
                                typeInfo.mixins.add(moduleName);
                            }
                        }
                    }
                }
            }
        }
    }
    
    private List<FieldInfo> collectConstants(String source, TSNode classNode) {
        List<FieldInfo> constants = new ArrayList<>();
        if (classNode == null) return constants;
        
        // Find constant assignments in the class body
        TSNode bodyStatement = findFirstChild(classNode, "body_statement");
        if (bodyStatement == null || bodyStatement.isNull()) {
            return constants;
        }
        
        // Look for assignment nodes where the left side is a constant (uppercase identifier)
        List<TSNode> assignments = findAllChildren(bodyStatement, "assignment");
        Set<String> seenConstants = new HashSet<>();
        
        for (TSNode assignment : assignments) {
            if (assignment == null || assignment.isNull()) continue;
            
            // Get the left side (the constant name)
            TSNode leftSide = assignment.getNamedChild(0);
            if (leftSide != null && "constant".equals(leftSide.getType())) {
                String constantName = getNodeText(source, leftSide);
                if (constantName != null && !seenConstants.contains(constantName)) {
                    seenConstants.add(constantName);
                    
                    FieldInfo fieldInfo = new FieldInfo();
                    fieldInfo.name = constantName;
                    fieldInfo.visibility = "public"; // Constants are public in Ruby
                    fieldInfo.modifiers.add("const");
                    constants.add(fieldInfo);
                }
            }
        }
        
        return constants;
    }
    
    private List<FieldInfo> collectInstanceVariables(String source, TSNode classNode) {
        List<FieldInfo> fields = new ArrayList<>();
        if (classNode == null) return fields;

        // Only inspect this class's body, do not traverse into nested classes/modules
        TSNode body = findFirstChild(classNode, "body_statement");
        if (body == null || body.isNull()) return fields;

        Set<String> seenVars = new HashSet<>();
        for (int i = 0; i < body.getNamedChildCount(); i++) {
            TSNode child = body.getNamedChild(i);
            if (child == null || child.isNull()) continue;
            String t = child.getType();
            if ("class".equals(t) || "module".equals(t)) {
                // Skip nested types
                continue;
            }

            // Collect instance variables under this subtree
            if ("instance_variable".equals(t)) {
                String varName = getNodeText(source, child);
                if (varName != null && !seenVars.contains(varName)) {
                    seenVars.add(varName);
                    FieldInfo fi = new FieldInfo();
                    fi.name = varName;
                    fi.visibility = "private";
                    fields.add(fi);
                }
            }

            List<TSNode> vars = findAllDescendants(child, "instance_variable");
            for (TSNode varNode : vars) {
                if (varNode == null || varNode.isNull()) continue;
                String varName = getNodeText(source, varNode);
                if (varName != null && !seenVars.contains(varName)) {
                    seenVars.add(varName);
                    FieldInfo fi = new FieldInfo();
                    fi.name = varName;
                    fi.visibility = "private";
                    fields.add(fi);
                }
            }
        }

        return fields;
    }
    
    private MethodInfo analyzeMethod(String source, TSNode methodNode, String className) {
        MethodInfo methodInfo = new MethodInfo();
        
        if (methodNode == null || methodNode.isNull()) {
            return methodInfo;
        }
        
        // Get method name - can be identifier, setter, or operator
        methodInfo.name = extractName(source, methodNode, "identifier");
        if (methodInfo.name == null) {
            methodInfo.name = extractName(source, methodNode, "setter");
        }
        if (methodInfo.name == null) {
            methodInfo.name = extractName(source, methodNode, "operator");
        }
        
        // Determine visibility based on naming convention and context
        if (methodInfo.name != null) {
            // Ruby uses naming conventions for visibility
            if (methodInfo.name.startsWith("_")) {
                methodInfo.visibility = "private";
            } else {
                methodInfo.visibility = "public";
            }
        }
        
        // Get parameters
        TSNode paramsNode = findFirstChild(methodNode, "method_parameters");
        if (paramsNode != null && !paramsNode.isNull()) {
            extractParameters(source, paramsNode, methodInfo);
        }
        
        // Get method body
        TSNode bodyNode = findFirstChild(methodNode, "body_statement");
        if (bodyNode != null && !bodyNode.isNull()) {
            analyzeMethodBody(source, bodyNode, methodInfo, className);
        }
        
        return methodInfo;
    }
    
    private void extractParameters(String source, TSNode paramsNode, MethodInfo methodInfo) {
        if (paramsNode == null || paramsNode.isNull()) {
            return;
        }
        
        // Ruby parameters can be: identifier, optional_parameter, keyword_parameter, etc.
        for (int i = 0; i < paramsNode.getNamedChildCount(); i++) {
            TSNode paramNode = paramsNode.getNamedChild(i);
            if (paramNode == null || paramNode.isNull()) {
                continue;
            }
            
            String paramType = paramNode.getType();
            String paramName = null;
            
            if ("identifier".equals(paramType)) {
                paramName = getNodeText(source, paramNode);
            } else if ("optional_parameter".equals(paramType) || "keyword_parameter".equals(paramType) || 
                       "splat_parameter".equals(paramType) || "hash_splat_parameter".equals(paramType) ||
                       "block_parameter".equals(paramType)) {
                // For these parameter types, find the identifier child for the name
                TSNode nameNode = findFirstChild(paramNode, "identifier");
                if (nameNode != null && !nameNode.isNull()) {
                    paramName = getNodeText(source, nameNode);
                }
            }
            
            // Capture block parameters with '&' prefix
            if ("block_parameter".equals(paramType) && paramName != null) {
                methodInfo.parameters.add(new Parameter("&" + paramName, null));
            } else if (paramName != null && paramName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                methodInfo.parameters.add(new Parameter(paramName, null));
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
                if (varName != null && !methodInfo.localVariables.contains(varName)
                    && varName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                    methodInfo.localVariables.add(varName);
                }
            }
        }
        
        // Find method calls - include both 'call' and 'method_call' node types
        List<TSNode> callExprs = new ArrayList<>();
        callExprs.addAll(findAllDescendants(bodyNode, "call"));
        callExprs.addAll(findAllDescendants(bodyNode, "method_call"));
        
        for (TSNode callExpr : callExprs) {
            if (callExpr == null || callExpr.isNull()) {
                continue;
            }
            
            String methodName = null;
            String objectName = null;
            String objectType = null;
            
            // Extract method name via helper
            methodName = extractMethodName(source, callExpr);
            
            // Get the receiver (object) and derive objectName/objectType
            TSNode receiverNode = extractReceiverNode(callExpr);
            String[] obj = determineObjectNameAndType(source, receiverNode, className, localTypes);
            objectName = obj[0];
            objectType = obj[1];
            
            // Handle bare method calls (like 'puts') and receiver matching method name
            if (isBareCall(source, methodName, receiverNode, callExpr)) {
                objectName = null;
            }
            
            // Count arguments in the method call
            int parameterCount = 0;
            TSNode argsNode = findArgsNode(callExpr);
            if (argsNode != null && !argsNode.isNull()) {
                parameterCount = countArgumentNodes(argsNode);
            }
            
            // Add the method call
            addMethodCall(methodInfo, methodName, objectType, objectName, parameterCount);
        }
        
        // Sort method calls
        methodInfo.methodCalls.sort(TreeSitterHelper.METHOD_CALL_COMPARATOR);
    }
    
    /**
     * Counts arguments in a method call's arguments node.
     * Rules:
     * - String literals (incl. interpolation) count as 1
     * - Hash/keyword pairs count as 1 total argument
     * - Splat arguments (*args) count as 1
     * - Otherwise each top-level expression counts as 1
     */
    private int countArgumentNodes(TSNode node) {
        String nodeType = node.getType();

        // Strings (including interpolation) are one argument
        if (nodeType.equals("string") || nodeType.equals("string_literal")) {
            return 1;
        }

        // Argument list container
        if (nodeType.equals("argument_list") || nodeType.equals("arguments")) {
            int count = 0;
            boolean inKeywordSeq = false;
            for (int i = 0; i < node.getNamedChildCount(); i++) {
                TSNode child = node.getNamedChild(i);
                String t = child.getType();

                // Skip punctuation-like nodes just in case
                if (t.equals(",") || t.equals("(") || t.equals(")")) {
                    continue;
                }

                // Splat argument: counts as one
                if (t.equals("splat_argument")) {
                    count++;
                    inKeywordSeq = false;
                    continue;
                }

                // Whole hash literal counts as one
                if (t.equals("hash") || t.equals("bare_hash_literal") || t.equals("hash_splat_argument")) {
                    count++;
                    inKeywordSeq = false;
                    continue;
                }

                // Some grammars expose keyword pairs directly; group contiguous as one
                if (t.equals("pair") || t.equals("keyword_argument") || t.equals("label")) {
                    if (!inKeywordSeq) {
                        count++;
                        inKeywordSeq = true;
                    }
                    continue;
                }

                // Strings at top-level
                if (t.equals("string") || t.equals("string_literal")) {
                    count++;
                    inKeywordSeq = false;
                    continue;
                }

                // Default: treat any other expression as one argument
                count++;
                inKeywordSeq = false;
            }
            return count;
        }

        // Fallback: any single expression node outside a container counts as one
        return 1;
    }
    
    /**
     * Determines if a node represents an argument in a method call
     */
    private boolean isArgumentNode(String nodeType) {
        // These node types are considered arguments in Ruby method calls
        return !(nodeType.equals("argument_list") || 
                nodeType.equals("arguments") ||
                nodeType.equals("(") || 
                nodeType.equals(")") ||
                nodeType.equals("{") || 
                nodeType.equals("}") ||
                nodeType.equals("[") || 
                nodeType.equals("]") ||
                nodeType.equals(",") ||
                nodeType.equals("|") ||
                nodeType.equals("`") ||
                nodeType.equals("begin") ||
                nodeType.equals("end") ||
                nodeType.equals("string_content") ||
                nodeType.equals("string") ||
                nodeType.equals("string_literal") ||
                nodeType.equals("\"") ||
                nodeType.equals("'"));
    }
}
