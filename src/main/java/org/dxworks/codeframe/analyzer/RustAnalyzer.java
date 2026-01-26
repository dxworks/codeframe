package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.*;
import org.treesitter.TSNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.dxworks.codeframe.analyzer.TreeSitterHelper.*;

public class RustAnalyzer implements LanguageAnalyzer {
    
    @Override
    public FileAnalysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        FileAnalysis analysis = new FileAnalysis();
        analysis.filePath = filePath;
        analysis.language = "rust";
        
        extractUseDeclarations(sourceCode, rootNode, analysis);
        
        List<TSNode> topLevelModules = findAllChildren(rootNode, "mod_item");
        for (TSNode modNode : topLevelModules) {
            TypeInfo moduleInfo = analyzeModule(sourceCode, modNode);
            if (moduleInfo.name != null && !moduleInfo.name.isEmpty()) {
                analysis.types.add(moduleInfo);
            }
        }
        
        List<TSNode> topLevelStructs = findAllChildren(rootNode, "struct_item");
        for (TSNode structNode : topLevelStructs) {
            TypeInfo structInfo = analyzeStruct(sourceCode, structNode);
            if (structInfo.name != null && !structInfo.name.isEmpty()) {
                analysis.types.add(structInfo);
            }
        }
        
        List<TSNode> topLevelEnums = findAllChildren(rootNode, "enum_item");
        for (TSNode enumNode : topLevelEnums) {
            TypeInfo enumInfo = analyzeEnum(sourceCode, enumNode);
            if (enumInfo.name != null && !enumInfo.name.isEmpty()) {
                analysis.types.add(enumInfo);
            }
        }
        
        List<TSNode> topLevelTraits = findAllChildren(rootNode, "trait_item");
        for (TSNode traitNode : topLevelTraits) {
            TypeInfo traitInfo = analyzeTrait(sourceCode, traitNode);
            if (traitInfo.name != null && !traitInfo.name.isEmpty()) {
                analysis.types.add(traitInfo);
            }
        }
        
        List<TSNode> topLevelImpls = findAllChildren(rootNode, "impl_item");
        for (TSNode implNode : topLevelImpls) {
            TypeInfo implType = analyzeImpl(sourceCode, implNode);
            if (implType != null) {
                analysis.types.add(implType);
            }
        }
        
