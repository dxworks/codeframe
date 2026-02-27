# Document with No Preamble Content

This document starts directly with a heading and should have null preamble.

## Bold Heading Test

### **Important Section Title**

This section has a heading wrapped in bold formatting. The analyzer should extract the actual text content, not the AST node type.

- First item in a list
- Second item in a list

### **Another Bold Heading**

More content here to test the bold heading extraction bug.

---

## Normal Heading Test

### Regular Heading Without Bold

This heading should extract correctly as "Regular Heading Without Bold".

1. First ordered item
2. Second ordered item
3. Third ordered item

### **Final Bold Test**

This is the last test case with bold formatting in the heading.

| Column 1 | Column 2 |
|----------|----------|
| Cell 1   | Cell 2   |
| Cell 3   | Cell 4   |

## Conclusion

This document tests both the preamble null issue and the bold heading extraction bug.
