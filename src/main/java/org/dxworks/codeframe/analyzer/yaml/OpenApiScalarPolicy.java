package org.dxworks.codeframe.analyzer.yaml;

import java.util.Set;

final class OpenApiScalarPolicy {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "patch", "options", "head", "trace"
    );

    private OpenApiScalarPolicy() {
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
        if (isOperationTagValue(path)) {
            return true;
        }
        if (isOperationId(path, key)) {
            return true;
        }
        if (isParameterField(path, key)) {
            return true;
        }
        if (isRequestBodyRequired(path, key)) {
            return true;
        }
        if (isSchemaTypeLike(path, key)) {
            return true;
        }
        if (isSecuritySchemeType(path, key)) {
            return true;
        }
        if (isSecuritySchemeHttpScheme(path, key)) {
            return true;
        }
        if (isSecuritySchemeApiKeyIn(path, key)) {
            return true;
        }
        if (isLocalLinkOperationRef(path, key, scalarValue)) {
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
        if ("required".equals(parent) || "enum".equals(parent)) {
            return isSchemaContext(path);
        }
        if ("tags".equals(parent)) {
            return isOperationContext(path);
        }
        return false;
    }

    private static boolean isRootVersion(NodePath path, String key) {
        return path.size() == 1 && ("openapi".equals(key) || "swagger".equals(key));
    }

    private static boolean isInfoTitle(NodePath path, String key) {
        return "title".equals(key) && path.size() == 2 && "info".equals(path.keyAt(0));
    }

    private static boolean isOperationTagValue(NodePath path) {
        return path.size() >= 5
                && path.keyAt(path.size() - 2) == null
                && "tags".equals(path.keyAt(path.size() - 3))
                && isOperationContext(path);
    }

    private static boolean isOperationId(NodePath path, String key) {
        if (!"operationId".equals(key)) {
            return false;
        }
        return isOperationContext(path) || isLinkContext(path);
    }

    private static boolean isParameterField(NodePath path, String key) {
        if (!"name".equals(key) && !"in".equals(key) && !"required".equals(key)) {
            return false;
        }

        boolean inParameterArray = path.size() >= 3
                && path.keyAt(path.size() - 2) == null
                && "parameters".equals(path.keyAt(path.size() - 3));
        boolean inComponentParameter = path.size() == 4
                && "components".equals(path.keyAt(0))
                && "parameters".equals(path.keyAt(1));

        return inParameterArray || inComponentParameter;
    }

    private static boolean isRequestBodyRequired(NodePath path, String key) {
        if (!"required".equals(key)) {
            return false;
        }

        return (path.size() >= 2 && "requestBody".equals(path.keyAt(path.size() - 2)))
                || (path.size() == 4 && "components".equals(path.keyAt(0))
                && "requestBodies".equals(path.keyAt(1)));
    }

    private static boolean isSchemaTypeLike(NodePath path, String key) {
        if (!"type".equals(key) && !"format".equals(key) && !"$ref".equals(key)) {
            return false;
        }
        return isSchemaContext(path) || isResponseLikeContext(path) || isRequestBodyContext(path);
    }

    private static boolean isSecuritySchemeType(NodePath path, String key) {
        return "type".equals(key) && isSecuritySchemeContext(path);
    }

    private static boolean isSecuritySchemeHttpScheme(NodePath path, String key) {
        return "scheme".equals(key) && isSecuritySchemeContext(path);
    }

    private static boolean isSecuritySchemeApiKeyIn(NodePath path, String key) {
        return "in".equals(key) && isSecuritySchemeContext(path);
    }

    private static boolean isLocalLinkOperationRef(NodePath path, String key, String scalarValue) {
        if (!"operationRef".equals(key)) {
            return false;
        }
        if (!isLinkContext(path)) {
            return false;
        }
        return scalarValue != null && scalarValue.startsWith("#");
    }

    private static boolean isSchemaContext(NodePath path) {
        if (path.startsWith("components", "schemas")) {
            return true;
        }
        for (int i = 0; i < path.size(); i++) {
            String segment = path.keyAt(i);
            if (segment == null) {
                continue;
            }
            if ("schema".equals(segment)
                    || "schemas".equals(segment)
                    || "definitions".equals(segment)
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

    private static boolean isOperationContext(NodePath path) {
        if (path.size() >= 3 && "paths".equals(path.keyAt(0)) && HTTP_METHODS.contains(path.keyAt(2))) {
            return true;
        }

        for (int i = 0; i < path.size() - 1; i++) {
            if ("callbacks".equals(path.keyAt(i))) {
                for (int j = i + 1; j < path.size(); j++) {
                    String segment = path.keyAt(j);
                    if (HTTP_METHODS.contains(segment)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isResponseLikeContext(NodePath path) {
        for (int i = 0; i < path.size(); i++) {
            String segment = path.keyAt(i);
            if ("responses".equals(segment) || "response".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRequestBodyContext(NodePath path) {
        for (int i = 0; i < path.size(); i++) {
            String segment = path.keyAt(i);
            if ("requestBody".equals(segment) || "requestBodies".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLinkContext(NodePath path) {
        for (int i = 0; i < path.size(); i++) {
            if ("links".equals(path.keyAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSecuritySchemeContext(NodePath path) {
        return (path.size() == 4
                && "components".equals(path.keyAt(0))
                && "securitySchemes".equals(path.keyAt(1)))
                || (path.size() == 3 && "securityDefinitions".equals(path.keyAt(0)));
    }
}
