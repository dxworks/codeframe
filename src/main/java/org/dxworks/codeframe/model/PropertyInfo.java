package org.dxworks.codeframe.model;

import java.util.ArrayList;
import java.util.List;

public class PropertyInfo {
    public String name;
    public String type;
    public String visibility;
    public List<String> modifiers = new ArrayList<>();
    public List<String> annotations = new ArrayList<>();
    public List<AccessorInfo> accessors = new ArrayList<>();
}
