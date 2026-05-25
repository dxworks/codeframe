package org.dxworks.codeframe.model.yaml;

import java.util.List;

public class YamlNode {
    public String kind;
    public int lines;
    public String value;
    public String anchor;
    public String alias;
    public Boolean merge;
    public List<YamlMappingEntry> entries;
    public List<YamlNode> items;
}
