package org.dxworks.codeframe.model;

import java.util.ArrayList;
import java.util.List;

public class MethodInfo {
    public String name;
    public String returnType;
    public String visibility;
    public List<String> modifiers = new ArrayList<>();
    public List<String> annotations = new ArrayList<>();
    public List<Parameter> parameters = new ArrayList<>();
    public List<String> localVariables = new ArrayList<>();
    public List<MethodCall> methodCalls = new ArrayList<>();
}
