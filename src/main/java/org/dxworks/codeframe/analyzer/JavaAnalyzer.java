package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.*;
import org.treesitter.TSNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.dxworks.codeframe.analyzer.TreeSitterHelper.*;

public class JavaAnalyzer implements LanguageAnalyzer {
    // Node type constants
    private static final String NT_TYPE = "type";
    private static final String NT_TYPE_IDENTIFIER = "type_identifier";
    private static final String NT_SCOPED_IDENTIFIER = "scoped_identifier";
    private static final String NT_SCOPED_TYPE_IDENTIFIER = "scoped_type_identifier";
    private static final String NT_INTEGRAL_TYPE = "integral_type";
    private static final String NT_FLOATING_POINT_TYPE = "floating_point_type";
    private static final String NT_BOOLEAN_TYPE = "boolean_type";
    private static final String NT_PRIMITIVE_TYPE = "primitive_type";
    private static final String NT_ARRAY_TYPE = "array_type";
    
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
        Set<Integer> nestedClassIds = identifyNestedNodes(allClasses, "class_body", "class_declaration");
        
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
        
        // Find all enum declarations
        List<TSNode> enumDecls = findAllDescendants(rootNode, "enum_declaration");
        for (TSNode enumDecl : enumDecls) {
            TypeInfo enumInfo = analyzeEnum(sourceCode, enumDecl);
            analysis.types.add(enumInfo);
        }

        // Find all record declarations (Java 14+)
        List<TSNode> recordDecls = findAllDescendants(rootNode, "record_declaration");
        for (TSNode recordDecl : recordDecls) {
            TypeInfo recordInfo = analyzeRecord(sourceCode, recordDecl);
            analysis.types.add(recordInfo);
        }
        
        return analysis;
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
        
        // Analyze constructors within this class only
        List<TSNode> constructors = findAllChildren(classBody, "constructor_declaration");
        for (TSNode constructor : constructors) {
            MethodInfo constructorInfo = analyzeConstructor(source, constructor, typeInfo.name, fieldTypes);
            typeInfo.methods.add(constructorInfo);
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
        
        // Get class name (include generic type parameters if present)
        typeInfo.name = extractNameWithTypeParams(source, classDecl);
        
        // Get superclass
        TSNode superclassNode = findFirstChild(classDecl, "superclass");
        if (superclassNode != null) {
            TSNode typeIdentifier = findFirstDescendant(superclassNode, "type_identifier");
            if (typeIdentifier != null) {
                typeInfo.extendsType = getNodeText(source, typeIdentifier);
            }
        }
        
        // Get implemented interfaces
        extractInterfaces(source, classDecl, "super_interfaces", typeInfo.implementsInterfaces);
        
        return typeInfo;
    }

    private TypeInfo analyzeRecord(String source, TSNode recordDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "record";

        // Extract modifiers and visibility
        extractModifiersAndVisibility(source, recordDecl, typeInfo.modifiers, typeInfo);
        // Extract annotations
        extractAnnotations(source, recordDecl, typeInfo.annotations);

        // Record name (include generic type parameters if present)
        typeInfo.name = extractNameWithTypeParams(source, recordDecl);

        // Records can implement interfaces
        extractInterfaces(source, recordDecl, "super_interfaces", typeInfo.implementsInterfaces);

        // Record components as fields
        List<TSNode> components = findAllDescendants(recordDecl, "record_component");
        Map<String, String> fieldTypes = new HashMap<>();
        for (TSNode comp : components) {
            TSNode compType = comp.getNamedChild(0);
            TSNode compName = findFirstChild(comp, "identifier");
            if (compName != null && compType != null) {
                String name = getNodeText(source, compName);
                String typeName = extractTypeWithGenerics(source, compType, comp);
                FieldInfo fi = new FieldInfo();
                fi.name = name;
                fi.type = typeName;
                // Components are implicitly final
                fi.modifiers.add("final");
                // No explicit visibility by default
                typeInfo.fields.add(fi);
                fieldTypes.put(name, typeName);
            }
        }

        // Analyze record body for additional members
        TSNode recordBody = findFirstChild(recordDecl, "class_body");
        if (recordBody == null) {
            // Some grammars may use record_body; fallback to any block-like child named class_body
            recordBody = findFirstChild(recordDecl, "record_body");
        }
        if (recordBody != null) {
            // Regular fields
            collectFieldsFromBodyInto(source, recordBody, typeInfo.fields, fieldTypes);

            // Methods and constructors
            collectMethodsAndConstructors(source, recordBody, typeInfo, fieldTypes);

            // Compact constructor (no parameter list)
            List<TSNode> compactCtors = findAllDescendants(recordBody, "compact_constructor_declaration");
            for (TSNode compact : compactCtors) {
                MethodInfo mi = new MethodInfo();
                extractModifiersAndVisibility(source, compact, mi.modifiers, mi);
                extractAnnotations(source, compact, mi.annotations);
                mi.name = typeInfo.name;
                mi.returnType = null;
                TSNode body = findFirstChild(compact, "block");
                if (body != null) {
                    analyzeMethodBody(source, body, mi, typeInfo.name, fieldTypes, new HashMap<>());
                }
                typeInfo.methods.add(mi);
            }
        }

        return typeInfo;
    }