        List<TSNode> topLevelFunctions = findAllChildren(rootNode, "function_item");
        for (TSNode funcNode : topLevelFunctions) {
            MethodInfo methodInfo = analyzeFunction(sourceCode, funcNode, null);
            if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                analysis.methods.add(methodInfo);
            }
        }
        
        extractTopLevelConstants(sourceCode, rootNode, analysis);
        extractTopLevelStatics(sourceCode, rootNode, analysis);
        
        return analysis;
    }
    
    private void extractUseDeclarations(String source, TSNode rootNode, FileAnalysis analysis) {
        List<TSNode> useDecls = findAllDescendants(rootNode, "use_declaration");
        for (TSNode useDecl : useDecls) {
            if (useDecl == null || useDecl.isNull()) continue;
            String text = getNodeText(source, useDecl).trim();
            if (text != null && !text.isEmpty()) {
                analysis.imports.add(text);
            }
        }
    }
    
    private void extractTopLevelConstants(String source, TSNode rootNode, FileAnalysis analysis) {
        List<TSNode> constItems = findAllChildren(rootNode, "const_item");
        for (TSNode constNode : constItems) {
            FieldInfo field = extractConstant(source, constNode);
            if (field != null) {
                analysis.fields.add(field);
            }
        }
    }
    
    private void extractTopLevelStatics(String source, TSNode rootNode, FileAnalysis analysis) {
        List<TSNode> staticItems = findAllChildren(rootNode, "static_item");
        for (TSNode staticNode : staticItems) {
            FieldInfo field = extractStatic(source, staticNode);
            if (field != null) {
                analysis.fields.add(field);
            }
        }
    }
    
    private FieldInfo extractConstant(String source, TSNode constNode) {
        if (constNode == null || constNode.isNull()) return null;
        
        FieldInfo field = new FieldInfo();
        field.modifiers.add("const");
        
        field.name = extractName(source, constNode, "identifier");
        
        field.type = extractTypeAnnotation(source, constNode);
        extractVisibility(source, constNode, field);
        
        return field.name != null ? field : null;
    }
    
    private FieldInfo extractStatic(String source, TSNode staticNode) {
        if (staticNode == null || staticNode.isNull()) return null;
        
        FieldInfo field = new FieldInfo();
        field.modifiers.add("static");
        
        TSNode mutNode = findFirstChild(staticNode, "mutable_specifier");
        if (mutNode != null) {
            field.modifiers.add("mut");
        }
        
        field.name = extractName(source, staticNode, "identifier");
        
        field.type = extractTypeAnnotation(source, staticNode);
        extractVisibility(source, staticNode, field);
        
        return field.name != null ? field : null;
    }
    
    private TypeInfo analyzeStruct(String source, TSNode structNode) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "struct";
        
        typeInfo.name = extractTypeNameWithGenerics(source, structNode);
        
        extractVisibility(source, structNode, typeInfo);
        extractAttributes(source, structNode, typeInfo);
        
        TSNode body = findFirstChild(structNode, "field_declaration_list");
        if (body != null) {
            extractStructFields(source, body, typeInfo);
        }
        
        return typeInfo;
    }
    
    private void extractStructFields(String source, TSNode body, TypeInfo typeInfo) {
        List<TSNode> fields = findAllChildren(body, "field_declaration");
        for (TSNode fieldNode : fields) {
            FieldInfo field = new FieldInfo();
            
            TSNode nameNode = findFirstChild(fieldNode, "field_identifier");
            if (nameNode != null) {
                field.name = getNodeText(source, nameNode);
            }
            
            field.type = extractTypeAnnotation(source, fieldNode);
            extractVisibility(source, fieldNode, field);
            extractAttributes(source, fieldNode, field);
            
            if (field.name != null) {
                typeInfo.fields.add(field);
            }
        }
    }
    
    private TypeInfo analyzeEnum(String source, TSNode enumNode) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "enum";
        
        typeInfo.name = extractTypeNameWithGenerics(source, enumNode);
        
        extractVisibility(source, enumNode, typeInfo);
        extractAttributes(source, enumNode, typeInfo);
        
        return typeInfo;
    }
    
    private TypeInfo analyzeTrait(String source, TSNode traitNode) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "trait";
        
        typeInfo.name = extractTypeNameWithGenerics(source, traitNode);
        
        extractVisibility(source, traitNode, typeInfo);
        extractAttributes(source, traitNode, typeInfo);
        
        TSNode body = findFirstChild(traitNode, "declaration_list");
        if (body != null) {
            List<TSNode> methods = findAllChildren(body, "function_item");
            for (TSNode methodNode : methods) {
                MethodInfo methodInfo = analyzeFunction(source, methodNode, typeInfo.name);
                if (methodInfo.name != null) {
                    typeInfo.methods.add(methodInfo);
                }
            }
            
            List<TSNode> funcSigs = findAllChildren(body, "function_signature_item");
            for (TSNode sigNode : funcSigs) {
                MethodInfo methodInfo = analyzeFunctionSignature(source, sigNode, typeInfo.name);
                if (methodInfo.name != null) {
                    typeInfo.methods.add(methodInfo);
                }
            }
        }
        
        return typeInfo;
    }
    
    private TypeInfo analyzeModule(String source, TSNode modNode) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "mod";
        
        typeInfo.name = extractName(source, modNode, "identifier");
        
        extractVisibility(source, modNode, typeInfo);
        extractAttributes(source, modNode, typeInfo);
        
        TSNode body = findFirstChild(modNode, "declaration_list");
        if (body != null) {
            List<TSNode> nestedMods = findAllChildren(body, "mod_item");
            for (TSNode nestedMod : nestedMods) {
                TypeInfo nestedModInfo = analyzeModule(source, nestedMod);
                if (nestedModInfo.name != null && !nestedModInfo.name.isEmpty()) {
                    typeInfo.types.add(nestedModInfo);
                }
            }
            
            List<TSNode> structs = findAllChildren(body, "struct_item");
            for (TSNode structNode : structs) {
                TypeInfo structInfo = analyzeStruct(source, structNode);
                if (structInfo.name != null && !structInfo.name.isEmpty()) {
                    typeInfo.types.add(structInfo);
                }
            }
            
            List<TSNode> enums = findAllChildren(body, "enum_item");
            for (TSNode enumNode : enums) {
                TypeInfo enumInfo = analyzeEnum(source, enumNode);
                if (enumInfo.name != null && !enumInfo.name.isEmpty()) {
                    typeInfo.types.add(enumInfo);
                }
            }
            
            List<TSNode> traits = findAllChildren(body, "trait_item");
            for (TSNode traitNode : traits) {
                TypeInfo traitInfo = analyzeTrait(source, traitNode);
                if (traitInfo.name != null && !traitInfo.name.isEmpty()) {
                    typeInfo.types.add(traitInfo);
                }
            }
            
            List<TSNode> impls = findAllChildren(body, "impl_item");
            for (TSNode implNode : impls) {
                TypeInfo implType = analyzeImpl(source, implNode);
                if (implType != null) {
                    typeInfo.types.add(implType);
                }
            }
            
            List<TSNode> functions = findAllChildren(body, "function_item");
            for (TSNode funcNode : functions) {
                MethodInfo methodInfo = analyzeFunction(source, funcNode, null);
                if (methodInfo.name != null && !methodInfo.name.isEmpty()) {
                    typeInfo.methods.add(methodInfo);
                }
            }
            
            List<TSNode> constants = findAllChildren(body, "const_item");
            for (TSNode constNode : constants) {
                FieldInfo field = extractConstant(source, constNode);
                if (field != null) {
                    typeInfo.fields.add(field);
                }
            }
            
            List<TSNode> statics = findAllChildren(body, "static_item");
            for (TSNode staticNode : statics) {
                FieldInfo field = extractStatic(source, staticNode);
                if (field != null) {
                    typeInfo.fields.add(field);
                }
            }
        }
        
        return typeInfo;
    }
    
    private TypeInfo analyzeImpl(String source, TSNode implNode) {
        TSNode typeNode = getChildByFieldName(implNode, "type");
        if (typeNode == null) return null;
        
        String implTypeName = getNodeText(source, typeNode);
        if (implTypeName == null) return null;
        
        TypeInfo implType = new TypeInfo();
        implType.kind = "impl";
        implType.name = implTypeName;
        
        addImplMethods(source, implNode, implType, implTypeName);
        
        return implType;
    }
    
    private void addImplMethods(String source, TSNode implNode, TypeInfo existingType, String implTypeName) {
        TSNode traitTypeNode = getChildByFieldName(implNode, "trait");
        if (traitTypeNode != null) {
            String traitName = extractTraitName(source, traitTypeNode);
            if (traitName != null && !existingType.implementsInterfaces.contains(traitName)) {
                existingType.implementsInterfaces.add(traitName);
            }
        }
        
        TSNode body = findFirstChild(implNode, "declaration_list");
        if (body != null) {
            List<TSNode> methods = findAllChildren(body, "function_item");
            for (TSNode methodNode : methods) {
                MethodInfo methodInfo = analyzeFunction(source, methodNode, implTypeName);
                if (methodInfo.name != null) {
                    existingType.methods.add(methodInfo);
                }
            }
            
            List<TSNode> constItems = findAllChildren(body, "const_item");
            for (TSNode constNode : constItems) {
                FieldInfo field = extractConstant(source, constNode);
                if (field != null) {
                    existingType.fields.add(field);
                }
            }
        }
    }
    
    private MethodInfo analyzeFunction(String source, TSNode funcNode, String typeName) {
        MethodInfo methodInfo = new MethodInfo();
        
        methodInfo.name = extractName(source, funcNode, "identifier");
        
        extractVisibility(source, funcNode, methodInfo);
        extractAttributes(source, funcNode, methodInfo);
        
        TSNode params = findFirstChild(funcNode, "parameters");
        if (params != null) {
            extractParameters(source, params, methodInfo);
        }
        
        methodInfo.returnType = extractReturnType(source, funcNode);
        
        TSNode body = findFirstChild(funcNode, "block");
        if (body != null) {
            analyzeMethodBody(source, body, methodInfo, typeName);
        }
        
        return methodInfo;
    }
    
    private MethodInfo analyzeFunctionSignature(String source, TSNode sigNode, String typeName) {
        MethodInfo methodInfo = new MethodInfo();
        
        methodInfo.name = extractName(source, sigNode, "identifier");
        
        extractVisibility(source, sigNode, methodInfo);
        extractAttributes(source, sigNode, methodInfo);
        
        TSNode params = findFirstChild(sigNode, "parameters");
        if (params != null) {
            extractParameters(source, params, methodInfo);
        }
        
        methodInfo.returnType = extractReturnType(source, sigNode);
        
        return methodInfo;
    }
    
    private void extractParameters(String source, TSNode params, MethodInfo methodInfo) {
        List<TSNode> paramNodes = findAllChildren(params, "parameter");
        for (TSNode paramNode : paramNodes) {
            TSNode patternNode = findFirstChild(paramNode, "identifier");
            String paramName = null;
            if (patternNode != null) {
                paramName = getNodeText(source, patternNode);
            } else {
                TSNode selfParam = findFirstChild(paramNode, "self");
                if (selfParam != null) {
                    paramName = "self";
                }
            }
            
            String paramType = extractTypeAnnotation(source, paramNode);
            
            if (paramName != null) {
                methodInfo.parameters.add(new Parameter(paramName, paramType));
            }
        }
        
        List<TSNode> selfParams = findAllChildren(params, "self_parameter");
        for (TSNode selfParam : selfParams) {
            String paramType = extractTypeAnnotation(source, selfParam);
            methodInfo.parameters.add(0, new Parameter("self", paramType));
        }
    }
    
    private void analyzeMethodBody(String source, TSNode body, MethodInfo methodInfo, String typeName) {
        Map<String, String> localTypes = new HashMap<>();
        
        extractLocalVariables(source, body, methodInfo, localTypes);
        extractMethodCalls(source, body, methodInfo, localTypes);
        
        methodInfo.methodCalls.sort(METHOD_CALL_COMPARATOR);
    }
    
    private void extractLocalVariables(String source, TSNode body, MethodInfo methodInfo, Map<String, String> localTypes) {
        List<TSNode> letDecls = findAllDescendants(body, "let_declaration");
        for (TSNode letDecl : letDecls) {
            TSNode pattern = findFirstChild(letDecl, "identifier");
            if (pattern != null) {
                String varName = getNodeText(source, pattern);
                if (varName != null) {
                    methodInfo.localVariables.add(varName);
                    
                    TSNode typeNode = findFirstChild(letDecl, "type_identifier");
                    if (typeNode != null) {
                        localTypes.put(varName, getNodeText(source, typeNode));
                    }
                }
            }
        }
    }
    
    private void extractMethodCalls(String source, TSNode body, MethodInfo methodInfo, Map<String, String> localTypes) {
        List<TSNode> callExprs = findAllDescendants(body, "call_expression");
        for (TSNode callExpr : callExprs) {
            String methodName = null;
            String objectName = null;
            String objectType = null;
            
            TSNode function = callExpr.getNamedChild(0);
            if (function == null || function.isNull()) continue;
            
            String funcType = function.getType();
            
            if ("identifier".equals(funcType)) {
                methodName = getNodeText(source, function);
            } else if ("field_expression".equals(funcType)) {
                TSNode field = findFirstChild(function, "field_identifier");
                if (field != null) {
                    methodName = getNodeText(source, field);
                }
                
                TSNode obj = function.getNamedChild(0);
                if (obj != null) {
                    if ("identifier".equals(obj.getType())) {
                        objectName = getNodeText(source, obj);
                        objectType = localTypes.get(objectName);
                        if ("self".equals(objectName)) {
                            objectType = null;
                        }
                    } else if ("self".equals(obj.getType())) {
                        objectName = "self";
                    }
                    // For complex expressions (parenthesized, binary ops, etc.), 
                    // don't extract objectName - leave as null
                }
            } else if ("scoped_identifier".equals(funcType)) {
                // For Config::new or std::collections::HashMap::new
                // Last child is method name, everything before is the path/type
                int childCount = function.getNamedChildCount();
                if (childCount >= 2) {
                    TSNode lastChild = function.getNamedChild(childCount - 1);
                    if (lastChild != null && "identifier".equals(lastChild.getType())) {
                        methodName = getNodeText(source, lastChild);
                    }
                    
                    // For nested paths, get all children except the last one
                    // This handles both Config::new and std::collections::HashMap::new
                    if (childCount == 2) {
                        // Simple case: Config::new
                        TSNode firstChild = function.getNamedChild(0);
                        if (firstChild != null) {
                            objectType = getNodeText(source, firstChild);
                        }
                    } else {
                        // Nested case: get text from start to just before last identifier
                        // This captures the full path like "std::collections::HashMap"
                        TSNode lastBeforeMethod = function.getNamedChild(childCount - 2);
                        if (lastBeforeMethod != null) {
                            int startByte = function.getStartByte();
                            int endByte = lastBeforeMethod.getEndByte();
                            objectType = source.substring(startByte, endByte);
                        }
                    }
                }
            }
            
            if (methodName != null) {
                TSNode args = findFirstChild(callExpr, "arguments");
                int paramCount = 0;
                if (args != null) {
                    paramCount = args.getNamedChildCount();
                }
                
                collectMethodCall(methodInfo, methodName, objectType, objectName, paramCount);
            }
        }
        
        List<TSNode> macroInvocations = findAllDescendants(body, "macro_invocation");
        for (TSNode macroNode : macroInvocations) {
            TSNode macroName = findFirstChild(macroNode, "identifier");
            if (macroName != null) {
                String name = getNodeText(source, macroName);
                if (name != null) {
                    TSNode tokenTree = findFirstChild(macroNode, "token_tree");
                    int paramCount = 0;
                    if (tokenTree != null) {
                        paramCount = countMacroArguments(source, tokenTree);
                    }
                    collectMethodCall(methodInfo, name + "!", null, null, paramCount);
                }
            }
        }
    }
    
    private void extractVisibility(String source, TSNode node, Object target) {
        TSNode visNode = findFirstChild(node, "visibility_modifier");
        if (visNode != null) {
            String visText = getNodeText(source, visNode);
            if (visText != null) {
                if (target instanceof TypeInfo) {
                    ((TypeInfo) target).visibility = visText;
                } else if (target instanceof MethodInfo) {
                    ((MethodInfo) target).visibility = visText;
                    ((MethodInfo) target).modifiers.add(visText);
                } else if (target instanceof FieldInfo) {
                    ((FieldInfo) target).visibility = visText;
                    ((FieldInfo) target).modifiers.add(visText);
                }
            }
        }
    }
    
    private void extractAttributes(String source, TSNode node, Object target) {
        List<String> annotations = null;
        if (target instanceof TypeInfo) {
            annotations = ((TypeInfo) target).annotations;
        } else if (target instanceof MethodInfo) {
            annotations = ((MethodInfo) target).annotations;
        } else if (target instanceof FieldInfo) {
            annotations = ((FieldInfo) target).annotations;
        }
        
        if (annotations == null) return;
        
        TSNode parent = node.getParent();
        if (parent != null && !parent.isNull()) {
            int nodeIndex = -1;
            int nodeStartByte = node.getStartByte();
            
            // Find node index by comparing byte positions
            for (int i = 0; i < parent.getNamedChildCount(); i++) {
                TSNode child = parent.getNamedChild(i);
                if (child != null && child.getStartByte() == nodeStartByte) {
                    nodeIndex = i;
                    break;
                }
            }
            
            if (nodeIndex > 0) {
                // Look backwards for attribute_item nodes
                for (int i = nodeIndex - 1; i >= 0; i--) {
                    TSNode prevSibling = parent.getNamedChild(i);
                    if (prevSibling != null && "attribute_item".equals(prevSibling.getType())) {
                        String attrText = getNodeText(source, prevSibling);
                        if (attrText != null) {
                            annotations.add(normalizeInline(attrText));
                        }
                    } else {
                        break;
                    }
                }
            }
        }
    }
    
    private String extractReturnType(String source, TSNode funcNode) {
        for (int i = 0; i < funcNode.getNamedChildCount(); i++) {
            TSNode child = funcNode.getNamedChild(i);
            if (child != null && !child.isNull() && isRustTypeNode(child.getType())) {
                TSNode params = findFirstChild(funcNode, "parameters");
                if (params != null && child.getStartByte() > params.getEndByte()) {
                    return getNodeText(source, child);
                }
            }
        }
        return null;
    }
    
    private String extractTypeAnnotation(String source, TSNode node) {
        if (node == null || node.isNull()) return null;
        
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            TSNode child = node.getNamedChild(i);
            if (child != null && !child.isNull() && isRustTypeNode(child.getType())) {
                return getNodeText(source, child);
            }
        }
        return null;
    }
    
    private boolean isRustTypeNode(String nodeType) {
        return "type_identifier".equals(nodeType) 
            || "generic_type".equals(nodeType)
            || "reference_type".equals(nodeType) 
            || "primitive_type".equals(nodeType)
            || "array_type".equals(nodeType) 
            || "tuple_type".equals(nodeType)
            || "function_type".equals(nodeType) 
            || "pointer_type".equals(nodeType);
    }
    
    private String extractTypeNameWithGenerics(String source, TSNode node) {
        TSNode nameNode = findFirstChild(node, "type_identifier");
        if (nameNode != null) {
            String baseName = getNodeText(source, nameNode);
            TSNode typeParams = findFirstChild(node, "type_parameters");
            if (typeParams != null) {
                return baseName + getNodeText(source, typeParams);
            }
            return baseName;
        }
        return null;
    }
    
    private String extractTraitName(String source, TSNode traitTypeNode) {
        if ("type_identifier".equals(traitTypeNode.getType())) {
            return getNodeText(source, traitTypeNode);
        }
        TSNode traitIdent = findFirstChild(traitTypeNode, "type_identifier");
        return traitIdent != null ? getNodeText(source, traitIdent) : null;
    }
    
    private int countMacroArguments(String source, TSNode tokenTree) {
        if (tokenTree == null || tokenTree.isNull()) return 0;
        
        String content = getNodeText(source, tokenTree);
        if (content == null || content.length() < 2) return 0;
        
        content = content.substring(1, content.length() - 1).trim();
        if (content.isEmpty()) return 0;
        
        int count = 1;
        int depth = 0;
        boolean inString = false;
        char prevChar = ' ';
        
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            
            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            } else if (!inString) {
                if (c == '(' || c == '[' || c == '{') {
                    depth++;
                } else if (c == ')' || c == ']' || c == '}') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    count++;
                }
            }
            prevChar = c;
        }
        
        return count;
    }
}
