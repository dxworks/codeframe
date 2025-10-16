package org.dxworks.codeframe.model;

import java.util.ArrayList;
import java.util.List;

public class AccessorInfo {
    public String kind; // "get" or "set"
    public String visibility;
    public List<String> modifiers = new ArrayList<>();
    public List<String> annotations = new ArrayList<>();
    public List<String> localVariables = new ArrayList<>();
    public List<MethodCall> methodCalls = new ArrayList<>();
}
