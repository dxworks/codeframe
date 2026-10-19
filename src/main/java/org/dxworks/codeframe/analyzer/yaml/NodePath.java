package org.dxworks.codeframe.analyzer.yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable structural path through a YAML document tree.
 * Segments are mapping key names. Array items use a null segment.
 */
final class NodePath {

    static final String ARRAY_ITEM = null;

    private final List<String> segments;

    NodePath() {
        this.segments = Collections.emptyList();
    }

    private NodePath(List<String> segments) {
        this.segments = segments;
    }

    NodePath append(String key) {
        List<String> next = new ArrayList<>(segments);
        next.add(key);
        return new NodePath(next);
    }

    NodePath arrayItem() {
        List<String> next = new ArrayList<>(segments);
        next.add(ARRAY_ITEM);
        return new NodePath(next);
    }

    int size() {
        return segments.size();
    }

    String get(int index) {
        return segments.get(index);
    }

    boolean startsWith(String... keys) {
        if (keys.length > segments.size()) {
            return false;
        }
        for (int i = 0; i < keys.length; i++) {
            String expected = keys[i];
            String actual = segments.get(i);
            if (expected == null) {
                if (actual != null) return false;
            } else {
                if (!expected.equals(actual)) return false;
            }
        }
        return true;
    }

    boolean endsWithKey(String key) {
        if (segments.isEmpty()) return false;
        return key.equals(segments.get(segments.size() - 1));
    }

    String lastKey() {
        if (segments.isEmpty()) return null;
        return segments.get(segments.size() - 1);
    }

    String parentKey() {
        if (segments.size() < 2) return null;
        return segments.get(segments.size() - 2);
    }

    String keyAt(int index) {
        if (index < 0 || index >= segments.size()) return null;
        return segments.get(index);
    }

    List<String> segments() {
        return Collections.unmodifiableList(segments);
    }

    @Override
    public String toString() {
        return segments.toString();
    }
}
