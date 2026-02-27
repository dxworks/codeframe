package org.dxworks.codeframe.model.markdown;

import java.util.Map;
import java.util.List;

public class MarkdownElement {
    public String type; // paragraph, code_block, table, bullet_list, ordered_list, block_quote, thematic_break, html_block, image
    public int lines; // number of lines spanned by this element
    public Map<String, Object> properties; // optional properties like language for code blocks, altText for images
    public List<MarkdownElement> children; // optional nested elements (e.g., list -> list_item -> paragraph)
}
