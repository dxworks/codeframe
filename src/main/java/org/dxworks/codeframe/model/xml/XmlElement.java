package org.dxworks.codeframe.model.xml;

import java.util.List;
import java.util.Map;

public class XmlElement {
    public String name; // qualified name as written, e.g. "soap:Envelope"
    public int lines; // number of source lines this element spans
    public List<String> attributes; // optional; attribute names in source order (excludes xmlns*)
    public Map<String, String> namespaces; // optional; prefix -> URI; "" for default namespace
    public List<XmlElement> children; // optional; nested elements in source order
}
