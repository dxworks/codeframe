package org.dxworks.codeframe.model.markdown;

import java.util.ArrayList;
import java.util.List;

public class MarkdownSection {
    public String heading;
    public int level; // 1-6, with 0 used for preamble
    public List<MarkdownElement> elements = new ArrayList<>();
    public List<MarkdownSection> subsections = new ArrayList<>();
}
