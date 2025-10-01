package org.dxworks.codeframe.model;

import java.util.ArrayList;
import java.util.List;

public class FieldInfo {
    public String name;
    public String type;
    public String visibility;
    public List<String> modifiers = new ArrayList<>();
    public List<String> annotations = new ArrayList<>();
}
