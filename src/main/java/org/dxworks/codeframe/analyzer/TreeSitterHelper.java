package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.MethodCall;
import org.dxworks.codeframe.model.MethodInfo;
import org.treesitter.TSNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TreeSitterHelper {
    
    /**
     * Standard comparator for sorting MethodCall objects by name, then objectType, then objectName.
     * Used across all language analyzers for consistent output ordering.
     */
    public static final Comparator<MethodCall> METHOD_CALL_COMPARATOR = (a, b) -> {
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
    };
    
    public static String getNodeText(String source, TSNode node) {
        if (node == null || node.isNull()) return null;
        int startByte = node.getStartByte();
        int endByte = node.getEndByte();
        
        // Convert byte offsets to character offsets
        // Tree-sitter uses UTF-8 byte offsets, but Java String uses UTF-16 character indices
        byte[] sourceBytes = source.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        // Ensure we don't go out of bounds
        if (startByte < 0) startByte = 0;
        if (endByte > sourceBytes.length) endByte = sourceBytes.length;
        if (startByte >= endByte) return "";
        
        // Extract the byte range and convert back to String
        byte[] textBytes = new byte[endByte - startByte];
        System.arraycopy(sourceBytes, startByte, textBytes, 0, endByte - startByte);
        String text = new String(textBytes, java.nio.charset.StandardCharsets.UTF_8);
        
        // Normalize line endings to LF for cross-platform consistency
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }
    
    /**
     * Collapse all whitespace (including newlines and tabs) to single spaces and trim.
     * Useful for normalizing annotations/attributes and other inline metadata.
     */
    public static String normalizeInline(String s) {
        if (s == null) return null;
        return s.replaceAll("\\s+", " ").trim();
    }
    
    public static TSNode findFirstChild(TSNode parent, String nodeType) {
        if (parent == null || parent.isNull()) return null;
        int count = parent.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = parent.getNamedChild(i);
            if (child != null && !child.isNull() && nodeType.equals(child.getType())) {
                return child;
            }
        }
        return null;
    }
    
    public static List<TSNode> findAllChildren(TSNode parent, String nodeType) {
        List<TSNode> result = new ArrayList<>();
        if (parent == null || parent.isNull()) return result;
        int count = parent.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = parent.getNamedChild(i);
            if (child != null && !child.isNull() && nodeType.equals(child.getType())) {
                result.add(child);
            }
        }
        return result;
    }
    
    public static TSNode findFirstDescendant(TSNode root, String nodeType) {
        if (root == null || root.isNull()) return null;
        Deque<TSNode> stack = new ArrayDeque<>();
        stack.push(root);
        
        while (!stack.isEmpty()) {
            TSNode node = stack.pop();
            if (node == null || node.isNull()) continue;
            
            if (nodeType.equals(node.getType())) {
                return node;
            }
            int count = node.getNamedChildCount();
            for (int i = count - 1; i >= 0; i--) {
                TSNode child = node.getNamedChild(i);
                if (child != null && !child.isNull()) {
                    stack.push(child);
                }
            }
        }
        return null;
    }
    
    public static List<TSNode> findAllDescendants(TSNode root, String nodeType) {
        List<TSNode> result = new ArrayList<>();
        if (root == null || root.isNull()) return result;
        
        Deque<TSNode> stack = new ArrayDeque<>();
        stack.push(root);
        
        while (!stack.isEmpty()) {
            TSNode node = stack.pop();
            if (node == null || node.isNull()) continue;
            
            if (nodeType.equals(node.getType())) {
                result.add(node);
            }
            int count = node.getNamedChildCount();
            for (int i = count - 1; i >= 0; i--) {
                TSNode child = node.getNamedChild(i);
                if (child != null && !child.isNull()) {
                    stack.push(child);
                }
            }
        }
        return result;
    }

    // --- Generic helpers to reduce duplication across analyzers ---

    public static TSNode getChildByFieldName(TSNode parent, String fieldName) {
        if (parent == null || parent.isNull()) return null;
        // Use getChildCount() and getChild(i) because getFieldNameForChild(i) 
        // expects the total child index (including anonymous nodes like operators, punctuation),
        // not the named child index
        for (int i = 0; i < parent.getChildCount(); i++) {
            try {
                String fn = parent.getFieldNameForChild(i);
                if (fieldName.equals(fn)) return parent.getChild(i);
            } catch (Exception ignored) { }
        }
        return null;
    }

    public static boolean isTypeOneOf(String type, String... types) {
        if (type == null) return false;
        for (String t : types) if (type.equals(t)) return true;
        return false;
    }

    public static boolean isNodeTypeOneOf(TSNode node, String... types) {
        if (node == null || node.isNull()) return false;
        return isTypeOneOf(node.getType(), types);
    }

    public static TSNode getFirstChildOfTypes(TSNode parent, String... types) {
        if (parent == null || parent.isNull()) return null;
        for (int i = 0; i < parent.getNamedChildCount(); i++) {
            TSNode child = parent.getNamedChild(i);
            if (child != null && !child.isNull() && isTypeOneOf(child.getType(), types)) {
                return child;
            }
        }
        return null;
    }

    public static List<TSNode> findAllDescendantsOfTypes(TSNode root, String... types) {
        List<TSNode> result = new ArrayList<>();
        if (root == null || root.isNull()) return result;
        Deque<TSNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            TSNode node = stack.pop();
            if (node == null || node.isNull()) continue;
            if (isTypeOneOf(node.getType(), types)) result.add(node);
            for (int i = node.getNamedChildCount() - 1; i >= 0; i--) {
                TSNode child = node.getNamedChild(i);
                if (child != null && !child.isNull()) stack.push(child);
            }
        }
        return result;
    }

    /**
     * Return the argument list node ("argument_list" or "arguments") if present.
     */
    public static TSNode getArgumentListNode(TSNode callLike) {
        if (callLike == null || callLike.isNull()) return null;
        TSNode byField = getChildByFieldName(callLike, "arguments");
        if (byField != null && !byField.isNull()) return byField;
        TSNode byType = findFirstChild(callLike, "argument_list");
        if (byType != null && !byType.isNull()) return byType;
        byType = findFirstChild(callLike, "arguments");
        return byType;
    }

    /**
     * Infers type from common expression patterns shared across JS/TS analyzers.
     * Returns null if no common pattern matches, allowing language-specific handling.
     */
    public static String inferCommonExpressionType(String source, TSNode expr) {
        if (expr == null || expr.isNull()) return null;
        
        String exprType = expr.getType();
        
        // Handle new expressions: new ClassName()
        if ("new_expression".equals(exprType)) {
            TSNode typeNode = expr.getNamedChild(0);
            if (typeNode != null && !typeNode.isNull() && "identifier".equals(typeNode.getType())) {
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
        
        // Handle call expressions - return the function name as a hint
        if ("call_expression".equals(exprType)) {
            TSNode callee = expr.getNamedChild(0);
            if (callee != null && !callee.isNull() && "identifier".equals(callee.getType())) {
                return getNodeText(source, callee) + "Result";
            }
        }
        
        return null;
    }

    /**
     * Collects a method call into the methodInfo, incrementing count if already present.
     * This deduplicates method call tracking logic across analyzers.
     */
    public static void collectMethodCall(MethodInfo methodInfo, String methodName, String objectType, String objectName) {
        for (MethodCall existingCall : methodInfo.methodCalls) {
            if (existingCall.matches(methodName, objectType, objectName, null)) {
                existingCall.callCount++;
                return;
            }
        }
        methodInfo.methodCalls.add(new MethodCall(methodName, objectType, objectName));
    }

    /**
     * Identifies nested classes/types within a list of type declarations.
     * Returns a set of start byte positions for nodes that are nested inside other declarations.
     *
     * @param allDeclarations list of all type declarations (classes, etc.)
     * @param bodyNodeType the node type for the body container (e.g., "class_body", "block")
     * @param nestedNodeTypes the node types to search for as nested declarations
     * @return set of start byte positions identifying nested declarations
     */
    public static Set<Integer> identifyNestedNodes(List<TSNode> allDeclarations, String bodyNodeType, String... nestedNodeTypes) {
        Set<Integer> nestedIds = new HashSet<>();
        for (TSNode decl : allDeclarations) {
            if (decl == null || decl.isNull()) continue;
            TSNode body = findFirstChild(decl, bodyNodeType);
            if (body != null) {
                for (String nestedType : nestedNodeTypes) {
                    List<TSNode> nested = findAllDescendants(body, nestedType);
                    for (TSNode n : nested) {
                        if (n != null && !n.isNull()) {
                            nestedIds.add(n.getStartByte());
                        }
                    }
                }
            }
        }
        return nestedIds;
    }

    /**
     * Checks if a string is a valid identifier (for JS/TS style identifiers).
     */
    public static boolean isValidIdentifier(String name) {
        return name != null && name.matches("[a-zA-Z_$][a-zA-Z0-9_$]*");
    }

    /**
     * Traverses left in a member_expression chain to find the base identifier node.
     * Useful for getting the root object in chains like a.b.c.method().
     */
    public static TSNode getLeftmostIdentifier(TSNode memberExpr) {
        if (memberExpr == null) return null;
        
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

    /**
     * Renders a simplified object name for method call tracking.
     * Handles call expressions, member expressions, and literals to produce clean output.
     * 
     * @param source the source code
     * @param objectExpr the object expression node
     * @param literalTypes node types to treat as literals (e.g., "array", "object", "string", "number")
     * @return simplified object name string
     */
    public static String renderObjectName(String source, TSNode objectExpr, String... literalTypes) {
        if (objectExpr == null || objectExpr.isNull()) return null;
        
        String objType = objectExpr.getType();
        
        // Check for literal types
        for (String litType : literalTypes) {
            if (litType.equals(objType)) {
                return "<literal>";
            }
        }
        
        // For call expressions, return just the function name rather than the full call text
        if ("call_expression".equals(objType)) {
            TSNode callee = objectExpr.getNamedChild(0);
            if (callee != null) {
                if ("identifier".equals(callee.getType())) {
                    return getNodeText(source, callee) + "()";
                }
                // For chained calls like foo().bar(), recurse
                return renderObjectName(source, callee, literalTypes) + "()";
            }
            return "<call>";
        }
        
        // For member expressions like obj.prop or call().prop, simplify
        if ("member_expression".equals(objType)) {
            TSNode baseObj = objectExpr.getNamedChild(0);
            TSNode propNode = findFirstChild(objectExpr, "property_identifier");
            // Also check for private property identifiers (#field)
            if (propNode == null) {
                propNode = findFirstChild(objectExpr, "private_property_identifier");
            }
            String baseName = baseObj != null ? renderObjectName(source, baseObj, literalTypes) : "<unknown>";
            String propName = propNode != null ? getNodeText(source, propNode) : "";
            return baseName + "." + propName;
        }
        
        return getNodeText(source, objectExpr);
    }
}
