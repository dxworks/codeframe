package org.dxworks.codeframe.model.cobol;

import java.util.ArrayList;
import java.util.List;

public class COBOLDataItem {
    public String name;
    public int level;
    public String picture;
    public String section;
    public String usage;
    public String redefines;
    public Integer occurs;
    public List<COBOLDataItem> children = new ArrayList<>();
}
