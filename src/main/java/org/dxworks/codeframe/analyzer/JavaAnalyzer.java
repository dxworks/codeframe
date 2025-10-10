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

public class JavaAnalyzer implements LanguageAnalyzer {
    
    @Override
    public FileAnalysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        FileAnalysis analysis = new FileAnalysis();
        analysis.filePath = filePath;
        analysis.language = "java";
        
        // Extract package name
        TSNode packageDecl = findFirstDescendant(rootNode, "package_declaration");
        if (packageDecl != null) {
            TSNode scopedId = findFirstDescendant(packageDecl, "scoped_identifier");
            if (scopedId != null) {
                analysis.packageName = getNodeText(sourceCode, scopedId);
            } else {
                TSNode id = findFirstChild(packageDecl, "identifier");
                if (id != null) {
                    analysis.packageName = getNodeText(sourceCode, id);
                }
            }
        }
        
        // Collect imports
        List<TSNode> importDecls = findAllDescendants(rootNode, "import_declaration");
        for (TSNode imp : importDecls) {
            String text = getNodeText(sourceCode, imp).trim();
            analysis.imports.add(text);
        }
        
        // Find all class declarations and identify nested ones
        List<TSNode> allClasses = findAllDescendants(rootNode, "class_declaration");
        Set<Integer> nestedClassIds = identifyNestedClasses(allClasses);
        
        // Process only top-level classes recursively (they will add nested classes themselves)
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
        
        // Collect field info and types for this class only (not from nested classes)
        Map<String, String> fieldTypes = new HashMap<>();
        List<FieldInfo> fields = collectFieldsFromBody(source, classBody, fieldTypes);
        typeInfo.fields.addAll(fields);
        
        // Analyze methods within this class only (not from nested classes)
        List<TSNode> methods = findAllChildren(classBody, "method_declaration");
        for (TSNode method : methods) {
            MethodInfo methodInfo = analyzeMethod(source, method, typeInfo.name, fieldTypes);
            typeInfo.methods.add(methodInfo);
        }
        
