package org.dxworks.codeframe.analyzer;

import org.treesitter.TSNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class TreeSitterHelper {
    
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
}
