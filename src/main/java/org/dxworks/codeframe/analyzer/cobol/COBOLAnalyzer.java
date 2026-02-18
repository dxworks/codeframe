package org.dxworks.codeframe.analyzer.cobol;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.dxworks.codeframe.analyzer.cobol.generated.Cobol85BaseVisitor;
import org.dxworks.codeframe.analyzer.cobol.generated.Cobol85Lexer;
import org.dxworks.codeframe.analyzer.cobol.generated.Cobol85Parser;
import org.dxworks.codeframe.analyzer.cobol.preprocessor.impl.CobolPreprocessorImpl;
import org.dxworks.codeframe.analyzer.LanguageAnalyzer;
import org.dxworks.codeframe.model.Analysis;
import org.dxworks.codeframe.model.cobol.COBOLControlFlowStatement;
import org.dxworks.codeframe.model.cobol.COBOLDataItem;
import org.dxworks.codeframe.model.cobol.COBOLExternalCall;
import org.dxworks.codeframe.model.cobol.COBOLFileAnalysis;
import org.dxworks.codeframe.model.cobol.COBOLFileControl;
import org.dxworks.codeframe.model.cobol.COBOLFileDefinition;
import org.dxworks.codeframe.model.cobol.COBOLFileOperation;
import org.dxworks.codeframe.model.cobol.COBOLParagraph;
import org.dxworks.codeframe.model.cobol.COBOLPerformCall;
import org.dxworks.codeframe.model.cobol.COBOLSection;
import org.treesitter.TSNode;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class COBOLAnalyzer implements LanguageAnalyzer {

    private static final String PROCEDURE_DIVISION_PROLOGUE_PARAGRAPH = "__PROCEDURE_DIVISION_PROLOGUE__";

    @Override
    public Analysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        COBOLFileAnalysis analysis = new COBOLFileAnalysis();
        analysis.filePath = filePath;

        CobolPreprocessorImpl preprocessor = new CobolPreprocessorImpl();
        String preprocessedSource = preprocessSource(sourceCode, preprocessor);
        Cobol85Parser.StartRuleContext tree = parse(tokenize(preprocessedSource));

        ExtractionVisitor visitor = new ExtractionVisitor(analysis);
        visitParseTree(tree, visitor, filePath);

        applyVisitorResults(analysis, visitor);
        populateExecFlags(analysis, sourceCode);
        analysis.copyStatements.addAll(preprocessor.copyStatements());
        
        return analysis;
    }

    private String preprocessSource(String sourceCode, CobolPreprocessorImpl preprocessor) {
        return preprocessor.process(
                sourceCode,
                null,
                org.dxworks.codeframe.analyzer.cobol.preprocessor.CobolPreprocessor.CobolSourceFormatEnum.FIXED
        );
    }

    private void visitParseTree(Cobol85Parser.StartRuleContext tree, ExtractionVisitor visitor, String filePath) {
        if (tree == null) {
            System.err.println("[COBOLAnalyzer] parse returned null for: " + filePath);
            return;
        }
        visitor.visit(tree);
    }

    private void applyVisitorResults(COBOLFileAnalysis analysis, ExtractionVisitor visitor) {
        analysis.programId = visitor.programId;
        analysis.fileControls.addAll(visitor.fileControls);
        analysis.fileDefinitions.addAll(visitor.fileDefinitions);
        analysis.dataItems.addAll(visitor.workingStorageDataItems);
        analysis.dataItems.addAll(visitor.linkageDataItems);
        analysis.dataItems.addAll(visitor.localStorageDataItems);
        analysis.dataItems.addAll(visitor.fileSectionDataItems);
        analysis.sections.addAll(visitor.sections);
        analysis.paragraphs.addAll(visitor.paragraphs);
        analysis.procedureParameters.addAll(visitor.procedureParameters);
    }

    private void populateExecFlags(COBOLFileAnalysis analysis, String sourceCode) {
        analysis.hasExecSql = containsExecBlock(sourceCode, "SQL");
        analysis.hasExecCics = containsExecBlock(sourceCode, "CICS");
        analysis.hasExecSqlIms = containsExecBlock(sourceCode, "SQLIMS");
    }

    // Simple regex-based EXEC block detection.
    private static boolean containsExecBlock(String sourceCode, String kind) {
        return sourceCode.matches("(?is).*\\bEXEC\\s+" + kind + "\\b.*");
    }

    // Tokenize for parser (preprocessed or raw source).
    private CommonTokenStream tokenize(String sourceCode) {
        Cobol85Lexer lexer = new Cobol85Lexer(CharStreams.fromString(sourceCode == null ? "" : sourceCode));
        lexer.removeErrorListeners();
        lexer.addErrorListener(QUIET_LISTENER);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        return tokens;
    }

    // Parse preprocessed tokens.
    private Cobol85Parser.StartRuleContext parse(CommonTokenStream tokens) {
        Cobol85Parser parser = new Cobol85Parser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(QUIET_LISTENER);
        parser.setErrorHandler(new DefaultErrorStrategy());

        try {
            return parser.startRule();
        } catch (Exception e) {
            System.err.println("[COBOLAnalyzer] Parse exception: " + e.getMessage());
            return null;
        }
    }

    // Normalize identifier/copybook names (strip quotes, trim whitespace).
    private static String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            text = text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    // Best-effort parsing: ignore syntax errors to extract as much structure as possible.
    private static final BaseErrorListener QUIET_LISTENER = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg,
                                RecognitionException e) {
            // best-effort parsing
        }
    };

    // Visitor extracts structure from preprocessed parse tree (EXEC blocks tagged, comment entries handled, COPY/REPLACE removed by reference preprocessor).
    private static class ExtractionVisitor extends Cobol85BaseVisitor<Void> {
        private final COBOLFileAnalysis analysis;
        private String programId;
        private final List<COBOLDataItem> workingStorageDataItems = new ArrayList<>();
        private final List<COBOLDataItem> linkageDataItems = new ArrayList<>();
        private final List<COBOLDataItem> localStorageDataItems = new ArrayList<>();
        private final List<COBOLDataItem> fileSectionDataItems = new ArrayList<>();
        private final List<COBOLFileControl> fileControls = new ArrayList<>();
        private final List<COBOLFileDefinition> fileDefinitions = new ArrayList<>();
        private final List<COBOLSection> sections = new ArrayList<>();
        private final List<COBOLParagraph> paragraphs = new ArrayList<>();
        private final List<COBOLExternalCall> externalCalls = new ArrayList<>();
        private final List<String> procedureParameters = new ArrayList<>();
        private final Deque<COBOLDataItem> hierarchy = new ArrayDeque<>();
        private Cobol85Parser.FileDescriptionEntryContext currentFdContext = null;
        private boolean hasFdEntries = false; // Track if we have FD entries

        // Constructor receives analysis object (flags are detected from raw source since preprocessor removes EXEC blocks).
        public ExtractionVisitor(COBOLFileAnalysis analysis) {
            this.analysis = analysis;
        }
        // Scope tracking for data division sections and procedure division context.
        private boolean inWorkingStorageSection;
        private boolean inLinkageSection;
        private boolean inLocalStorageSection;
        private boolean inFileSection;
        private boolean inProcedureDivision;
        private COBOLSection currentSection;
        private COBOLParagraph currentParagraph;
        private COBOLParagraph procedureDivisionPrologueParagraph;

        @Override
        public Void visitProcedureDivision(Cobol85Parser.ProcedureDivisionContext ctx) {
            boolean previousInProcedureDivision = inProcedureDivision;
            COBOLParagraph previousPrologueParagraph = procedureDivisionPrologueParagraph;

            inProcedureDivision = true;
            procedureDivisionPrologueParagraph = null;

            extractProcedureDivisionUsingParameters(ctx);

            super.visitProcedureDivision(ctx);

            if (hasCapturedContent(procedureDivisionPrologueParagraph)) {
                paragraphs.add(0, procedureDivisionPrologueParagraph);
            }

            inProcedureDivision = previousInProcedureDivision;
            procedureDivisionPrologueParagraph = previousPrologueParagraph;
            return null;
        }

        private void extractProcedureDivisionUsingParameters(ParseTree node) {
            if (node == null) {
                return;
            }

            if (node instanceof Cobol85Parser.ProcedureDivisionUsingClauseContext) {
                collectProcedureParametersFromUsingClause((Cobol85Parser.ProcedureDivisionUsingClauseContext) node);
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                extractProcedureDivisionUsingParameters(node.getChild(i));
            }
        }

        private void collectProcedureParametersFromUsingClause(Cobol85Parser.ProcedureDivisionUsingClauseContext usingClause) {
            for (Cobol85Parser.ProcedureDivisionUsingParameterContext parameterCtx : usingClause.procedureDivisionUsingParameter()) {
                collectProcedureParameterNames(parameterCtx);
            }
        }

        private void collectProcedureParameterNames(ParseTree node) {
            if (node == null) {
                return;
            }

            if (node instanceof Cobol85Parser.IdentifierContext) {
                addProcedureParameter(node.getText());
            } else if (node instanceof Cobol85Parser.FileNameContext) {
                addProcedureParameter(node.getText());
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                collectProcedureParameterNames(node.getChild(i));
            }
        }

        private void addProcedureParameter(String rawName) {
            String paramName = normalizeName(rawName);
            if (paramName != null && !paramName.isEmpty() && !procedureParameters.contains(paramName)) {
                procedureParameters.add(paramName);
            }
        }

        private static boolean hasCapturedContent(COBOLParagraph paragraph) {
            if (paragraph == null) {
                return false;
            }
            return !paragraph.performCalls.isEmpty()
                    || !paragraph.externalCalls.isEmpty()
                    || !paragraph.fileOperations.isEmpty()
                    || !paragraph.controlFlowStatements.isEmpty()
                    || !paragraph.dataReferences.isEmpty();
        }

        private COBOLParagraph targetParagraph() {
            if (currentParagraph != null) {
                return currentParagraph;
            }
            if (!inProcedureDivision) {
                return null;
            }
            if (procedureDivisionPrologueParagraph == null) {
                procedureDivisionPrologueParagraph = new COBOLParagraph();
                procedureDivisionPrologueParagraph.name = PROCEDURE_DIVISION_PROLOGUE_PARAGRAPH;
            }
            return procedureDivisionPrologueParagraph;
        }

        private enum DataSection {
            WORKING_STORAGE,
            LINKAGE,
            LOCAL_STORAGE,
            FILE
        }

        private boolean isInSection(DataSection section) {
            switch (section) {
                case WORKING_STORAGE:
                    return inWorkingStorageSection;
                case LINKAGE:
                    return inLinkageSection;
                case LOCAL_STORAGE:
                    return inLocalStorageSection;
                case FILE:
                    return inFileSection;
                default:
                    return false;
            }
        }

        private void setInSection(DataSection section, boolean value) {
            switch (section) {
                case WORKING_STORAGE:
                    inWorkingStorageSection = value;
                    break;
                case LINKAGE:
                    inLinkageSection = value;
                    break;
                case LOCAL_STORAGE:
                    inLocalStorageSection = value;
                    break;
                case FILE:
                    inFileSection = value;
                    break;
                default:
                    break;
            }
        }

        private Void withDataSection(DataSection section, Runnable visitBody) {
            boolean previous = isInSection(section);
            setInSection(section, true);
            hierarchy.clear();

            visitBody.run();

            hierarchy.clear();
            setInSection(section, previous);
            return null;
        }

        // Track WORKING-STORAGE section scope and clear hierarchy before/after.
        @Override
        public Void visitWorkingStorageSection(Cobol85Parser.WorkingStorageSectionContext ctx) {
            return withDataSection(DataSection.WORKING_STORAGE, () -> super.visitWorkingStorageSection(ctx));
        }

        // Track LINKAGE section scope and clear hierarchy before/after.
        @Override
        public Void visitLinkageSection(Cobol85Parser.LinkageSectionContext ctx) {
            return withDataSection(DataSection.LINKAGE, () -> super.visitLinkageSection(ctx));
        }

        // Track LOCAL-STORAGE section scope and clear hierarchy before/after.
        @Override
        public Void visitLocalStorageSection(Cobol85Parser.LocalStorageSectionContext ctx) {
            return withDataSection(DataSection.LOCAL_STORAGE, () -> super.visitLocalStorageSection(ctx));
        }

        // Track FILE SECTION scope and clear hierarchy before/after.
        @Override
        public Void visitFileSection(Cobol85Parser.FileSectionContext ctx) {
            return withDataSection(DataSection.FILE, () -> super.visitFileSection(ctx));
        }

        // Extract FD entries for files without SELECT clauses (basic metadata only) and populate fileDefinitions
        @Override
        public Void visitFileDescriptionEntry(Cobol85Parser.FileDescriptionEntryContext ctx) {
            if (ctx.fileName() != null) {
                String fileName = normalizeName(ctx.fileName().getText());
                
                // Mark that we have FD entries
                hasFdEntries = true;
                
                // Set current FD context for record extraction
                currentFdContext = ctx;
                
                // Populate fileDefinitions with FD entries and record layouts
                COBOLFileDefinition fileDef = new COBOLFileDefinition();
                fileDef.name = fileName;
                
                // Extract record layouts (01-level entries under this specific FD)
                extractRecordLayouts(ctx, fileDef);
                
                fileDefinitions.add(fileDef);
                
                // Clear current FD context after processing
                currentFdContext = null;
            }
            return super.visitFileDescriptionEntry(ctx);
        }

        // Helper method to extract record layouts from FD entries (FD-scoped extraction)
        private void extractRecordLayouts(Cobol85Parser.FileDescriptionEntryContext ctx, COBOLFileDefinition fileDef) {
            // Extract only 01-level records that belong to this specific FD
            // by visiting the data description entries that are children of this FD
            for (Cobol85Parser.DataDescriptionEntryContext dataDescCtx : ctx.dataDescriptionEntry()) {
                if (dataDescCtx.dataDescriptionEntryFormat1() != null) {
                    COBOLDataItem dataItem = toDataItem(dataDescCtx.dataDescriptionEntryFormat1());
                    if (dataItem != null && dataItem.level == 1 && dataItem.name != null) {
                        // Create a copy of the record for this file definition
                        COBOLDataItem record = new COBOLDataItem();
                        record.name = dataItem.name;
                        record.level = dataItem.level;
                        record.picture = dataItem.picture;
                        record.section = "FILE SECTION";
                        record.usage = dataItem.usage;
                        record.children = new ArrayList<>();
                        
                        // Copy the children (sub-fields) by processing the data description entry
                        processChildrenForRecord(dataDescCtx.dataDescriptionEntryFormat1(), record);
                        
                        fileDef.records.add(record);
                    }
                }
            }
        }
        
        // Helper method to process children of a record data item
        private void processChildrenForRecord(Cobol85Parser.DataDescriptionEntryFormat1Context ctx, COBOLDataItem parentRecord) {
            // Build hierarchy for this record to extract children
            Deque<COBOLDataItem> localHierarchy = new ArrayDeque<>();
            localHierarchy.push(parentRecord);
            
            // Visit all data description entries under this FD to find children
            if (currentFdContext != null) {
                for (Cobol85Parser.DataDescriptionEntryContext dataDescCtx : currentFdContext.dataDescriptionEntry()) {
                    if (dataDescCtx.dataDescriptionEntryFormat1() != null) {
                        COBOLDataItem dataItem = toDataItem(dataDescCtx.dataDescriptionEntryFormat1());
                        if (dataItem != null) {
                            // Maintain hierarchy based on levels
                            while (!localHierarchy.isEmpty() && dataItem.level <= localHierarchy.peek().level) {
                                localHierarchy.pop();
                            }
                            
                            // Add as child if we have a parent and this is not the record itself
                            if (!localHierarchy.isEmpty() && dataItem.name != null && !dataItem.name.equals(parentRecord.name)) {
                                localHierarchy.peek().children.add(dataItem);
                                localHierarchy.push(dataItem);
                            } else if (dataItem.name != null && !dataItem.name.equals(parentRecord.name)) {
                                localHierarchy.push(dataItem);
                            }
                        }
                    }
                }
            }
        }

        // Extract FILE-CONTROL metadata from SELECT clauses.
        @Override
        public Void visitSelectClause(Cobol85Parser.SelectClauseContext ctx) {
            if (ctx.fileName() != null) {
                String fileName = normalizeName(ctx.fileName().getText());
                COBOLFileControl fileControl = findOrCreateFileControl(fileName);

                if (fileControl == null) {
                    return super.visitSelectClause(ctx);
                }

                if (ctx.getParent() instanceof Cobol85Parser.FileControlEntryContext) {
                    Cobol85Parser.FileControlEntryContext fileControlCtx = 
                        (Cobol85Parser.FileControlEntryContext) ctx.getParent();
                    populateFileControlMetadata(fileControl, fileControlCtx);
                }
            }
            return super.visitSelectClause(ctx);
        }

        private COBOLFileControl findOrCreateFileControl(String fileName) {
            if (fileName == null) {
                return null;
            }

            COBOLFileControl fileControl = fileControls.stream()
                    .filter(fc -> fileName.equals(fc.name))
                    .findFirst()
                    .orElse(null);

            if (fileControl != null) {
                return fileControl;
            }

            fileControl = new COBOLFileControl();
            fileControl.name = fileName;
            fileControls.add(fileControl);
            return fileControl;
        }

        private void populateFileControlMetadata(COBOLFileControl fileControl,
                                                 Cobol85Parser.FileControlEntryContext fileControlCtx) {
            for (Cobol85Parser.FileControlClauseContext clause : fileControlCtx.fileControlClause()) {
                if (clause.organizationClause() != null) {
                    fileControl.organization = extractOrganization(clause.organizationClause());
                }
                if (clause.accessModeClause() != null) {
                    fileControl.accessMode = extractAccessMode(clause.accessModeClause());
                }
                if (clause.recordKeyClause() != null) {
                    fileControl.hasKey = true;
                }
            }
        }

        private String extractOrganization(Cobol85Parser.OrganizationClauseContext ctx) {
            if (ctx.SEQUENTIAL() != null) return "SEQUENTIAL";
            if (ctx.RELATIVE() != null) return "RELATIVE";
            if (ctx.INDEXED() != null) return "INDEXED";
            return null;
        }

        private String extractAccessMode(Cobol85Parser.AccessModeClauseContext ctx) {
            if (ctx.SEQUENTIAL() != null) return "SEQUENTIAL";
            if (ctx.RANDOM() != null) return "RANDOM";
            if (ctx.DYNAMIC() != null) return "DYNAMIC";
            if (ctx.EXCLUSIVE() != null) return "EXCLUSIVE";
            return null;
        }

        // Extract PROGRAM-ID from IDENTIFICATION DIVISION.
        @Override
        public Void visitProgramIdParagraph(Cobol85Parser.ProgramIdParagraphContext ctx) {
            if (programId == null && ctx.programName() != null) {
                programId = normalizeName(ctx.programName().getText());
            }
            return null;
        }

        // Track PROCEDURE DIVISION sections and their paragraphs.
        @Override
        public Void visitProcedureSection(Cobol85Parser.ProcedureSectionContext ctx) {
            COBOLSection previousSection = currentSection;

            COBOLSection section = new COBOLSection();
            if (ctx.procedureSectionHeader() != null && ctx.procedureSectionHeader().sectionName() != null) {
                section.name = normalizeName(ctx.procedureSectionHeader().sectionName().getText());
            }
            sections.add(section);
            currentSection = section;

            super.visitProcedureSection(ctx);

            currentSection = previousSection;
            return null;
        }

        // Track paragraphs and their calls/operations/data-references.
        @Override
        public Void visitParagraph(Cobol85Parser.ParagraphContext ctx) {
            if (ctx.paragraphName() == null) {
                return null;
            }

            COBOLParagraph paragraph = new COBOLParagraph();
            paragraph.name = normalizeName(ctx.paragraphName().getText());

            COBOLParagraph previousParagraph = currentParagraph;
            currentParagraph = paragraph;

            // Always add to top-level paragraphs list (for paragraphs outside sections)
            if (currentSection == null) {
                paragraphs.add(paragraph);
            } else {
                // Add to section paragraphs if inside a section
                currentSection.paragraphs.add(paragraph);
            }

            super.visitParagraph(ctx);

            currentParagraph = previousParagraph;
            return null;
        }

        // Extract PERFORM calls (target paragraph and optional THRU paragraph).
        @Override
        public Void visitPerformStatement(Cobol85Parser.PerformStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph == null) {
                return super.visitPerformStatement(ctx);
            }

            COBOLPerformCall performCall = new COBOLPerformCall();

            if (ctx.performProcedureStatement() != null) {
                Cobol85Parser.PerformProcedureStatementContext proc = ctx.performProcedureStatement();
                if (!proc.procedureName().isEmpty()) {
                    performCall.targetParagraph = normalizeName(proc.procedureName(0).getText());
                }
                if ((proc.THROUGH() != null || proc.THRU() != null) && proc.procedureName().size() > 1) {
                    performCall.thruParagraph = normalizeName(proc.procedureName(1).getText());
                }
            }

            // Only add PERFORM calls with valid targets (skip control flow PERFORMs)
            if (performCall.targetParagraph != null && !performCall.targetParagraph.isEmpty()) {
                paragraph.performCalls.add(performCall);
            }
            return super.visitPerformStatement(ctx);
        }

        // Extract external calls and data references from CALL statements.
        @Override
        public Void visitCallStatement(Cobol85Parser.CallStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            COBOLExternalCall externalCall = new COBOLExternalCall();
            if (ctx.literal() != null) {
                externalCall.programName = normalizeName(ctx.literal().getText());
                externalCall.isDynamic = false;
            } else if (ctx.identifier() != null) {
                externalCall.programName = normalizeName(ctx.identifier().getText());
                externalCall.isDynamic = true;
            }

            if (ctx.callUsingPhrase() != null) {
                externalCall.parameterCount = countCallUsingParameters(ctx.callUsingPhrase());
            }

            if (paragraph != null) {
                paragraph.externalCalls.add(externalCall);
            } else {
                externalCalls.add(externalCall);
            }

            return super.visitCallStatement(ctx);
        }

        private void addFileOperation(COBOLParagraph paragraph, String verb, String rawFileName) {
            if (paragraph == null) {
                return;
            }

            COBOLFileOperation op = new COBOLFileOperation();
            op.verb = verb;
            op.fileName = normalizeName(rawFileName);
            paragraph.fileOperations.add(op);
        }

        private void addOpenInputOperations(COBOLParagraph paragraph,
                                            List<Cobol85Parser.OpenInputStatementContext> inputStatements) {
            for (Cobol85Parser.OpenInputStatementContext inputStmt : inputStatements) {
                for (Cobol85Parser.OpenInputContext input : inputStmt.openInput()) {
                    if (input.fileName() != null) {
                        addFileOperation(paragraph, "OPEN", input.fileName().getText());
                    }
                }
            }
        }

        private void addOpenOutputOperations(COBOLParagraph paragraph,
                                             List<Cobol85Parser.OpenOutputStatementContext> outputStatements) {
            for (Cobol85Parser.OpenOutputStatementContext outputStmt : outputStatements) {
                for (Cobol85Parser.OpenOutputContext output : outputStmt.openOutput()) {
                    if (output.fileName() != null) {
                        addFileOperation(paragraph, "OPEN", output.fileName().getText());
                    }
                }
            }
        }

        private void addOpenOperationsFromFileNames(COBOLParagraph paragraph,
                                                    List<Cobol85Parser.FileNameContext> fileNames) {
            for (Cobol85Parser.FileNameContext fileName : fileNames) {
                if (fileName != null) {
                    addFileOperation(paragraph, "OPEN", fileName.getText());
                }
            }
        }

        // Extract READ file operations (verb and file name).
        @Override
        public Void visitReadStatement(Cobol85Parser.ReadStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                addFileOperation(paragraph, "READ", ctx.fileName() == null ? null : ctx.fileName().getText());
            }
            return super.visitReadStatement(ctx);
        }

        // Extract WRITE file operations (verb and record name).
        @Override
        public Void visitWriteStatement(Cobol85Parser.WriteStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                addFileOperation(paragraph, "WRITE", ctx.recordName() == null ? null : ctx.recordName().getText());
            }
            return super.visitWriteStatement(ctx);
        }

        // Extract OPEN file operations (verb and file name).
        @Override
        public Void visitOpenStatement(Cobol85Parser.OpenStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                addOpenInputOperations(paragraph, ctx.openInputStatement());
                addOpenOutputOperations(paragraph, ctx.openOutputStatement());

                for (Cobol85Parser.OpenIOStatementContext ioStmt : ctx.openIOStatement()) {
                    addOpenOperationsFromFileNames(paragraph, ioStmt.fileName());
                }

                for (Cobol85Parser.OpenExtendStatementContext extendStmt : ctx.openExtendStatement()) {
                    addOpenOperationsFromFileNames(paragraph, extendStmt.fileName());
                }
            }
            return super.visitOpenStatement(ctx);
        }

        // Extract CLOSE file operations (verb and file name).
        @Override
        public Void visitCloseStatement(Cobol85Parser.CloseStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                for (Cobol85Parser.CloseFileContext fileCtx : ctx.closeFile()) {
                    if (fileCtx.fileName() != null) {
                        addFileOperation(paragraph, "CLOSE", fileCtx.fileName().getText());
                    }
                }
            }
            return super.visitCloseStatement(ctx);
        }

        // Extract REWRITE file operations (verb and record name).
        @Override
        public Void visitRewriteStatement(Cobol85Parser.RewriteStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                addFileOperation(paragraph, "REWRITE", ctx.recordName() == null ? null : ctx.recordName().getText());
            }
            return super.visitRewriteStatement(ctx);
        }

        // Extract DELETE file operations (verb and file name).
        @Override
        public Void visitDeleteStatement(Cobol85Parser.DeleteStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                addFileOperation(paragraph, "DELETE", ctx.fileName() == null ? null : ctx.fileName().getText());
            }
            return super.visitDeleteStatement(ctx);
        }

        // Extract START file operations (verb and file name).
        @Override
        public Void visitStartStatement(Cobol85Parser.StartStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                addFileOperation(paragraph, "START", ctx.fileName() == null ? null : ctx.fileName().getText());
            }
            return super.visitStartStatement(ctx);
        }

        // Extract data references from MOVE statements (both source and target identifiers).
        @Override
        public Void visitMoveStatement(Cobol85Parser.MoveStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                if (ctx.moveToStatement() != null) {
                    Cobol85Parser.MoveToStatementContext moveTo = ctx.moveToStatement();
                    
                    // Extract source identifier from moveToSendingArea
                    if (moveTo.moveToSendingArea() != null && moveTo.moveToSendingArea().identifier() != null) {
                        paragraph.dataReferences.add(normalizeName(moveTo.moveToSendingArea().identifier().getText()));
                    }
                    
                    // Extract target identifiers
                    for (Cobol85Parser.IdentifierContext id : moveTo.identifier()) {
                        paragraph.dataReferences.add(normalizeName(id.getText()));
                    }
                }
            }
            return super.visitMoveStatement(ctx);
        }

        // Extract data references from COMPUTE statements (simplified).
        @Override
        public Void visitComputeStatement(Cobol85Parser.ComputeStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                // Extract identifiers from computeStore (left side targets)
                for (Cobol85Parser.ComputeStoreContext store : ctx.computeStore()) {
                    if (store.identifier() != null) {
                        paragraph.dataReferences.add(normalizeName(store.identifier().getText()));
                    }
                }
            }
            return super.visitComputeStatement(ctx);
        }

        // Extract data references from ADD statements (simplified).
        @Override
        public Void visitAddStatement(Cobol85Parser.AddStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                if (ctx.addToStatement() != null) {
                    Cobol85Parser.AddToStatementContext addTo = ctx.addToStatement();
                    // Extract from addFrom (source operands)
                    for (Cobol85Parser.AddFromContext from : addTo.addFrom()) {
                        if (from.identifier() != null) {
                            paragraph.dataReferences.add(normalizeName(from.identifier().getText()));
                        }
                    }
                    // Extract from addTo (target operands)
                    for (Cobol85Parser.AddToContext to : addTo.addTo()) {
                        if (to.identifier() != null) {
                            paragraph.dataReferences.add(normalizeName(to.identifier().getText()));
                        }
                    }
                }
            }
            return super.visitAddStatement(ctx);
        }

        // Extract data references from SUBTRACT statements (simplified).
        @Override
        public Void visitSubtractStatement(Cobol85Parser.SubtractStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                if (ctx.subtractFromStatement() != null) {
                    Cobol85Parser.SubtractFromStatementContext subtractFrom = ctx.subtractFromStatement();
                    for (Cobol85Parser.SubtractSubtrahendContext subtrahend : subtractFrom.subtractSubtrahend()) {
                        addIdentifierReference(paragraph, subtrahend.identifier());
                    }
                    for (Cobol85Parser.SubtractMinuendContext minuend : subtractFrom.subtractMinuend()) {
                        addIdentifierReference(paragraph, minuend.identifier());
                    }
                }

                if (ctx.subtractFromGivingStatement() != null) {
                    Cobol85Parser.SubtractFromGivingStatementContext giving = ctx.subtractFromGivingStatement();
                    for (Cobol85Parser.SubtractSubtrahendContext subtrahend : giving.subtractSubtrahend()) {
                        addIdentifierReference(paragraph, subtrahend.identifier());
                    }
                    addIdentifierReference(paragraph, giving.subtractMinuendGiving().identifier());
                    for (Cobol85Parser.SubtractGivingContext result : giving.subtractGiving()) {
                        addIdentifierReference(paragraph, result.identifier());
                    }
                }

                if (ctx.subtractCorrespondingStatement() != null) {
                    Cobol85Parser.SubtractCorrespondingStatementContext corresponding = ctx.subtractCorrespondingStatement();
                    addQualifiedNameReference(paragraph, corresponding.qualifiedDataName());
                    if (corresponding.subtractMinuendCorresponding() != null) {
                        addQualifiedNameReference(paragraph, corresponding.subtractMinuendCorresponding().qualifiedDataName());
                    }
                }
            }
            return super.visitSubtractStatement(ctx);
        }

        // Extract data references from MULTIPLY statements (simplified).
        @Override
        public Void visitMultiplyStatement(Cobol85Parser.MultiplyStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                addIdentifierReference(paragraph, ctx.identifier());

                if (ctx.multiplyRegular() != null) {
                    for (Cobol85Parser.MultiplyRegularOperandContext operand : ctx.multiplyRegular().multiplyRegularOperand()) {
                        addIdentifierReference(paragraph, operand.identifier());
                    }
                }

                if (ctx.multiplyGiving() != null) {
                    addIdentifierReference(paragraph, ctx.multiplyGiving().multiplyGivingOperand().identifier());
                    for (Cobol85Parser.MultiplyGivingResultContext result : ctx.multiplyGiving().multiplyGivingResult()) {
                        addIdentifierReference(paragraph, result.identifier());
                    }
                }
            }
            return super.visitMultiplyStatement(ctx);
        }

        // Extract data references from DIVIDE statements (simplified).
        @Override
        public Void visitDivideStatement(Cobol85Parser.DivideStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                addIdentifierReference(paragraph, ctx.identifier());

                if (ctx.divideIntoStatement() != null) {
                    for (Cobol85Parser.DivideIntoContext into : ctx.divideIntoStatement().divideInto()) {
                        addIdentifierReference(paragraph, into.identifier());
                    }
                }

                if (ctx.divideIntoGivingStatement() != null) {
                    Cobol85Parser.DivideIntoGivingStatementContext intoGiving = ctx.divideIntoGivingStatement();
                    addIdentifierReference(paragraph, intoGiving.identifier());
                    if (intoGiving.divideGivingPhrase() != null) {
                        for (Cobol85Parser.DivideGivingContext giving : intoGiving.divideGivingPhrase().divideGiving()) {
                            addIdentifierReference(paragraph, giving.identifier());
                        }
                    }
                }

                if (ctx.divideByGivingStatement() != null) {
                    Cobol85Parser.DivideByGivingStatementContext byGiving = ctx.divideByGivingStatement();
                    addIdentifierReference(paragraph, byGiving.identifier());
                    if (byGiving.divideGivingPhrase() != null) {
                        for (Cobol85Parser.DivideGivingContext giving : byGiving.divideGivingPhrase().divideGiving()) {
                            addIdentifierReference(paragraph, giving.identifier());
                        }
                    }
                }

                if (ctx.divideRemainder() != null) {
                    addIdentifierReference(paragraph, ctx.divideRemainder().identifier());
                }
            }
            return super.visitDivideStatement(ctx);
        }

        // Extract data references from SET statements (simplified).
        @Override
        public Void visitSetStatement(Cobol85Parser.SetStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                for (Cobol85Parser.SetToStatementContext setToStatement : ctx.setToStatement()) {
                    for (Cobol85Parser.SetToContext setTo : setToStatement.setTo()) {
                        addIdentifierReference(paragraph, setTo.identifier());
                    }
                    for (Cobol85Parser.SetToValueContext value : setToStatement.setToValue()) {
                        for (Cobol85Parser.IdentifierContext identifier : value.getRuleContexts(Cobol85Parser.IdentifierContext.class)) {
                            addIdentifierReference(paragraph, identifier);
                        }
                    }
                }

                if (ctx.setUpDownByStatement() != null) {
                    Cobol85Parser.SetUpDownByStatementContext upDown = ctx.setUpDownByStatement();
                    for (Cobol85Parser.SetToContext setTo : upDown.setTo()) {
                        addIdentifierReference(paragraph, setTo.identifier());
                    }
                    addIdentifierReference(paragraph, upDown.setByValue().identifier());
                }
            }
            return super.visitSetStatement(ctx);
        }

        // Extract control-flow statements (GOBACK, STOP RUN, EXIT PROGRAM, RETURN).
        @Override
        public Void visitGobackStatement(Cobol85Parser.GobackStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                COBOLControlFlowStatement controlFlow = new COBOLControlFlowStatement();
                controlFlow.type = "GOBACK";
                controlFlow.target = null; // GOBACK doesn't take a target
                paragraph.controlFlowStatements.add(controlFlow);
            }
            return super.visitGobackStatement(ctx);
        }

        @Override
        public Void visitStopStatement(Cobol85Parser.StopStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                COBOLControlFlowStatement controlFlow = new COBOLControlFlowStatement();
                if (ctx.RUN() != null) {
                    controlFlow.type = "STOP_RUN";
                } else if (ctx.literal() != null) {
                    controlFlow.type = "STOP_LITERAL";
                    controlFlow.target = normalizeName(ctx.literal().getText());
                } else {
                    controlFlow.type = "STOP";
                }
                paragraph.controlFlowStatements.add(controlFlow);
            }
            return super.visitStopStatement(ctx);
        }

        @Override
        public Void visitExitStatement(Cobol85Parser.ExitStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                COBOLControlFlowStatement controlFlow = new COBOLControlFlowStatement();
                if (ctx.PROGRAM() != null) {
                    controlFlow.type = "EXIT_PROGRAM";
                } else {
                    controlFlow.type = "EXIT";
                }
                controlFlow.target = null; // EXIT doesn't take a target
                paragraph.controlFlowStatements.add(controlFlow);
            }
            return super.visitExitStatement(ctx);
        }

        @Override
        public Void visitReturnStatement(Cobol85Parser.ReturnStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                COBOLControlFlowStatement controlFlow = new COBOLControlFlowStatement();
                controlFlow.type = "RETURN";
                // RETURN works with fileName, not identifier
                if (ctx.fileName() != null) {
                    controlFlow.target = normalizeName(ctx.fileName().getText());
                } else {
                    controlFlow.target = null;
                }
                paragraph.controlFlowStatements.add(controlFlow);
            }
            return super.visitReturnStatement(ctx);
        }

        private void addIdentifierReference(COBOLParagraph paragraph, Cobol85Parser.IdentifierContext identifier) {
            if (paragraph == null || identifier == null) {
                return;
            }
            paragraph.dataReferences.add(normalizeDataReference(identifier.getText()));
        }

        private void addQualifiedNameReference(COBOLParagraph paragraph, Cobol85Parser.QualifiedDataNameContext qualifiedDataName) {
            if (paragraph == null || qualifiedDataName == null) {
                return;
            }
            paragraph.dataReferences.add(normalizeDataReference(qualifiedDataName.getText()));
        }

        private String normalizeDataReference(String raw) {
            String normalized = normalizeName(raw);
            if (normalized == null) {
                return null;
            }

            String prefix = "ADDRESSOF";
            if (normalized.regionMatches(true, 0, prefix, 0, prefix.length()) && normalized.length() > prefix.length()) {
                return normalized.substring(prefix.length());
            }
            return normalized;
        }

        // Count CALL USING parameters across BY REFERENCE/VALUE/CONTENT phrases.
        private int countCallUsingParameters(Cobol85Parser.CallUsingPhraseContext usingPhrase) {
            int total = 0;
            for (Cobol85Parser.CallUsingParameterContext parameter : usingPhrase.callUsingParameter()) {
                if (parameter.callByReferencePhrase() != null) {
                    total += parameter.callByReferencePhrase().callByReference().size();
                }
                if (parameter.callByValuePhrase() != null) {
                    total += parameter.callByValuePhrase().callByValue().size();
                }
                if (parameter.callByContentPhrase() != null) {
                    total += parameter.callByContentPhrase().callByContent().size();
                }
            }
            return total;
        }

        // Extract data items with hierarchy and section context (format 1 entries).
        @Override
        public Void visitDataDescriptionEntryFormat1(Cobol85Parser.DataDescriptionEntryFormat1Context ctx) {
            if (!inWorkingStorageSection && !inLinkageSection && !inLocalStorageSection && !inFileSection) {
                return null;
            }

            // Skip global file section processing when we have FD entries to avoid duplication
            // Records will be extracted directly by extractRecordLayouts for fileDefinitions
            if (hasFdEntries && inFileSection) {
                return null;
            }

            COBOLDataItem dataItem = toDataItem(ctx);
            if (dataItem == null) {
                return null;
            }

            while (!hierarchy.isEmpty() && dataItem.level <= hierarchy.peek().level) {
                hierarchy.pop();
            }

            List<COBOLDataItem> targetList = null;
            if (inWorkingStorageSection) {
                targetList = workingStorageDataItems;
                dataItem.section = "WORKING-STORAGE";
            } else if (inLinkageSection) {
                targetList = linkageDataItems;
                dataItem.section = "LINKAGE";
            } else if (inLocalStorageSection) {
                targetList = localStorageDataItems;
                dataItem.section = "LOCAL-STORAGE";
            } else if (inFileSection) {
                targetList = fileSectionDataItems;
                dataItem.section = "FILE";
            }

            if (targetList != null) {
                if (hierarchy.isEmpty()) {
                    targetList.add(dataItem);
                } else {
                    hierarchy.peek().children.add(dataItem);
                }
            }

            if (dataItem.level != 77) {
                hierarchy.push(dataItem);
            }

            return null;
        }

        // Convert DataDescriptionEntryFormat1Context to COBOLDataItem with level, name, and picture.
        private COBOLDataItem toDataItem(Cobol85Parser.DataDescriptionEntryFormat1Context ctx) {
            Integer level = parseInteger(ctx.getStart().getText());
            if (level == null) {
                return null;
            }

            COBOLDataItem item = new COBOLDataItem();
            item.level = level;

            if (ctx.dataName() != null) {
                item.name = ctx.dataName().getText();
            } else if (ctx.FILLER() != null) {
                item.name = "FILLER";
            }

            List<Cobol85Parser.DataPictureClauseContext> pictureClauses =
                    ctx.getRuleContexts(Cobol85Parser.DataPictureClauseContext.class);
            if (!pictureClauses.isEmpty() && pictureClauses.get(0).pictureString() != null) {
                item.picture = pictureClauses.get(0).pictureString().getText();
            }

            List<Cobol85Parser.DataUsageClauseContext> usageClauses =
                    ctx.getRuleContexts(Cobol85Parser.DataUsageClauseContext.class);
            if (!usageClauses.isEmpty()) {
                item.usage = normalizeUsage(usageClauses.get(0).getText());
            }

            List<Cobol85Parser.DataRedefinesClauseContext> redefineClauses =
                    ctx.getRuleContexts(Cobol85Parser.DataRedefinesClauseContext.class);
            if (!redefineClauses.isEmpty() && redefineClauses.get(0).dataName() != null) {
                item.redefines = redefineClauses.get(0).dataName().getText();
            }

            List<Cobol85Parser.DataOccursClauseContext> occursClauses =
                    ctx.getRuleContexts(Cobol85Parser.DataOccursClauseContext.class);
            if (!occursClauses.isEmpty()) {
                List<Cobol85Parser.IntegerLiteralContext> integerLiterals =
                        occursClauses.get(0).getRuleContexts(Cobol85Parser.IntegerLiteralContext.class);
                if (!integerLiterals.isEmpty()) {
                    item.occurs = parseInteger(integerLiterals.get(0).getText());
                }
            }

            return item;
        }

        // Normalize USAGE clause (COMP, COMP-3, COMP-5, DISPLAY, etc.).
        private static String normalizeUsage(String raw) {
            if (raw == null || raw.isEmpty()) {
                return null;
            }

            String value = raw;
            if (value.startsWith("USAGE")) {
                value = value.substring("USAGE".length());
            }
            if (value.startsWith("IS")) {
                value = value.substring("IS".length());
            }

            value = value.trim();
            return value.isEmpty() ? null : value;
        }

        private static Integer parseInteger(String raw) {
            try {
                return Integer.parseInt(raw);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
