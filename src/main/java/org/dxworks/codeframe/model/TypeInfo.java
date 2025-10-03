package org.dxworks.codeframe.model;

import java.util.ArrayList;
import java.util.List;

public class TypeInfo {
    public String kind;
    public String name;
    public String visibility;
    public List<String> modifiers = new ArrayList<>();
    public List<String> annotations = new ArrayList<>();
    public String extendsType;
    public List<String> implementsInterfaces = new ArrayList<>();
    public List<FieldInfo> fields = new ArrayList<>();  // Fields belonging to this type
    public List<MethodInfo> methods = new ArrayList<>();  // Methods belonging to this type
    public List<TypeInfo> types = new ArrayList<>();  // Nested types belonging to this type
}
