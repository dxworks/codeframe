package org.dxworks.codeframe.analyzer;

import org.dxworks.codeframe.model.Analysis;
import org.dxworks.codeframe.model.sql.*;
import org.treesitter.TSNode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLAnalyzer implements LanguageAnalyzer {
    
    @Override
    public Analysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        // Create SQL-specific analysis
        SQLFileAnalysis sqlAnalysis = new SQLFileAnalysis();
        sqlAnalysis.filePath = filePath;
        sqlAnalysis.language = "sql";
        
        // Traverse AST and extract SQL constructs
        traverseNode(rootNode, sourceCode, sqlAnalysis);
        
        return sqlAnalysis;
    }

    // Returns true if the node is inside a function/procedure body or a definition context
    private boolean isInsideDefinition(TSNode node) {
        if (node == null || node.isNull()) return false;
        TSNode cur = node.getParent();
        while (cur != null && !cur.isNull()) {
            String t = cur.getType();
            if ("function_body".equals(t) || "create_function".equals(t) || "create_procedure".equals(t) || "block".equals(t) || "create_view".equals(t)) {
                return true;
            }
            cur = cur.getParent();
        }
        return false;
    }

    // Parse CALL statements from a text fragment and add qualified names to the given collection.
    private void findCallNamesInText(String text, java.util.Collection<String> out) {
        if (text == null || text.isEmpty()) return;
        Pattern pCall = Pattern.compile("(?is)\\bcall\\s+([A-Za-z_][\\w$]*)\\s*(?:\\.\\s*([A-Za-z_][\\w$]*))?\\s*\\(");
        Matcher m = pCall.matcher(text);
        while (m.find()) {
            String s = m.group(2) != null ? m.group(1) : null;
            String n = m.group(2) != null ? m.group(2) : m.group(1);
            addToReferencedList(out, qualify(s, n));
        }
    }

    // --- Fallback: procedure defined as ERROR header followed by block body ---
    private void maybeExtractProcedureFromBlock(TSNode blockNode, String source, SQLFileAnalysis analysis) {
        int start = blockNode.getStartByte();
        int windowStart = Math.max(0, start - 512);
        String prefix = source.substring(windowStart, start);
        // Try schema-qualified: CREATE PROCEDURE schema.name
        Pattern pQualified = Pattern.compile("(?is)create\\s+procedure\\s+([A-Za-z_][\\w$]*)\\s*\\.\\s*([A-Za-z_][\\w$]*)");
        Matcher mq = pQualified.matcher(prefix);
        String schema = null;
        String name = null;
        int nameEnd = -1;
        while (mq.find()) { // take last match in window
            schema = mq.group(1);
            name = mq.group(2);
            nameEnd = mq.end();
        }
        if (name == null) {
            // Try unqualified: CREATE PROCEDURE name
            Pattern pSimple = Pattern.compile("(?is)create\\s+procedure\\s+([A-Za-z_][\\w$]*)");
            Matcher ms = pSimple.matcher(prefix);
            while (ms.find()) {
                name = ms.group(1);
                nameEnd = ms.end();
            }
        }
        if (name == null || name.isEmpty()) return;

        if (procedureAlreadyExists(analysis, schema, name)) return;

        CreateProcedureOperation op = new CreateProcedureOperation();
        op.schema = schema;
        op.procedureName = name;

        // Try to parse parameters from header text located between the name and next keyword (LANGUAGE/AS/etc.)
        if (nameEnd > 0) {
            String afterName = prefix.substring(nameEnd);
            String paramsSeg = extractParamsSegment(afterName);
            if (paramsSeg != null) {
                parseAndAddProcedureParams(paramsSeg, op);
            }
        }

        // Collect from the block body
        for (TSNode rel : descendantsOfType(blockNode, "relation")) {
            TSNode ref = firstChildOfType(rel, "object_reference");
            if (ref == null) continue;
            QualifiedName qn = parseQualifiedName(ref, source);
            addToReferencedList(op.references.relations, qualify(qn.schema, qn.name));
        }
        for (TSNode call : descendantsOfType(blockNode, "call_statement")) {
            TSNode ref = firstChildOfType(call, "object_reference");
            if (ref != null) {
                QualifiedName qn = parseQualifiedName(ref, source);
                addToReferencedList(op.calls.procedures, qualify(qn.schema, qn.name));
            }
        }
        // Regex fallback for CALL statements if AST doesn't emit call_statement inside blocks
        if (op.calls.procedures.isEmpty()) {
            String bodyText = textOf(blockNode, source);
            findCallNamesInText(bodyText, op.calls.procedures);
        }
        for (TSNode inv : descendantsOfType(blockNode, "invocation")) {
            TSNode ref = firstChildOfType(inv, "object_reference");
            String qualified = null;
            if (ref != null) {
                QualifiedName qn = parseQualifiedName(ref, source);
                qualified = qualify(qn.schema, qn.name);
            } else {
                TSNode id = firstChildOfType(inv, "identifier");
                if (id != null) qualified = textOf(id, source);
            }
            if (qualified != null && !qualified.isEmpty()) {
                addToReferencedList(op.calls.functions, qualified);
            }
        }
        analysis.createProcedures.add(op);
    }

    // Extracts the first parenthesized segment starting at or after the beginning of the header tail.
    // Returns the inside of parentheses without the surrounding '(' and ')', or null if none.
    private String extractParamsSegment(String headerTail) {
        int i = headerTail.indexOf('(');
        if (i < 0) return null;
        int depth = 0;
        for (int j = i; j < headerTail.length(); j++) {
            char c = headerTail.charAt(j);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return headerTail.substring(i + 1, j);
                }
            }
        }
        return null;
    }

    // Parses a comma-separated parameter list. Each parameter may look like:
    //   IN name TYPE
    //   OUT name TYPE
    //   INOUT name TYPE
    //   name TYPE
    // TYPE may contain spaces (e.g., DOUBLE PRECISION). We trim each item.
    private void parseAndAddProcedureParams(String paramsSeg, CreateProcedureOperation op) {
        java.util.List<String> parts = splitTopLevelByComma(paramsSeg);
        for (String raw : parts) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            ParameterDefinition pd = new ParameterDefinition();
            java.util.regex.Pattern pat = java.util.regex.Pattern.compile("(?is)^(?:(INOUT|OUT|IN)\\s+)?([A-Za-z_][\\w$]*)\\s+(.+)$");
            java.util.regex.Matcher m = pat.matcher(s);
            if (m.find()) {
                String dir = m.group(1);
                if (dir != null) pd.direction = dir.toUpperCase(); else pd.direction = "IN";
                pd.name = m.group(2);
                pd.type = m.group(3).trim();
            } else {
                // Fallback: single identifier -> name; unknown type
                pd.name = s.split("\\s+")[0];
            }
            op.parameters.add(pd);
        }
    }

    // Splits by commas not nested inside parentheses.
    private java.util.List<String> splitTopLevelByComma(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int depth = 0; int last = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                out.add(s.substring(last, i));
                last = i + 1;
            }
        }
        if (last <= s.length()) out.add(s.substring(last));
        return out;
    }

    private boolean procedureAlreadyExists(SQLFileAnalysis analysis, String schema, String name) {
        String s1 = (schema != null && !schema.isEmpty()) ? schema : null;
        for (CreateProcedureOperation p : analysis.createProcedures) {
            boolean sameSchema = (s1 == null && (p.schema == null || p.schema.isEmpty())) || (s1 != null && s1.equals(p.schema));
            if (sameSchema && name.equals(p.procedureName)) return true;
        }
        return false;
    }

    private void processConstraintsContainer(TSNode container, String source, CreateTableOperation op) {
        int k = container.getChildCount();
        for (int j = 0; j < k; j++) {
            TSNode cons = container.getChild(j);
            if ("constraint".equals(cons.getType())) {
                processSingleConstraint(cons, source, op);
            }
        }
    }

    private void processSingleConstraint(TSNode cons, String source, CreateTableOperation op) {
        if (hasChildType(cons, "keyword_primary")) {
            extractPrimaryKey(cons, source, op);
            return;
        }
        if (hasChildType(cons, "keyword_references") || hasChildType(cons, "keyword_foreign")) {
            ForeignKeyDefinition fk = extractForeignKey(cons, source);
            op.foreignKeys.add(fk);
        }
    }

    private void extractPrimaryKey(TSNode cons, String source, CreateTableOperation op) {
        java.util.List<String> pkCols = readOrderedColumns(cons, source);
        if (!pkCols.isEmpty()) {
            op.primaryKeys.clear();
            op.primaryKeys.addAll(pkCols);
        }
    }

    private ForeignKeyDefinition extractForeignKey(TSNode cons, String source) {
        ForeignKeyDefinition fk = new ForeignKeyDefinition();
        java.util.List<TSNode> orderedNodes = childrenOfType(cons, "ordered_columns");
        
        // Extract local columns
        fk.columns.addAll(extractForeignKeyColumns(orderedNodes, source));
        
        // Extract referenced table
        TSNode refObj = firstChildOfType(cons, "object_reference");
        if (refObj != null) {
            QualifiedName qn = parseQualifiedName(refObj, source);
            fk.referencedTable = qualify(qn.schema, qn.name);
        }
        
        // Extract referenced columns
        fk.referencedColumns = extractReferencedColumns(cons, refObj, orderedNodes, source);
        
        // Extract actions
        Actions actions = readActions(cons, source);
        fk.onDelete = actions.onDelete;
        fk.onUpdate = actions.onUpdate;
        
        return fk;
    }

    private java.util.List<String> extractForeignKeyColumns(java.util.List<TSNode> orderedNodes, String source) {
        if (!orderedNodes.isEmpty()) {
            return readOrderedColumnsFromNode(orderedNodes.get(0), source);
        }
        return new java.util.ArrayList<>();
    }

    private java.util.List<String> extractReferencedColumns(TSNode cons, TSNode refObj, java.util.List<TSNode> orderedNodes, String source) {
        // Try ordered_columns first (second one if present)
        if (orderedNodes.size() > 1) {
            java.util.List<String> cols = readOrderedColumnsFromNode(orderedNodes.get(orderedNodes.size() - 1), source);
            if (!cols.isEmpty()) return cols;
        }
        
        // Try collecting identifiers after REFERENCES keyword and before ON
        int startAfterRef = -1;
        int childCount = cons.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if ("keyword_references".equals(cons.getChild(i).getType())) {
                startAfterRef = i + 1;
                break;
            }
        }
        
        if (startAfterRef != -1) {
            java.util.List<String> all = new java.util.ArrayList<>();
            for (int i = startAfterRef; i < childCount; i++) {
                TSNode ch = cons.getChild(i);
                if ("keyword_on".equals(ch.getType())) break;
                collectIdentifiers(ch, all, source);
            }
            
            // Remove identifiers from referenced table name
            if (refObj != null) {
                java.util.List<String> refObjIds = new java.util.ArrayList<>();
                collectIdentifiers(refObj, refObjIds, source);
                all.removeAll(refObjIds);
            }
            
            // Remove local column identifiers
            if (!orderedNodes.isEmpty()) {
                java.util.List<String> localCols = readOrderedColumnsFromNode(orderedNodes.get(0), source);
                all.removeAll(localCols);
            }
            
            if (!all.isEmpty()) return all;
        }
        
        return new java.util.ArrayList<>();
    }

    private String qualify(String schema, String name) {
        return (schema != null && !schema.isEmpty()) ? schema + "." + name : name;
    }
    
    private void traverseNode(TSNode node, String sourceCode, SQLFileAnalysis analysis) {
        String nodeType = node.getType();

        switch (nodeType) {
            case "create_table":
                extractCreateTable(node, sourceCode, analysis);
                break;
            case "alter_table":
                extractAlterTable(node, sourceCode, analysis);
                break;
            case "create_function":
                // Some grammars may emit create_function for procedures; detect via keyword_procedure
                if (hasChildType(node, "keyword_procedure")) {
                    extractCreateProcedure(node, sourceCode, analysis);
                } else {
                    extractCreateFunction(node, sourceCode, analysis);
                }
                break;
            case "create_procedure":
                extractCreateProcedure(node, sourceCode, analysis);
                break;
            case "call_statement":
                // Record top-level procedure calls (outside definitions)
                if (!isInsideDefinition(node)) {
                    TSNode procRef = firstChildOfType(node, "object_reference");
                    if (procRef != null) {
                        QualifiedName qn = parseQualifiedName(procRef, sourceCode);
                        addToReferencedList(analysis.topLevelCalls.procedures, qualify(qn.schema, qn.name));
                    }
                }
                break;
            case "invocation":
                // Record top-level function calls (outside definitions)
                if (!isInsideDefinition(node)) {
                    TSNode ref = firstChildOfType(node, "object_reference");
                    String qualified = null;
                    if (ref != null) {
                        QualifiedName qn = parseQualifiedName(ref, sourceCode);
                        qualified = qualify(qn.schema, qn.name);
                    } else {
                        TSNode id = firstChildOfType(node, "identifier");
                        if (id != null) qualified = textOf(id, sourceCode);
                    }
                    if (qualified != null && !qualified.isEmpty()) {
                        addToReferencedList(analysis.topLevelCalls.functions, qualified);
                    }
                }
                break;
            case "select":
            case "select_statement":
            case "insert":
            case "insert_statement":
            case "update":
            case "update_statement":
            case "delete":
            case "delete_statement":
                // Record top-level table/view references for standalone DML statements
                if (!isInsideDefinition(node)) {
                    collectRelations(node, sourceCode, analysis.topLevelReferences.relations);
                }
                break;
            case "statement":
                // Some grammars wrap top-level statements; CALL may appear as ERROR inside.
                // When outside definitions, collect relations for DML and scan text for CALL fallbacks.
                if (!isInsideDefinition(node)) {
                    // Collect table/view references only from DML nodes that are not inside definitions
                    String[] dmlTypes = new String[] {
                        "select", "select_statement", "insert", "insert_statement",
                        "update", "update_statement", "delete", "delete_statement"
                    };
                    for (String dml : dmlTypes) {
                        for (TSNode dmlNode : descendantsOfType(node, dml)) {
                            if (!isInsideDefinition(dmlNode)) {
                                collectRelations(dmlNode, sourceCode, analysis.topLevelReferences.relations);
                            }
                        }
                    }
                    String stmtText = textOf(node, sourceCode);
                    findCallNamesInText(stmtText, analysis.topLevelCalls.procedures);
                }
                break;
            case "ERROR":
                // Some grammars emit top-level CALL as ERROR. Scan text for CALL outside definitions.
                if (!isInsideDefinition(node)) {
                    // Use a forward window to allow pattern to span adjacent nodes (e.g., ERROR + '(' token)
                    int start = node.getStartByte();
                    int end = Math.min(sourceCode.length(), node.getEndByte() + 64);
                    if (start >= 0 && end > start) {
                        String slice = sourceCode.substring(start, end);
                        findCallNamesInText(slice, analysis.topLevelCalls.procedures);
                    } else {
                        String errText = textOf(node, sourceCode);
                        findCallNamesInText(errText, analysis.topLevelCalls.procedures);
                    }
                }
                break;
            case "drop_table":
                extractDrop(node, sourceCode, analysis, "TABLE");
                break;
            case "drop_view":
                extractDrop(node, sourceCode, analysis, "VIEW");
                break;
            case "drop_index":
                extractDrop(node, sourceCode, analysis, "INDEX");
                break;
            case "create_view":
                extractCreateView(node, sourceCode, analysis);
                break;
            case "create_index":
                extractCreateIndex(node, sourceCode, analysis);
                break;
            case "block":
                // Fallback for CREATE PROCEDURE when grammar emits ERROR + block
                maybeExtractProcedureFromBlock(node, sourceCode, analysis);
                break;
            default:
                break;
        }
        
        // Recursively traverse children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            traverseNode(child, sourceCode, analysis);
        }
    }

    private void extractCreateTable(TSNode node, String source, SQLFileAnalysis analysis) {
        CreateTableOperation op = new CreateTableOperation();
        // object_reference -> possibly schema-qualified
        TSNode objRef = firstChildOfType(node, "object_reference");
        if (objRef != null) {
            QualifiedName qn = parseQualifiedName(objRef, source);
            op.schema = qn.schema;
            op.tableName = qn.name;
        }

        // IF NOT EXISTS
        if (hasChildType(node, "keyword_if") && hasChildType(node, "keyword_not") && hasChildType(node, "keyword_exists")) {
            op.ifNotExists = true;
        }

        // column_definitions -> multiple column_definition and optional constraints
        TSNode cols = firstChildOfType(node, "column_definitions");
        if (cols != null) {
            int cc = cols.getChildCount();
            for (int i = 0; i < cc; i++) {
                TSNode c = cols.getChild(i);
                String t = c.getType();
                if ("column_definition".equals(t)) {
                    ColumnDefinition cd = new ColumnDefinition();
                    TSNode name = firstChildOfType(c, "identifier");
                    if (name != null) cd.name = textOf(name, source);
                    // Type node could be int/varchar/decimal/timestamp/date/etc - take first named child that's not identifier
                    TSNode type = firstTypeChildExcluding(c, source, "identifier");
                    if (type != null) cd.type = textOf(type, source);
                    // Constraints within column_definition (e.g., PRIMARY KEY, NOT NULL, UNIQUE)
                    if (hasChildType(c, "keyword_primary")) {
                        op.primaryKeys.add(cd.name);
                        cd.constraints.add("PRIMARY KEY");
                    }
                    if (hasChildType(c, "keyword_not") && hasChildType(c, "keyword_null")) {
                        cd.nullable = false;
                        cd.constraints.add("NOT NULL");
                    }
                    if (hasChildType(c, "keyword_unique")) {
                        cd.constraints.add("UNIQUE");
                    }
                    op.columns.add(cd);
                } else if ("constraints".equals(t) || "table_constraints".equals(t)) {
                    // handle table-level constraints, e.g., PRIMARY KEY / FOREIGN KEY
                    processConstraintsContainer(c, source, op);
                }
            }
            // Note: do not additionally scan all descendants for 'constraint' to avoid duplicate processing

            // (Alternate grammar handling removed; relying on constraint containers and generic fallbacks.)
        }

        analysis.createTables.add(op);
    }

    private void extractCreateView(TSNode node, String source, SQLFileAnalysis analysis) {
        CreateViewOperation op = new CreateViewOperation();
        TSNode objRef = firstChildOfType(node, "object_reference");
        if (objRef != null) {
            QualifiedName qn = parseQualifiedName(objRef, source);
            op.schema = qn.schema;
            op.viewName = qn.name;
        }
        // Find referenced tables under create_query -> from subtree (relation -> object_reference -> identifier)
        TSNode createQuery = firstChildOfType(node, "create_query");
        if (createQuery != null) {
            collectRelations(createQuery, source, op.references.relations);
        }
        analysis.createViews.add(op);
    }

    private void extractCreateIndex(TSNode node, String source, SQLFileAnalysis analysis) {
        CreateIndexOperation op = new CreateIndexOperation();
        TSNode idxName = firstChildOfType(node, "identifier");
        if (idxName != null) op.indexName = textOf(idxName, source);
        TSNode objRef = firstChildOfType(node, "object_reference");
        if (objRef != null) {
            QualifiedName qn = parseQualifiedName(objRef, source);
            op.schema = qn.schema;
            op.tableName = qn.name;
        }
        TSNode fields = firstChildOfType(node, "index_fields");
        if (fields != null) {
            // fields contain one or more field -> identifier
            int cc = fields.getChildCount();
            for (int i = 0; i < cc; i++) {
                TSNode f = fields.getChild(i);
                if ("field".equals(f.getType())) {
                    TSNode id = firstChildOfType(f, "identifier");
                    if (id != null) op.columns.add(textOf(id, source));
                }
            }
        }
        // UNIQUE index
        if (hasChildType(node, "keyword_unique")) {
            op.unique = true;
        }
        analysis.createIndexes.add(op);
    }

    private void extractCreateFunction(TSNode node, String source, SQLFileAnalysis analysis) {
        CreateFunctionOperation op = new CreateFunctionOperation();
        // name and schema
        TSNode objRef = firstChildOfType(node, "object_reference");
        if (objRef != null) {
            QualifiedName qn = parseQualifiedName(objRef, source);
            op.schema = qn.schema;
            op.functionName = qn.name;
        } else {
            TSNode id = firstChildOfType(node, "identifier");
            if (id != null) op.functionName = textOf(id, source);
        }
        // parameters
        TSNode args = firstChildOfType(node, "function_arguments");
        if (args != null) {
            java.util.List<TSNode> argNodes = descendantsOfType(args, "function_argument");
            for (TSNode an : argNodes) {
                ParameterDefinition pd = new ParameterDefinition();
                TSNode name = firstChildOfType(an, "identifier");
                if (name != null) pd.name = textOf(name, source);
                TSNode type = firstTypeChildExcluding(an, source, "identifier");
                if (type != null) pd.type = textOf(type, source);
                op.parameters.add(pd);
            }
        }
        // return type: next named child after keyword_returns
        TSNode retKw = firstChildOfType(node, "keyword_returns");
        if (retKw != null) {
            // scan siblings after retKw within parent node
            int count = node.getChildCount();
            boolean after = false;
            for (int i = 0; i < count; i++) {
                TSNode ch = node.getChild(i);
                if (ch.equals(retKw)) { after = true; continue; }
                if (after && ch.isNamed()) {
                    // skip language/body nodes
                    if (!"function_language".equals(ch.getType()) && !"function_body".equals(ch.getType())) {
                        op.returnType = textOf(ch, source);
                        break;
                    }
                }
            }
            // fallback: slice between end of RETURNS and start of language/body
            if (op.returnType == null || op.returnType.isEmpty()) {
                int start = retKw.getEndByte();
                int end = node.getEndByte();
                for (int i = 0; i < count; i++) {
                    TSNode ch = node.getChild(i);
                    if ("function_language".equals(ch.getType()) || "function_body".equals(ch.getType())) {
                        end = ch.getStartByte();
                        break;
                    }
                }
                if (start < end && end <= source.length()) {
                    String slice = source.substring(start, end);
                    // keep only the first line after RETURNS
                    int nl = slice.indexOf('\n');
                    if (nl >= 0) slice = slice.substring(0, nl);
                    slice = slice.trim();
                    // remove leading/trailing punctuation
                    slice = slice.replaceAll("^[:;\\s]+|[:;\\s]+$", "");
                    if (!slice.isEmpty()) op.returnType = slice;
                }
            }
        }
        // referenced objects from function body (AST-only)
        TSNode body = firstChildOfType(node, "function_body");
        if (body != null) {
            java.util.List<TSNode> relations = descendantsOfType(body, "relation");
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (TSNode rel : relations) {
                TSNode ref = firstChildOfType(rel, "object_reference");
                if (ref == null) continue;
                QualifiedName qn = parseQualifiedName(ref, source);
                if (qn.name == null || qn.name.isEmpty()) continue;
                String qualified = qualify(qn.schema, qn.name);
                if (seen.add(qualified)) {
                    addToReferencedList(op.references.relations, qualified);
                }
            }
            // procedure calls inside function bodies
            for (TSNode call : descendantsOfType(body, "call_statement")) {
                TSNode ref = firstChildOfType(call, "object_reference");
                if (ref != null) {
                    QualifiedName qn = parseQualifiedName(ref, source);
                    addToReferencedList(op.calls.procedures, qualify(qn.schema, qn.name));
                }
            }
            // function calls inside function bodies (explicit nodes)
            // The grammar emits 'invocation' nodes for function calls in expressions
            for (TSNode fcall : descendantsOfType(body, "invocation")) {
                TSNode ref = firstChildOfType(fcall, "object_reference");
                String qualified = null;
                if (ref != null) {
                    QualifiedName qn = parseQualifiedName(ref, source);
                    qualified = qualify(qn.schema, qn.name);
                } else {
                    TSNode id = firstChildOfType(fcall, "identifier");
                    if (id != null) qualified = textOf(id, source);
                }
                if (qualified != null && !qualified.isEmpty()) {
                    addToReferencedList(op.calls.functions, qualified);
                }
            }
        }
        analysis.createFunctions.add(op);
    }

    private void extractCreateProcedure(TSNode node, String source, SQLFileAnalysis analysis) {
        CreateProcedureOperation op = new CreateProcedureOperation();
        // name and schema
        TSNode objRef = firstChildOfType(node, "object_reference");
        if (objRef != null) {
            QualifiedName qn = parseQualifiedName(objRef, source);
            op.schema = qn.schema;
            op.procedureName = qn.name;
        } else {
            TSNode id = firstChildOfType(node, "identifier");
            if (id != null) op.procedureName = textOf(id, source);
        }
        // parameters
        TSNode args = firstChildOfType(node, "function_arguments");
        if (args != null) {
            java.util.List<TSNode> argNodes = descendantsOfType(args, "function_argument");
            for (TSNode an : argNodes) {
                ParameterDefinition pd = new ParameterDefinition();
                // direction (optional): keyword_in, keyword_out, keyword_inout
                if (hasChildType(an, "keyword_inout")) pd.direction = "INOUT";
                else if (hasChildType(an, "keyword_out")) pd.direction = "OUT";
                else pd.direction = "IN";
                TSNode name = firstChildOfType(an, "identifier");
                if (name != null) pd.name = textOf(name, source);
                TSNode type = firstTypeChildExcluding(an, source, "identifier");
                if (type != null) pd.type = textOf(type, source);
                op.parameters.add(pd);
            }
        }
        // referenced objects from procedure body (relations and calls)
        java.util.List<TSNode> relations = descendantsOfType(node, "relation");
        for (TSNode rel : relations) {
            TSNode ref = firstChildOfType(rel, "object_reference");
            if (ref == null) continue;
            QualifiedName qn = parseQualifiedName(ref, source);
            String qualified = qualify(qn.schema, qn.name);
            addToReferencedList(op.references.relations, qualified);
        }
        for (TSNode call : descendantsOfType(node, "call_statement")) {
            TSNode ref = firstChildOfType(call, "object_reference");
            if (ref != null) {
                QualifiedName qn = parseQualifiedName(ref, source);
                addToReferencedList(op.calls.procedures, qualify(qn.schema, qn.name));
            }
        }
        // Regex fallback for CALL statements inside procedure node
        if (op.calls.procedures.isEmpty()) {
            String nodeText = textOf(node, source);
            findCallNamesInText(nodeText, op.calls.procedures);
        }
        // function calls inside procedures (explicit nodes)
        // The grammar emits 'invocation' nodes for function calls in expressions
        for (TSNode fcall : descendantsOfType(node, "invocation")) {
            TSNode ref = firstChildOfType(fcall, "object_reference");
            String qualified = null;
            if (ref != null) {
                QualifiedName qn = parseQualifiedName(ref, source);
                qualified = qualify(qn.schema, qn.name);
            } else {
                TSNode id = firstChildOfType(fcall, "identifier");
                if (id != null) qualified = textOf(id, source);
            }
            if (qualified != null && !qualified.isEmpty()) {
                addToReferencedList(op.calls.functions, qualified);
            }
        }
        analysis.createProcedures.add(op);
    }

    private void extractDrop(TSNode node, String source, SQLFileAnalysis analysis, String objectType) {
        DropOperation drop = new DropOperation();
        drop.objectType = objectType;
        // IF EXISTS detection (direct children or descendants)
        if ((hasChildType(node, "keyword_if") && hasChildType(node, "keyword_exists"))
                || (!descendantsOfType(node, "keyword_if").isEmpty() && !descendantsOfType(node, "keyword_exists").isEmpty())) {
            drop.ifExists = true;
        }
        TSNode objRef = firstChildOfType(node, "object_reference");
        if (objRef != null) {
            QualifiedName qn = parseQualifiedName(objRef, source);
            drop.schema = qn.schema;
            drop.objectName = qn.name;
            // file-level referenced tables/views removed; keep only drop operation fields
        } else {
            // Some drops (e.g., INDEX) may present raw identifier(s) without object_reference
            java.util.List<TSNode> ids = descendantsOfType(node, "identifier");
            if (!ids.isEmpty()) {
                if ("INDEX".equals(objectType) && ids.size() == 1) {
                    // Single identifier is the schema; name will be parsed from node text
                    drop.schema = textOf(ids.get(0), source);
                    drop.objectName = null;
                } else {
                    // Assume last identifier is object name; preceding one (if any) is schema
                    drop.objectName = textOf(ids.get(ids.size() - 1), source);
                    if (ids.size() > 1) {
                        drop.schema = textOf(ids.get(ids.size() - 2), source);
                    }
                }
            }
            // If grammar emits ERROR after schema dot (e.g., DROP INDEX sales.ix_name),
            // recover by scanning forward from the node end to find "." and the index name
            if ("INDEX".equals(objectType) && (drop.objectName == null || drop.objectName.isEmpty())) {
                int end = node.getEndByte();
                int lookaheadEnd = Math.min(source.length(), end + 128);
                // slice after node end
                String ahead = source.substring(end, lookaheadEnd);
                int dotPos = ahead.indexOf('.');
                if (dotPos >= 0 && dotPos + 1 < ahead.length()) {
                    int i = dotPos + 1;
                    int j = i;
                    while (j < ahead.length()) {
                        char ch = ahead.charAt(j);
                        if (Character.isLetterOrDigit(ch) || ch == '_') j++; else break;
                    }
                    String nm = ahead.substring(i, j).trim();
                    if (!nm.isEmpty()) drop.objectName = nm;
                    // If schema missing, try to read trailing identifier before the dot from the node prefix
                    if ((drop.schema == null || drop.schema.isEmpty()) && dotPos > 0) {
                        int backStart = Math.max(0, end - 64);
                        String prefix = source.substring(backStart, end);
                        int p = prefix.length() - 1;
                        // skip non-identifier tail
                        while (p >= 0 && !(Character.isLetterOrDigit(prefix.charAt(p)) || prefix.charAt(p) == '_')) p--;
                        int q = p;
                        while (q >= 0 && (Character.isLetterOrDigit(prefix.charAt(q)) || prefix.charAt(q) == '_')) q--;
                        if (p >= 0 && p >= q + 1) {
                            String sc = prefix.substring(q + 1, p + 1).trim();
                            if (!sc.isEmpty()) drop.schema = sc;
                        }
                    }
                }
            }
        }
        analysis.dropOperations.add(drop);
    }

    private void extractAlterTable(TSNode node, String source, SQLFileAnalysis analysis) {
        AlterTableOperation op = new AlterTableOperation();
        // target table
        TSNode objRef = firstChildOfType(node, "object_reference");
        if (objRef != null) {
            QualifiedName qn = parseQualifiedName(objRef, source);
            op.schema = qn.schema;
            op.tableName = qn.name;
        }

        // Determine specific alteration via concrete child nodes
        TSNode addColumn = firstChildOfType(node, "add_column");
        TSNode dropColumn = firstChildOfType(node, "drop_column");
        TSNode addConstraint = firstChildOfType(node, "add_constraint");

        if (addColumn != null) {
            op.operationType = "ADD_COLUMN";
            // collect all column_definition descendants (tree-sitter shape dependent)
            java.util.List<TSNode> colDefs = descendantsOfType(addColumn, "column_definition");
            for (TSNode cdNode : colDefs) {
                ColumnDefinition cd = new ColumnDefinition();
                TSNode name = firstChildOfType(cdNode, "identifier");
                if (name != null) cd.name = textOf(name, source);
                TSNode type = firstTypeChildExcluding(cdNode, source, "identifier");
                if (type != null) cd.type = textOf(type, source);
                if (cd.name != null) op.addedColumns.add(cd);
            }
            analysis.alterTables.add(op);
            return;
        }

        if (dropColumn != null) {
            op.operationType = "DROP_COLUMN";
            // Heuristic via AST: take identifier descendants excluding table name and those in definitions/constraints
            java.util.List<String> ids = new java.util.ArrayList<>();
            collectIdentifiers(dropColumn, ids, source);
            // remove identifiers from object_reference (table name)
            TSNode tableRef = firstChildOfType(node, "object_reference");
            if (tableRef != null) {
                java.util.List<String> tableIds = new java.util.ArrayList<>();
                collectIdentifiers(tableRef, tableIds, source);
                ids.removeAll(tableIds);
            }
            // remove identifiers that belong to column_definition (added columns)
            for (TSNode cdNode : descendantsOfType(dropColumn, "column_definition")) {
                java.util.List<String> tmp = new java.util.ArrayList<>();
                collectIdentifiers(cdNode, tmp, source);
                ids.removeAll(tmp);
            }
            // remove identifiers that belong to constraint blocks
            for (TSNode consNode : descendantsOfType(dropColumn, "constraint")) {
                java.util.List<String> tmp = new java.util.ArrayList<>();
                collectIdentifiers(consNode, tmp, source);
                ids.removeAll(tmp);
            }
            if (!ids.isEmpty()) op.droppedColumns.add(ids.get(0));
            analysis.alterTables.add(op);
            return;
        }

        if (addConstraint != null) {
            op.operationType = "ADD_CONSTRAINT";
            // Try to detect PRIMARY KEY or FOREIGN KEY constraints as descendant 'constraint' nodes
            java.util.List<TSNode> consNodes = descendantsOfType(addConstraint, "constraint");
            for (TSNode cons : consNodes) {
                if (hasChildType(cons, "keyword_primary")) {
                    java.util.List<String> pkCols = readOrderedColumns(cons, source);
                    if (!pkCols.isEmpty()) op.addedConstraints.add("PRIMARY KEY (" + String.join(", ", pkCols) + ")");
                } else if (hasChildType(cons, "keyword_foreign") || hasChildType(cons, "keyword_references")) {
                    ForeignKeyDefinition fk = extractForeignKey(cons, source);
                    String fkStr = "FOREIGN KEY (" + String.join(", ", fk.columns) + ")" +
                            (fk.referencedTable != null ? (" REFERENCES " + fk.referencedTable) : "") +
                            ((fk.referencedColumns == null || fk.referencedColumns.isEmpty()) ? "" : ("(" + String.join(", ", fk.referencedColumns) + ")")) +
                            (fk.onDelete != null ? (" ON DELETE " + fk.onDelete) : "") +
                            (fk.onUpdate != null ? (" ON UPDATE " + fk.onUpdate) : "");
                    op.addedConstraints.add(fkStr);
                    // file-level referenced tables removed
                }
            }
            analysis.alterTables.add(op);
            return;
        }
    }

    private java.util.List<TSNode> descendantsOfType(TSNode node, String type) {
        java.util.List<TSNode> list = new java.util.ArrayList<>();
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = node.getChild(i);
            if (type.equals(c.getType())) list.add(c);
            list.addAll(descendantsOfType(c, type));
        }
        return list;
    }

    private void collectRelations(TSNode node, String source, java.util.Collection<String> out) {
        // Collect object_reference nodes that are under a 'relation' node to avoid aliases
        if ("relation".equals(node.getType())) {
            TSNode or = firstChildOfType(node, "object_reference");
            if (or != null) {
                QualifiedName qn = parseQualifiedName(or, source);
                addToReferencedList(out, qualify(qn.schema, qn.name));
            }
        }
        int cc = node.getChildCount();
        for (int i = 0; i < cc; i++) {
            collectRelations(node.getChild(i), source, out);
        }
    }

    private TSNode firstChildOfType(TSNode node, String type) {
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = node.getChild(i);
            if (type.equals(c.getType())) return c;
        }
        return null;
    }

    private boolean hasChildType(TSNode node, String type) {
        return firstChildOfType(node, type) != null;
    }

    private void addToReferencedList(java.util.Collection<String> collection, String value) {
        if (value != null && !value.isEmpty() && !collection.contains(value)) {
            collection.add(value);
        }
    }

    private TSNode firstTypeChildExcluding(TSNode node, String source, String excludeType) {
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = node.getChild(i);
            if (!c.isNamed()) continue;
            if (excludeType.equals(c.getType())) continue;
            // likely a type node (int/varchar/decimal/etc.)
            return c;
        }
        return null;
    }

    private String textOf(TSNode node, String source) {
        int start = node.getStartByte();
        int end = node.getEndByte();
        if (start >= 0 && end >= start && end <= source.length()) {
            return source.substring(start, end);
        }
        return "";
    }

    private java.util.List<TSNode> childrenOfType(TSNode node, String type) {
        java.util.List<TSNode> list = new java.util.ArrayList<>();
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = node.getChild(i);
            if (type.equals(c.getType())) list.add(c);
        }
        return list;
    }

    private void collectIdentifiers(TSNode node, java.util.List<String> out, String source) {
        if ("identifier".equals(node.getType())) {
            out.add(textOf(node, source));
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            collectIdentifiers(node.getChild(i), out, source);
        }
    }

    private java.util.List<String> readOrderedColumns(TSNode node, String source) {
        java.util.List<String> cols = new java.util.ArrayList<>();
        // find first ordered_columns child under node and read all columns
        TSNode ordered = firstChildOfType(node, "ordered_columns");
        if (ordered != null) cols.addAll(readOrderedColumnsFromNode(ordered, source));
        return cols;
    }

    private java.util.List<String> readOrderedColumnsFromNode(TSNode ordered, String source) {
        java.util.List<String> cols = new java.util.ArrayList<>();
        int n = ordered.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = ordered.getChild(i);
            if ("column".equals(c.getType())) {
                TSNode id = firstChildOfType(c, "identifier");
                if (id != null) cols.add(textOf(id, source));
            }
        }
        return cols;
    }

    private static class QualifiedName {
        String schema;
        String name;
    }

    private QualifiedName parseQualifiedName(TSNode objectRef, String source) {
        java.util.List<String> idents = new java.util.ArrayList<>();
        // collect all identifier descendants directly under object_reference
        int n = objectRef.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = objectRef.getChild(i);
            if ("identifier".equals(c.getType())) {
                idents.add(textOf(c, source));
            }
        }
        QualifiedName qn = new QualifiedName();
        if (idents.isEmpty()) {
            // fallback
            qn.name = textOf(objectRef, source);
            return qn;
        }
        if (idents.size() == 1) {
            qn.name = idents.get(0);
        } else {
            qn.name = idents.get(idents.size() - 1);
            qn.schema = String.join(".", idents.subList(0, idents.size() - 1));
        }
        return qn;
    }

    private static class Actions {
        String onDelete;
        String onUpdate;
    }

    private enum ActionMode { NONE, DELETE, UPDATE }

    private Actions readActions(TSNode node, String source) {
        Actions a = new Actions();
        ActionMode mode = ActionMode.NONE;
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = node.getChild(i);
            String t = c.getType();
            if ("keyword_on".equals(t)) {
                // Lookahead to determine DELETE or UPDATE
                if (i + 1 < n) {
                    String next = node.getChild(i + 1).getType();
                    if ("keyword_delete".equals(next)) {
                        mode = ActionMode.DELETE;
                        i += 1; // consume DELETE
                        continue;
                    } else if ("keyword_update".equals(next)) {
                        mode = ActionMode.UPDATE;
                        i += 1; // consume UPDATE
                        continue;
                    }
                }
            }
            if (mode == ActionMode.DELETE || mode == ActionMode.UPDATE) {
                // Collect action words until we hit another keyword_on or end of clause
                if (t.startsWith("keyword_")) {
                    String word = textOf(c, source).trim();
                    if ("keyword_on".equals(t)) { // start of next clause
                        mode = ActionMode.NONE;
                        continue;
                    }
                    if (mode == ActionMode.DELETE) {
                        a.onDelete = (a.onDelete == null || a.onDelete.isEmpty()) ? word : (a.onDelete + " " + word);
                        if ("keyword_action".equals(t)) mode = ActionMode.NONE; // NO ACTION complete
                    } else {
                        a.onUpdate = (a.onUpdate == null || a.onUpdate.isEmpty()) ? word : (a.onUpdate + " " + word);
                        if ("keyword_action".equals(t)) mode = ActionMode.NONE;
                    }
                } else if (")".equals(t) || ";".equals(t) || ",".equals(t)) {
                    mode = ActionMode.NONE;
                }
            }
        }
        if (a.onDelete != null) a.onDelete = a.onDelete.replaceAll("^\\s+|\\s+$", "").replaceAll("\\s+", " ").toUpperCase();
        if (a.onUpdate != null) a.onUpdate = a.onUpdate.replaceAll("^\\s+|\\s+$", "").replaceAll("\\s+", " ").toUpperCase();
        return a;
    }
}
