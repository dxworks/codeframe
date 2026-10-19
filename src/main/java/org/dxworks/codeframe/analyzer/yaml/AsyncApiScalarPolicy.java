package org.dxworks.codeframe.analyzer.yaml;

final class AsyncApiScalarPolicy {

    private AsyncApiScalarPolicy() {
    }

    static boolean isAllowed(NodePath path, String scalarValue) {
        if (path == null || path.size() == 0) {
            return false;
        }
        if (containsVendorExtension(path)) {
            return false;
        }

        String key = path.lastKey();
        if (key == null) {
            return isAllowedSequenceScalar(path);
        }

        if (isRootVersion(path, key)) {
            return true;
        }
        if (isInfoTitle(path, key)) {
            return true;
        }
        if (isOperationId(path, key)) {
            return true;
        }
        if (isOperationAction(path, key, scalarValue)) {
            return true;
        }
        if (isSchemaTypeLike(path, key, scalarValue)) {
            return true;
        }
        if (isSecuritySchemeType(path, key)) {
            return true;
        }

        return false;
    }

    private static boolean containsVendorExtension(NodePath path) {
        for (int i = 0; i < path.size(); i++) {
            String segment = path.keyAt(i);
            if (segment != null && segment.toLowerCase().startsWith("x-")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedSequenceScalar(NodePath path) {
        String parent = path.parentKey();
        return ("required".equals(parent) || "enum".equals(parent)) && isSchemaContext(path);
    }

    private static boolean isRootVersion(NodePath path, String key) {
        return path.size() == 1 && "asyncapi".equals(key);
    }

    private static boolean isInfoTitle(NodePath path, String key) {
        return "title".equals(key) && path.size() == 2 && "info".equals(path.keyAt(0));
    }

    private static boolean isOperationId(NodePath path, String key) {
        if (!"operationId".equals(key)) {
            return false;
        }
        return isAsyncApi2ChannelOperationContext(path) || isAsyncApi3TopLevelOperationContext(path);
    }

    private static boolean isOperationAction(NodePath path, String key, String scalarValue) {
        if (!"action".equals(key) || !isAsyncApi3TopLevelOperationContext(path)) {
            return false;
        }
        return "send".equals(scalarValue) || "receive".equals(scalarValue);
    }

    private static boolean isSchemaTypeLike(NodePath path, String key, String scalarValue) {
        if ("type".equals(key) || "format".equals(key)) {
            return isSchemaContext(path);
        }
        if (!"$ref".equals(key)) {
            return false;
        }
        return isSchemaContext(path) && scalarValue != null && scalarValue.startsWith("#");
    }

    private static boolean isSecuritySchemeType(NodePath path, String key) {
        return "type".equals(key) && isSecuritySchemeContext(path);
    }

    private static boolean isAsyncApi2ChannelOperationContext(NodePath path) {
        return path.size() == 4
                && "channels".equals(path.keyAt(0))
                && ("publish".equals(path.keyAt(2)) || "subscribe".equals(path.keyAt(2)));
    }

    private static boolean isAsyncApi3TopLevelOperationContext(NodePath path) {
        return path.size() == 3
                && "operations".equals(path.keyAt(0));
    }

    private static boolean isSchemaContext(NodePath path) {
        if (path.startsWith("components", "schemas") || path.startsWith("components", "messages")) {
            return true;
        }

        for (int i = 0; i < path.size(); i++) {
            String segment = path.keyAt(i);
            if (segment == null) {
                continue;
            }
            if ("schema".equals(segment)
                    || "schemas".equals(segment)
                    || "payload".equals(segment)
                    || "properties".equals(segment)
                    || "items".equals(segment)
                    || "allOf".equals(segment)
                    || "anyOf".equals(segment)
                    || "oneOf".equals(segment)
                    || "not".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSecuritySchemeContext(NodePath path) {
        return path.size() == 4
                && "components".equals(path.keyAt(0))
                && "securitySchemes".equals(path.keyAt(1));
    }
}