        // Recursively process nested classes - add them to THIS type's types list
        List<TSNode> nestedClasses = findAllChildren(classBody, "class_declaration");
        for (TSNode nested : nestedClasses) {
            analyzeClassRecursivelyInto(source, nested, typeInfo.types);
        }
    }
    
    private TypeInfo analyzeClass(String source, TSNode classDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "class";
        
        // Extract modifiers and visibility
        extractModifiersAndVisibility(source, classDecl, typeInfo.modifiers, typeInfo);
        
        // Extract annotations
        extractAnnotations(source, classDecl, typeInfo.annotations);
        
        // Get class name
        TSNode nameNode = findFirstChild(classDecl, "identifier");
        if (nameNode != null) {
            typeInfo.name = getNodeText(source, nameNode);
        }
        
        // Get superclass
        TSNode superclassNode = findFirstChild(classDecl, "superclass");
        if (superclassNode != null) {
            TSNode typeIdentifier = findFirstDescendant(superclassNode, "type_identifier");
            if (typeIdentifier != null) {
                typeInfo.extendsType = getNodeText(source, typeIdentifier);
            }
        }
        
        // Get implemented interfaces
        TSNode interfacesNode = findFirstChild(classDecl, "super_interfaces");
        if (interfacesNode != null) {
            List<TSNode> typeIdentifiers = findAllDescendants(interfacesNode, "type_identifier");
            for (TSNode typeId : typeIdentifiers) {
                typeInfo.implementsInterfaces.add(getNodeText(source, typeId));
            }
        }
        
        return typeInfo;
    }
    
    private TypeInfo analyzeInterface(String source, TSNode interfaceDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "interface";
        
        // Extract modifiers and visibility
        extractModifiersAndVisibility(source, interfaceDecl, typeInfo.modifiers, typeInfo);
        // Extract annotations
        extractAnnotations(source, interfaceDecl, typeInfo.annotations);

        TSNode nameNode = findFirstChild(interfaceDecl, "identifier");
        if (nameNode != null) {
            typeInfo.name = getNodeText(source, nameNode);
        }
        
        // Interfaces can extend other interfaces
        TSNode extendsNode = findFirstChild(interfaceDecl, "extends_interfaces");
        if (extendsNode != null) {
            List<TSNode> typeIdentifiers = findAllDescendants(extendsNode, "type_identifier");
            for (TSNode typeId : typeIdentifiers) {
                typeInfo.implementsInterfaces.add(getNodeText(source, typeId));
            }
        }

        // Collect interface methods from interface_body
        TSNode interfaceBody = findFirstChild(interfaceDecl, "interface_body");
        if (interfaceBody != null) {
            List<TSNode> methods = findAllChildren(interfaceBody, "method_declaration");
            for (TSNode method : methods) {
                // For interfaces, there are no fields map to resolve types; pass empty map
                MethodInfo methodInfo = analyzeMethod(source, method, typeInfo.name, new HashMap<>());
                typeInfo.methods.add(methodInfo);
            }
        }
        
        return typeInfo;
    }

    private MethodInfo analyzeMethod(String source, TSNode methodDecl, String className, Map<String, String> fieldTypes) {
        MethodInfo methodInfo = new MethodInfo();

        // Extract modifiers and visibility
        extractModifiersAndVisibility(source, methodDecl, methodInfo.modifiers, methodInfo);
        // Extract annotations
        extractAnnotations(source, methodDecl, methodInfo.annotations);

        // Name, return type, parameters
        methodInfo.name = readMethodName(source, methodDecl);
        methodInfo.returnType = readReturnType(source, methodDecl);
        Map<String, String> paramTypes = readParameters(source, methodDecl, methodInfo);

        // Body
        TSNode bodyNode = findFirstChild(methodDecl, "block");
        if (bodyNode != null) {
            analyzeMethodBody(source, bodyNode, methodInfo, className, fieldTypes, paramTypes);
        }

        return methodInfo;
    }

    // --- helpers extracted for readability (no behavior change) ---
    private String readMethodName(String source, TSNode methodDecl) {
        TSNode nameNode = findFirstChild(methodDecl, "identifier");
        return nameNode != null ? getNodeText(source, nameNode) : null;
    }

    private String readReturnType(String source, TSNode methodDecl) {
        int childCount = methodDecl.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = methodDecl.getNamedChild(i);
            String childType = child.getType();
            if ("void_type".equals(childType)) {
                return "void";
            } else if (!"modifiers".equals(childType) && !"identifier".equals(childType)
                    && !"formal_parameters".equals(childType) && !"block".equals(childType)
                    && !"throws".equals(childType)) {
                return extractTypeWithGenerics(source, child, child);
            }
        }
        return null;
    }

    private Map<String, String> readParameters(String source, TSNode methodDecl, MethodInfo methodInfo) {
        Map<String, String> paramTypes = new HashMap<>();
        TSNode paramsNode = findFirstChild(methodDecl, "formal_parameters");
        if (paramsNode == null) return paramTypes;
        List<TSNode> params = findAllChildren(paramsNode, "formal_parameter");
        for (TSNode param : params) {
            String typeName = null;
            TSNode typeNode = param.getNamedChild(0);
            if (typeNode != null && !"identifier".equals(typeNode.getType())) {
                typeName = extractTypeWithGenerics(source, typeNode, param);
            }
            TSNode paramName = findFirstChild(param, "identifier");
            if (paramName != null) {
                String pName = getNodeText(source, paramName);
                if (typeName != null) {
                    paramTypes.put(pName, typeName);
                }
                methodInfo.parameters.add(new Parameter(pName, typeName));
            }
        }
        return paramTypes;
    }

    private void analyzeMethodBody(String source, TSNode bodyNode, MethodInfo methodInfo,
                                   String className, Map<String, String> fieldTypes, Map<String, String> paramTypes) {
        // Local types map (built from declarations in the body)
        Map<String, String> localTypes = new HashMap<>();
        // 1) Locals
        collectLocalVariables(source, bodyNode, methodInfo, localTypes);
        // 2) Method invocations (returns the set of member accesses used as invocations)
        Set<TSNode> memberAccessInInvocations = collectMethodInvocations(source, bodyNode, methodInfo, className, fieldTypes, paramTypes, localTypes);
        // 3) Property accesses (skip those used in invocations)
        collectPropertyAccesses(source, bodyNode, methodInfo, className, localTypes, memberAccessInInvocations);
        // 4) Sort
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

    private void collectLocalVariables(String source, TSNode bodyNode, MethodInfo methodInfo, Map<String, String> localTypes) {
        List<TSNode> localVarDecls = findAllDescendants(bodyNode, "local_variable_declaration");
        for (TSNode varDecl : localVarDecls) {
            String declaredType = null;
            TSNode typeNode = findFirstChild(varDecl, "type");
            if (typeNode == null) typeNode = findFirstDescendant(varDecl, "type_identifier");
            if (typeNode != null) {
                declaredType = extractTypeWithGenerics(source, typeNode, varDecl);
            }
            List<TSNode> declarators = findAllDescendants(varDecl, "variable_declarator");
            for (TSNode declarator : declarators) {
                TSNode varName = findFirstChild(declarator, "identifier");
                if (varName != null) {
                    String name = getNodeText(source, varName);
                    methodInfo.localVariables.add(name);
                    if (declaredType != null) {
                        localTypes.put(name, declaredType);
                    }
                }
            }
        }
    }

    private Set<TSNode> collectMethodInvocations(String source, TSNode bodyNode, MethodInfo methodInfo,
                                                 String className, Map<String, String> fieldTypes,
                                                 Map<String, String> paramTypes, Map<String, String> localTypes) {
        Set<TSNode> memberAccessInInvocations = new HashSet<>();
        List<TSNode> methodInvocations = findAllDescendants(bodyNode, "method_invocation");
        for (TSNode invocation : methodInvocations) {
            int childCount = invocation.getNamedChildCount();
            String methodName = null;
            String objectName = null;
            String objectType = null;

            if (childCount >= 2) {
                TSNode firstChild = invocation.getNamedChild(0);
                TSNode secondChild = invocation.getNamedChild(1);
                if ("identifier".equals(secondChild.getType()) && !"argument_list".equals(firstChild.getType())) {
                    methodName = getNodeText(source, secondChild);
                    if ("identifier".equals(firstChild.getType())) {
                        objectName = getNodeText(source, firstChild);
                        objectType = localTypes.getOrDefault(objectName,
                                paramTypes.getOrDefault(objectName, fieldTypes.get(objectName)));
                    } else if ("field_access".equals(firstChild.getType())) {
                        objectName = getNodeText(source, firstChild);
                        TSNode baseId = findFirstChild(firstChild, "identifier");
                        if (baseId != null) {
                            String baseName = getNodeText(source, baseId);
                            objectType = localTypes.getOrDefault(baseName,
                                    paramTypes.getOrDefault(baseName, fieldTypes.get(baseName)));
                        }
                    } else if ("type_identifier".equals(firstChild.getType()) || "scoped_identifier".equals(firstChild.getType())) {
                        objectType = getNodeText(source, firstChild);
                        objectName = null;
                    } else if ("this".equals(firstChild.getType())) {
                        objectName = "this";
                        objectType = className;
                    }
                } else if ("identifier".equals(firstChild.getType()) && "argument_list".equals(secondChild.getType())) {
                    methodName = getNodeText(source, firstChild);
                }
            } else if (childCount == 1) {
                TSNode firstChild = invocation.getNamedChild(0);
                if ("identifier".equals(firstChild.getType())) {
                    methodName = getNodeText(source, firstChild);
                }
            }

            if (methodName != null) {
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
        return memberAccessInInvocations; // remains empty for Java (kept for symmetry and future use)
    }

    private void collectPropertyAccesses(String source, TSNode bodyNode, MethodInfo methodInfo,
                                         String className, Map<String, String> localTypes, Set<TSNode> memberAccessInInvocations) {
        // Java: count property-like accesses via method_invocation already; no separate member_access node used
        // This method is a placeholder for parity with other analyzers and future expansion.
    }

    private List<FieldInfo> collectFieldsFromBody(String source, TSNode classBody, Map<String, String> fieldTypes) {
        List<FieldInfo> fields = new ArrayList<>();
        if (classBody == null) return fields;
        
        // Only direct field_declaration children of this class body
        List<TSNode> fieldDecls = findAllChildren(classBody, "field_declaration");
        for (TSNode field : fieldDecls) {
            String declaredType = null;
            TSNode typeNode = findFirstChild(field, "type");
            if (typeNode == null) typeNode = findFirstDescendant(field, "type_identifier");
            if (typeNode == null) typeNode = findFirstDescendant(field, "scoped_identifier");
            if (typeNode == null) typeNode = findFirstDescendant(field, "scoped_type_identifier");
            if (typeNode == null) typeNode = findFirstDescendant(field, "integral_type");
            if (typeNode != null) {
                // Use generic-aware extraction for fields
                declaredType = extractTypeWithGenerics(source, typeNode, field);
            }
            
            List<TSNode> declarators = findAllDescendants(field, "variable_declarator");
            for (TSNode declarator : declarators) {
                FieldInfo fi = buildFieldInfo(source, field, declarator, declaredType, fieldTypes);
                if (fi != null) fields.add(fi);
            }
        }
        return fields;
    }

    // Builds FieldInfo for a single variable_declarator inside a field_declaration, updating fieldTypes map.
    private FieldInfo buildFieldInfo(String source, TSNode fieldDecl, TSNode declarator,
                                     String declaredType, Map<String, String> fieldTypes) {
        TSNode varName = findFirstChild(declarator, "identifier");
        if (varName == null) return null;
        String name = getNodeText(source, varName);
        if (declaredType != null) {
            fieldTypes.put(name, declaredType);
        }

        FieldInfo fieldInfo = new FieldInfo();
        fieldInfo.name = name;
        fieldInfo.type = declaredType;

        // Extract modifiers and visibility for this field
        extractModifiersAndVisibility(source, fieldDecl, fieldInfo.modifiers, fieldInfo);
        // Extract annotations for this field
        extractAnnotations(source, fieldDecl, fieldInfo.annotations);
        return fieldInfo;
    }

    // Generic-aware type extraction helper: returns the text of the base type extended to include any trailing
    // type_arguments found within the provided search scope. Falls back to the base node text if no generics found.
    private String extractTypeWithGenerics(String source, TSNode baseTypeNode, TSNode searchScope) {
        if (baseTypeNode == null) return null;
        try {
            TSNode typeArgs = findFirstDescendant(searchScope, "type_arguments");
            if (typeArgs != null) {
                int start = baseTypeNode.getStartByte();
                int end = typeArgs.getEndByte();
                if (start >= 0 && end > start && end <= source.length()) {
                    return source.substring(start, end);
                }
            }
        } catch (Exception ignored) { }
        return getNodeText(source, baseTypeNode);
    }
    
    private void extractModifiersAndVisibility(String source, TSNode node, List<String> modifiers, Object target) {
        TSNode modifiersNode = findFirstChild(node, "modifiers");
        if (modifiersNode != null) {
            // Get the full text of the modifiers node
            String fullModifiersText = getNodeText(source, modifiersNode);
            
            int count = modifiersNode.getNamedChildCount();
            for (int i = 0; i < count; i++) {
                TSNode mod = modifiersNode.getNamedChild(i);
                String type = mod.getType();
                String modText = getNodeText(source, mod);
                
                // Skip annotations - they'll be handled separately
                if ("marker_annotation".equals(type) || "annotation".equals(type)) {
                    continue;
                }
                
                modifiers.add(modText);
                
                // Set visibility if it's a visibility modifier
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
            
            // Fallback: Check if visibility keywords appear in the full modifiers text
            // This handles cases where Tree-sitter doesn't properly separate them as child nodes
            if (target instanceof TypeInfo && ((TypeInfo) target).visibility == null) {
                if (fullModifiersText.contains("public")) {
                    ((TypeInfo) target).visibility = "public";
                    if (!modifiers.contains("public")) modifiers.add("public");
                } else if (fullModifiersText.contains("protected")) {
                    ((TypeInfo) target).visibility = "protected";
                    if (!modifiers.contains("protected")) modifiers.add("protected");
                } else if (fullModifiersText.contains("private")) {
                    ((TypeInfo) target).visibility = "private";
                    if (!modifiers.contains("private")) modifiers.add("private");
                }
            } else if (target instanceof MethodInfo && ((MethodInfo) target).visibility == null) {
                if (fullModifiersText.contains("public")) {
                    ((MethodInfo) target).visibility = "public";
                    if (!modifiers.contains("public")) modifiers.add("public");
                } else if (fullModifiersText.contains("protected")) {
                    ((MethodInfo) target).visibility = "protected";
                    if (!modifiers.contains("protected")) modifiers.add("protected");
                } else if (fullModifiersText.contains("private")) {
                    ((MethodInfo) target).visibility = "private";
                    if (!modifiers.contains("private")) modifiers.add("private");
                }
            } else if (target instanceof FieldInfo && ((FieldInfo) target).visibility == null) {
                if (fullModifiersText.contains("public")) {
                    ((FieldInfo) target).visibility = "public";
                    if (!modifiers.contains("public")) modifiers.add("public");
                } else if (fullModifiersText.contains("protected")) {
                    ((FieldInfo) target).visibility = "protected";
                    if (!modifiers.contains("protected")) modifiers.add("protected");
                } else if (fullModifiersText.contains("private")) {
                    ((FieldInfo) target).visibility = "private";
                    if (!modifiers.contains("private")) modifiers.add("private");
                }
            }
        }
        
        // Do not default visibility; leave null when not explicitly specified
    }
    
    private void extractAnnotations(String source, TSNode node, List<String> annotations) {
        // Annotations are inside the modifiers node in Java Tree-sitter
        TSNode modifiersNode = findFirstChild(node, "modifiers");
        if (modifiersNode != null) {
            int count = modifiersNode.getNamedChildCount();
            for (int i = 0; i < count; i++) {
                TSNode child = modifiersNode.getNamedChild(i);
                String type = child.getType();
                if ("marker_annotation".equals(type) || "annotation".equals(type)) {
                    // Normalize annotation text to a single line for cross-platform JSON stability
                    String raw = getNodeText(source, child);
                    if (raw != null) {
                        String normalized = normalizeInline(raw);
                        annotations.add(normalized);
                    }
                }
            }
        }
    }
}
