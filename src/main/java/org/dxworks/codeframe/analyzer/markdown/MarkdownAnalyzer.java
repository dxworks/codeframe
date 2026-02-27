package org.dxworks.codeframe.analyzer.markdown;

import org.commonmark.parser.Parser;
import org.commonmark.node.Document;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.Text;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Image;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.BulletList;
import org.commonmark.node.OrderedList;
import org.commonmark.node.ListItem;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.ThematicBreak;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.SourceSpan;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterBlock;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.dxworks.codeframe.analyzer.LanguageAnalyzer;
import org.dxworks.codeframe.model.Analysis;
import org.dxworks.codeframe.model.markdown.MarkdownFileAnalysis;
import org.dxworks.codeframe.model.markdown.MarkdownSection;
import org.dxworks.codeframe.model.markdown.MarkdownElement;
import org.treesitter.TSNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarkdownAnalyzer implements LanguageAnalyzer {
    
    private final Parser parser;
    
    public MarkdownAnalyzer() {
        // Build parser with source spans enabled for block nodes
        this.parser = Parser.builder()
                .extensions(List.of(
                        TablesExtension.create(),
                        YamlFrontMatterExtension.create()
                ))
                .includeSourceSpans(org.commonmark.parser.IncludeSourceSpans.BLOCKS)
                .build();
    }
    
    @Override
    public Analysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        MarkdownFileAnalysis analysis = new MarkdownFileAnalysis();
        analysis.filePath = filePath;
        // language is already set to "markdown" in the model
        
        // Parse markdown document using commonmark
        Node document = parser.parse(sourceCode);
        
        // Build section hierarchy from headings
        buildSectionHierarchy((Document) document, analysis);
        
        return analysis;
    }
    
    private void buildSectionHierarchy(Document document, MarkdownFileAnalysis analysis) {
        List<MarkdownSection> sections = new ArrayList<>();
        List<MarkdownSection> sectionStack = new ArrayList<>();

        document.accept(new MarkdownSectionVisitor(sections, sectionStack));

        analysis.preamble = extractPreamble(document);
        analysis.sections = sections;
    }

    private MarkdownSection extractPreamble(Document document) {
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading) {
                break;
            }
            if (isMeaningfulPreambleNode(node)) {
                MarkdownSection preamble = new MarkdownSection();
                preamble.heading = null;
                preamble.level = 0;
                return preamble;
            }
        }
        return null;
    }

    private boolean isMeaningfulPreambleNode(Node node) {
        if (node instanceof YamlFrontMatterBlock) {
            return false;
        }
        if (node instanceof Paragraph) {
            return hasNonBlankText(node);
        }
        return true;
    }

    private boolean hasNonBlankText(Node node) {
        if (node instanceof Text text) {
            return !text.getLiteral().isBlank();
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (hasNonBlankText(child)) {
                return true;
            }
        }
        return false;
    }
    
    private static class MarkdownSectionVisitor extends AbstractVisitor {
        private final List<MarkdownSection> sections;
        private final List<MarkdownSection> sectionStack;
        private final List<MarkdownElement> elementStack = new ArrayList<>();
        
        public MarkdownSectionVisitor(List<MarkdownSection> sections, 
                                    List<MarkdownSection> sectionStack) {
            this.sections = sections;
            this.sectionStack = sectionStack;
        }
        
        @Override
        public void visit(Heading heading) {
            MarkdownSection section = createSectionFromHeading(heading);
            addSectionToHierarchy(section);
            super.visit(heading);
        }
        
        @Override
        public void visit(Paragraph paragraph) {
            if (hasFoundFirstHeading()) {
                Image standaloneImage = getStandaloneImage(paragraph);
                if (standaloneImage != null) {
                    addElementToCurrentContext(createImageElement(standaloneImage));
                } else {
                    addElementToCurrentContext(createParagraphElement(paragraph));
                }
            }
            super.visit(paragraph);
        }
        
        @Override
        public void visit(FencedCodeBlock codeBlock) {
            if (hasFoundFirstHeading()) {
                addElementToCurrentContext(createFencedCodeBlockElement(codeBlock));
            }
            super.visit(codeBlock);
        }
        
        @Override
        public void visit(IndentedCodeBlock codeBlock) {
            if (hasFoundFirstHeading()) {
                addElementToCurrentContext(createIndentedCodeBlockElement(codeBlock));
            }
            super.visit(codeBlock);
        }
        
        @Override
        public void visit(CustomBlock customBlock) {
            if (customBlock instanceof YamlFrontMatterBlock) {
                // Front matter is intentionally ignored in the output model.
                return;
            }
            if (customBlock instanceof TableBlock table && hasFoundFirstHeading()) {
                addElementToCurrentContext(createTableElement(table));
            }
            super.visit(customBlock);
        }
        
        @Override
        public void visit(BulletList bulletList) {
            if (hasFoundFirstHeading()) {
                MarkdownElement element = createBulletListElement(bulletList);
                withElementContext(element, () -> super.visit(bulletList));
                return;
            }
            super.visit(bulletList);
        }
        
        @Override
        public void visit(OrderedList orderedList) {
            if (hasFoundFirstHeading()) {
                MarkdownElement element = createOrderedListElement(orderedList);
                withElementContext(element, () -> super.visit(orderedList));
                return;
            }
            super.visit(orderedList);
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            if (hasFoundFirstHeading()) {
                addElementToCurrentContext(createBlockQuoteElement(blockQuote));
                return;
            }
            super.visit(blockQuote);
        }

        @Override
        public void visit(ThematicBreak thematicBreak) {
            if (hasFoundFirstHeading()) {
                addElementToCurrentContext(createThematicBreakElement(thematicBreak));
            }
            super.visit(thematicBreak);
        }

        @Override
        public void visit(HtmlBlock htmlBlock) {
            if (hasFoundFirstHeading()) {
                addElementToCurrentContext(createHtmlBlockElement(htmlBlock));
            }
            super.visit(htmlBlock);
        }
        
        @Override
        public void visit(ListItem listItem) {
            if (hasFoundFirstHeading()) {
                MarkdownElement element = createListItemElement(listItem);
                withElementContext(element, () -> super.visit(listItem));
                return;
            }
            super.visit(listItem);
        }
        
        private boolean hasFoundFirstHeading() {
            return !sectionStack.isEmpty();
        }
        
        private MarkdownSection getCurrentSection() {
            return sectionStack.isEmpty() ? null : sectionStack.get(sectionStack.size() - 1);
        }

        private void addElementToCurrentContext(MarkdownElement element) {
            if (!elementStack.isEmpty()) {
                MarkdownElement parent = elementStack.get(elementStack.size() - 1);
                if (parent.children == null) {
                    parent.children = new ArrayList<>();
                }
                parent.children.add(element);
                return;
            }

            MarkdownSection currentSection = getCurrentSection();
            if (currentSection != null) {
                currentSection.elements.add(element);
            }
        }

        private void withElementContext(MarkdownElement element, Runnable visitorAction) {
            addElementToCurrentContext(element);
            elementStack.add(element);
            try {
                visitorAction.run();
            } finally {
                elementStack.remove(elementStack.size() - 1);
            }
        }

        private Image getStandaloneImage(Paragraph paragraph) {
            Node first = paragraph.getFirstChild();
            if (first instanceof Image image && first.getNext() == null) {
                return image;
            }
            return null;
        }
        
        private MarkdownElement createParagraphElement(Paragraph paragraph) {
            return createElement("paragraph", paragraph, null);
        }

        private MarkdownElement createImageElement(Image image) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("altText", extractText(image));
            return createElement("image", image, properties);
        }
        
        private int computeLineSpan(Node node) {
            if (node == null || node.getSourceSpans() == null || node.getSourceSpans().isEmpty()) {
                return 1;
            }

            int minLine = Integer.MAX_VALUE;
            int maxLine = Integer.MIN_VALUE;

            for (SourceSpan span : node.getSourceSpans()) {
                int line = span.getLineIndex();
                minLine = Math.min(minLine, line);
                maxLine = Math.max(maxLine, line);
            }

            if (minLine == Integer.MAX_VALUE) {
                return 1;
            }

            return (maxLine - minLine) + 1;
        }

        private MarkdownElement createFencedCodeBlockElement(FencedCodeBlock codeBlock) {
            Map<String, Object> properties = null;
            if (codeBlock.getInfo() != null && !codeBlock.getInfo().trim().isEmpty()) {
                properties = new HashMap<>();
                properties.put("language", codeBlock.getInfo().trim());
            }
            return createElement("code_block", codeBlock, properties);
        }
        
        private MarkdownElement createIndentedCodeBlockElement(IndentedCodeBlock codeBlock) {
            return createElement("code_block", codeBlock, null);
        }
        
        private MarkdownElement createTableElement(TableBlock table) {
            return createElement("table", table, null);
        }
        
        private MarkdownElement createBulletListElement(BulletList bulletList) {
            return createElement("bullet_list", bulletList, null);
        }
        
        private MarkdownElement createOrderedListElement(OrderedList orderedList) {
            return createElement("ordered_list", orderedList, null);
        }

        private MarkdownElement createBlockQuoteElement(BlockQuote blockQuote) {
            return createElement("block_quote", blockQuote, null);
        }

        private MarkdownElement createThematicBreakElement(ThematicBreak thematicBreak) {
            return createElement("thematic_break", thematicBreak, null);
        }

        private MarkdownElement createHtmlBlockElement(HtmlBlock htmlBlock) {
            return createElement("html_block", htmlBlock, null);
        }

        private MarkdownElement createListItemElement(ListItem listItem) {
            return createElement("list_item", listItem, null);
        }

        private MarkdownElement createElement(String type, Node node, Map<String, Object> properties) {
            MarkdownElement element = new MarkdownElement();
            element.type = type;
            element.lines = computeLineSpan(node);
            element.properties = properties;
            element.children = null;
            return element;
        }
        
        private MarkdownSection createSectionFromHeading(Heading heading) {
            MarkdownSection section = new MarkdownSection();
            section.heading = extractText(heading);
            section.level = heading.getLevel();
            return section;
        }
        
        private void addSectionToHierarchy(MarkdownSection section) {
            // Find appropriate parent by level
            while (!sectionStack.isEmpty() && sectionStack.get(sectionStack.size() - 1).level >= section.level) {
                sectionStack.remove(sectionStack.size() - 1);
            }
            
            if (sectionStack.isEmpty()) {
                sections.add(section); // Top-level section
            } else {
                MarkdownSection parent = sectionStack.get(sectionStack.size() - 1);
                parent.subsections.add(section); // Nested section
            }
            
            sectionStack.add(section);
        }
        
        private String extractText(Node node) {
            StringBuilder text = new StringBuilder();
            Node child = node.getFirstChild();
            while (child != null) {
                if (child instanceof Text) {
                    text.append(((Text) child).getLiteral());
                } else {
                    // Recursively extract text from nested nodes (e.g., bold, italic)
                    text.append(extractText(child));
                }
                child = child.getNext();
            }
            return text.toString().trim();
        }
    }
}
