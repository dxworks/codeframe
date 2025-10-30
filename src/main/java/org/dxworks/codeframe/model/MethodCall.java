package org.dxworks.codeframe.model;

public class MethodCall {
    public String methodName;
    public String objectType;
    public String objectName;
    public int callCount = 1;
    public Integer parameterCount; // Number of parameters in the method call

    public MethodCall(String methodName, String objectType, String objectName) {
        this.methodName = methodName;
        this.objectType = objectType;
        this.objectName = objectName;
    }
    
    public MethodCall(String methodName, String objectType, String objectName, Integer parameterCount) {
        this.methodName = methodName;
        this.objectType = objectType;
        this.objectName = objectName;
        this.parameterCount = parameterCount;
    }
    
    // For aggregation: check if two calls are the same (same method, type, object, and parameter count)
    public boolean matches(String methodName, String objectType, String objectName, Integer parameterCount) {
        boolean nameMatch = this.methodName != null && this.methodName.equals(methodName);
        boolean typeMatch = (this.objectType == null && objectType == null) || 
                           (this.objectType != null && this.objectType.equals(objectType));
        boolean objMatch = (this.objectName == null && objectName == null) || 
                          (this.objectName != null && this.objectName.equals(objectName));
        boolean paramMatch = (this.parameterCount == null && parameterCount == null) ||
                            (this.parameterCount != null && this.parameterCount.equals(parameterCount));
        return nameMatch && typeMatch && objMatch && paramMatch;
    }
}