    private TypeInfo analyzeEnum(String source, TSNode enumDecl) {
        TypeInfo typeInfo = new TypeInfo();
        typeInfo.kind = "enum";
        
        // Extract modifiers and visibility
        extractModifiersAndVisibility(source, enumDecl, typeInfo.modifiers, typeInfo);
        // Extract annotations
        extractAnnotations(source, enumDecl, typeInfo.annotations);

        // Enum name (include generic type parameters if present)
        typeInfo.name = extractNameWithTypeParams(source, enumDecl);

        // Enums can implement interfaces
        extractInterfaces(source, enumDecl, "super_interfaces", typeInfo.implementsInterfaces);

        // Analyze enum body for fields, methods, and constructors
        TSNode enumBody = findFirstChild(enumDecl, "enum_body");
        if (enumBody != null) {
            Map<String, String> fieldTypes = new HashMap<>();

            // Fields declared in enum body
            collectFieldsFromBodyInto(source, enumBody, typeInfo.fields, fieldTypes);

            // Methods and constructors
            collectMethodsAndConstructors(source, enumBody, typeInfo, fieldTypes);
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

        typeInfo.name = extractNameWithTypeParams(source, interfaceDecl);
        
        // Interfaces can extend other interfaces
        extractInterfaces(source, interfaceDecl, "extends_interfaces", typeInfo.implementsInterfaces);

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
    
    private MethodInfo analyzeConstructor(String source, TSNode constructorDecl, String className, Map<String, String> fieldTypes) {
        MethodInfo methodInfo = new MethodInfo();

        // Extract modifiers and visibility
        extractModifiersAndVisibility(source, constructorDecl, methodInfo.modifiers, methodInfo);
        // Extract annotations
        extractAnnotations(source, constructorDecl, methodInfo.annotations);

        // Constructor name is the class name, no return type
        methodInfo.name = className;
        methodInfo.returnType = null;
        Map<String, String> paramTypes = readParameters(source, constructorDecl, methodInfo);

        // Body
        TSNode bodyNode = findFirstChild(constructorDecl, "constructor_body");
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
                    && !"throws".equals(childType) && !"type_parameters".equals(childType)) {
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
            // Use a robust finder to skip modifiers (e.g., 'final') and get the actual type node
            TSNode typeNode = findTypeNode(param);
            if (typeNode != null) {
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
        // 2) Method invocations
        collectMethodInvocations(source, bodyNode, methodInfo, className, fieldTypes, paramTypes, localTypes);
        // 3) Sort
        methodInfo.methodCalls.sort(TreeSitterHelper.METHOD_CALL_COMPARATOR);
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

    private void collectMethodInvocations(String source, TSNode bodyNode, MethodInfo methodInfo,
                                          String className, Map<String, String> fieldTypes,
                                          Map<String, String> paramTypes, Map<String, String> localTypes) {
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
                // Count parameters in the invocation
                int paramCount = countInvocationParameters(invocation);
                boolean found = false;
                for (MethodCall existingCall : methodInfo.methodCalls) {
                    if (existingCall.matches(methodName, objectType, objectName, paramCount)) {
                        existingCall.callCount++;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    methodInfo.methodCalls.add(new MethodCall(methodName, objectType, objectName, paramCount));
                }
            }
        }
    }

    private int countInvocationParameters(TSNode invocation) {
        // Find the argument list node in the method_invocation
        TSNode argList = getArgumentListNode(invocation);
        if (argList == null) {
            return 0;
        }
        // Count direct children that are not punctuation (commas, parens)
        int count = 0;
        for (int i = 0; i < argList.getNamedChildCount(); i++) {
            count++;
        }
        return count;
    }

    private List<FieldInfo> collectFieldsFromBody(String source, TSNode classBody, Map<String, String> fieldTypes) {
        List<FieldInfo> fields = new ArrayList<>();
        collectFieldsFromBodyInto(source, classBody, fields, fieldTypes, false);
        return fields;
    }
    
    // Overload for enum/record bodies that need to search nested declarations
    private void collectFieldsFromBodyInto(String source, TSNode classBody, List<FieldInfo> targetList, Map<String, String> fieldTypes) {
        collectFieldsFromBodyInto(source, classBody, targetList, fieldTypes, true);
    }
    
    private void collectFieldsFromBodyInto(String source, TSNode classBody, List<FieldInfo> targetList, Map<String, String> fieldTypes, boolean includeNested) {
        if (classBody == null) return;
        
        // includeNested=false: use findAllChildren (direct children only, avoids nested class fields)
        // includeNested=true: use findAllDescendants (all descendants, needed for enum/record bodies)
        List<TSNode> fieldDecls = includeNested 
            ? findAllDescendants(classBody, "field_declaration")
            : findAllChildren(classBody, "field_declaration");
        for (TSNode field : fieldDecls) {
            String declaredType = null;
            TSNode typeNode = findTypeNode(field);
            if (typeNode != null) {
                declaredType = extractTypeWithGenerics(source, typeNode, field);
            }
            
            List<TSNode> declarators = findAllDescendants(field, "variable_declarator");
            for (TSNode declarator : declarators) {
                FieldInfo fi = buildFieldInfo(source, field, declarator, declaredType, fieldTypes);
                if (fi != null) targetList.add(fi);
            }
        }
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
            applyVisibilityFallback(target, fullModifiersText, modifiers);

            // Add non-visibility modifiers that may not be exposed as named children
            String[] knownNonVisibility = new String[] {
                "final", "static", "abstract", "synchronized", "native", "transient", "volatile", "strictfp", "default"
            };
            String full = fullModifiersText == null ? "" : fullModifiersText;
            for (String kw : knownNonVisibility) {
                if (full.contains(kw) && !modifiers.contains(kw)) {
                    modifiers.add(kw);
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
    
    // Helper: Extract name with optional type parameters
    private String extractNameWithTypeParams(String source, TSNode node) {
        TSNode nameNode = findFirstChild(node, "identifier");
        if (nameNode == null) return null;
        String baseName = getNodeText(source, nameNode);
        TSNode typeParams = findFirstChild(node, "type_parameters");
        if (typeParams != null) {
            return baseName + getNodeText(source, typeParams);
        }
        return baseName;
    }
    
    // Helper: Extract interfaces/extends into target list
    private void extractInterfaces(String source, TSNode node, String childNodeType, List<String> targetList) {
        TSNode interfacesNode = findFirstChild(node, childNodeType);
        if (interfacesNode != null) {
            List<TSNode> typeIdentifiers = findAllDescendants(interfacesNode, "type_identifier");
            for (TSNode typeId : typeIdentifiers) {
                targetList.add(getNodeText(source, typeId));
            }
        }
    }
    
    // Helper: Collect methods and constructors from a body node
    private void collectMethodsAndConstructors(String source, TSNode bodyNode, TypeInfo typeInfo, Map<String, String> fieldTypes) {
        List<TSNode> methods = findAllDescendants(bodyNode, "method_declaration");
        for (TSNode method : methods) {
            MethodInfo methodInfo = analyzeMethod(source, method, typeInfo.name, fieldTypes);
            typeInfo.methods.add(methodInfo);
        }
        
        List<TSNode> constructors = findAllDescendants(bodyNode, "constructor_declaration");
        for (TSNode constructor : constructors) {
            MethodInfo ctorInfo = analyzeConstructor(source, constructor, typeInfo.name, fieldTypes);
            typeInfo.methods.add(ctorInfo);
        }
    }
    
    // Helper: Find type node in common locations
    private TSNode findTypeNode(TSNode searchScope) {
        TSNode typeNode = findFirstChild(searchScope, NT_TYPE);
        if (typeNode == null) typeNode = findFirstDescendant(searchScope, NT_TYPE_IDENTIFIER);
        if (typeNode == null) typeNode = findFirstDescendant(searchScope, NT_SCOPED_IDENTIFIER);
        if (typeNode == null) typeNode = findFirstDescendant(searchScope, NT_SCOPED_TYPE_IDENTIFIER);
        if (typeNode == null) typeNode = findFirstDescendant(searchScope, NT_ARRAY_TYPE);
        if (typeNode == null) typeNode = findFirstDescendant(searchScope, NT_PRIMITIVE_TYPE);
        if (typeNode == null) typeNode = findFirstDescendant(searchScope, NT_INTEGRAL_TYPE);
        if (typeNode == null) typeNode = findFirstDescendant(searchScope, NT_FLOATING_POINT_TYPE);
        if (typeNode == null) typeNode = findFirstDescendant(searchScope, NT_BOOLEAN_TYPE);
        return typeNode;
    }
    
    // Helper: Apply visibility fallback for all target types
    private void applyVisibilityFallback(Object target, String fullModifiersText, List<String> modifiers) {
        if (fullModifiersText == null) return;
        String visibility = null;
        if (fullModifiersText.contains("public")) {
            visibility = "public";
        } else if (fullModifiersText.contains("protected")) {
            visibility = "protected";
        } else if (fullModifiersText.contains("private")) {
            visibility = "private";
        }
        
        if (visibility != null) {
            if (target instanceof TypeInfo && ((TypeInfo) target).visibility == null) {
                ((TypeInfo) target).visibility = visibility;
            } else if (target instanceof MethodInfo && ((MethodInfo) target).visibility == null) {
                ((MethodInfo) target).visibility = visibility;
            } else if (target instanceof FieldInfo && ((FieldInfo) target).visibility == null) {
                ((FieldInfo) target).visibility = visibility;
            }
            if (!modifiers.contains(visibility)) {
                modifiers.add(visibility);
            }
        }
    }
}
