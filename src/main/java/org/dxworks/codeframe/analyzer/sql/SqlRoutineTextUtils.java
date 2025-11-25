package org.dxworks.codeframe.analyzer.sql;

import java.util.ArrayList;
import java.util.List;

public final class SqlRoutineTextUtils {

    private SqlRoutineTextUtils() {
        // utility class
    }

    public static int findMatchingParen(String text, int openIdx) {
        if (text == null || openIdx < 0 || openIdx >= text.length()) {
            return -1;
        }
        int depth = 0;
        for (int i = openIdx; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static List<String> splitTopLevel(String text, char separator) {
        List<String> parts = new ArrayList<>();
        if (text == null) {
            return parts;
        }
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
                current.append(c);
            } else if (c == ')') {
                depth--;
                current.append(c);
            } else if (c == separator && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }
}
