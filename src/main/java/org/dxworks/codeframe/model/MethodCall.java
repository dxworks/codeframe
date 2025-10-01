package org.dxworks.codeframe.model;

public class MethodCall {
    public String methodName;
    public String objectType;
    public String objectName;
    public int callCount = 1;

    public MethodCall(String methodName, String objectType, String objectName) {
        this.methodName = methodName;
        this.objectType = objectType;
        this.objectName = objectName;
    }
    
    // For aggregation: check if two calls are the same (same method, type, object)
    public boolean matches(String methodName, String objectType, String objectName) {
        boolean nameMatch = this.methodName != null && this.methodName.equals(methodName);
        boolean typeMatch = (this.objectType == null && objectType == null) || 
                           (this.objectType != null && this.objectType.equals(objectType));
        boolean objMatch = (this.objectName == null && objectName == null) || 
                          (this.objectName != null && this.objectName.equals(objectName));
        return nameMatch && typeMatch && objMatch;
    }
}
