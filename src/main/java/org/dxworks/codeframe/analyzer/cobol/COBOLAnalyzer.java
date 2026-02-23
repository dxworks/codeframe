package org.dxworks.codeframe.analyzer.cobol;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.*;
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
import java.util.Locale;
import java.io.File;

public class COBOLAnalyzer implements LanguageAnalyzer {

    private static final String PROCEDURE_DIVISION_PROLOGUE_PARAGRAPH = "__PROCEDURE_DIVISION_PROLOGUE__";

    private final CobolCopybookRepository copybookRepository;

    public COBOLAnalyzer(CobolCopybookRepository copybookRepository) {
        this.copybookRepository = copybookRepository;
    }

    @Override
    public Analysis analyze(String filePath, String sourceCode, TSNode rootNode) {
        COBOLFileAnalysis analysis = new COBOLFileAnalysis();
        analysis.filePath = filePath;

        CobolPreprocessorImpl preprocessor = new CobolPreprocessorImpl();
        String preprocessedSource = preprocessSource(sourceCode, preprocessor);
        CommonTokenStream tokens = tokenize(preprocessedSource);
        Cobol85Parser.StartRuleContext tree = parse(tokens);

        ExtractionVisitor visitor = new ExtractionVisitor(analysis, tokens);
        visitParseTree(tree, visitor, filePath);

        applyVisitorResults(analysis, visitor);
        populateExecFlags(analysis, sourceCode);
        analysis.copyStatements.addAll(preprocessor.copyStatements());
        
        return analysis;
    }

    private String preprocessSource(String sourceCode, CobolPreprocessorImpl preprocessor) {
        // Convert repository files to the List<File> expected by the preprocessor API
        List<File> copyFiles = copybookRepository != null 
                ? copybookRepository.indexSnapshot().values().stream().toList()
                : List.of();
        return preprocessor.process(
                sourceCode,
                copyFiles,
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
        private final CommonTokenStream tokens;
        private String programId;
        private final List<COBOLDataItem> workingStorageDataItems = new ArrayList<>();
        private final List<COBOLDataItem> linkageDataItems = new ArrayList<>();
        private final List<COBOLDataItem> localStorageDataItems = new ArrayList<>();
        private final List<COBOLDataItem> fileSectionDataItems = new ArrayList<>();
        private final List<COBOLFileControl> fileControls = new ArrayList<>();
        private final List<COBOLFileDefinition> fileDefinitions = new ArrayList<>();
        private final List<COBOLSection> sections = new ArrayList<>();
        private final List<COBOLParagraph> paragraphs = new ArrayList<>();
        private final List<String> procedureParameters = new ArrayList<>();
        private final Deque<COBOLDataItem> hierarchy = new ArrayDeque<>();
        private Cobol85Parser.FileDescriptionEntryContext currentFdContext = null;
        private boolean hasFdEntries = false; // Track if we have FD entries

        // Constructor receives analysis object (flags are detected from raw source since preprocessor removes EXEC blocks).
        public ExtractionVisitor(COBOLFileAnalysis analysis, CommonTokenStream tokens) {
            this.analysis = analysis;
            this.tokens = tokens;
        }
        // Scope tracking for data division sections and procedure division context.
        private DataSection currentDataSection = null;
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
            WORKING_STORAGE("WORKING-STORAGE"),
            LINKAGE("LINKAGE"),
            LOCAL_STORAGE("LOCAL-STORAGE"),
            FILE("FILE");

            final String label;
            DataSection(String label) { this.label = label; }
        }

        private Void withDataSection(DataSection section, Runnable visitBody) {
            DataSection previous = currentDataSection;
            currentDataSection = section;
            hierarchy.clear();

            visitBody.run();

            hierarchy.clear();
            currentDataSection = previous;
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
            // Find the index of this record in the FD's data description entries
            if (currentFdContext == null) return;
            
            List<Cobol85Parser.DataDescriptionEntryContext> dataEntries = currentFdContext.dataDescriptionEntry();
            int parentIndex = -1;
            
            // Find the parent record's position
            for (int i = 0; i < dataEntries.size(); i++) {
                Cobol85Parser.DataDescriptionEntryContext dataDescCtx = dataEntries.get(i);
                if (dataDescCtx.dataDescriptionEntryFormat1() != null) {
                    COBOLDataItem dataItem = toDataItem(dataDescCtx.dataDescriptionEntryFormat1());
                    if (dataItem != null && dataItem.name != null && dataItem.name.equals(parentRecord.name)) {
                        parentIndex = i;
                        break;
                    }
                }
            }
            
            if (parentIndex == -1) return; // Parent not found
            
            // Process subsequent entries as children until we hit another level 1 record or end of FD
            Deque<COBOLDataItem> hierarchy = new ArrayDeque<>();
            hierarchy.push(parentRecord);
            
            for (int i = parentIndex + 1; i < dataEntries.size(); i++) {
                Cobol85Parser.DataDescriptionEntryContext dataDescCtx = dataEntries.get(i);
                if (dataDescCtx.dataDescriptionEntryFormat1() != null) {
                    COBOLDataItem dataItem = toDataItem(dataDescCtx.dataDescriptionEntryFormat1());
                    if (dataItem != null && dataItem.name != null) {
                        // Stop if we hit another level 1 record (different record)
                        if (dataItem.level <= 1) {
                            break;
                        }
                        
                        // Maintain hierarchy based on levels
                        while (!hierarchy.isEmpty() && dataItem.level <= hierarchy.peek().level) {
                            hierarchy.pop();
                        }
                        
                        // Add as child if we have a parent
                        if (!hierarchy.isEmpty()) {
                            hierarchy.peek().children.add(dataItem);
                            hierarchy.push(dataItem);
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
            if (ctx.procedureSectionHeader() == null) {
                return super.visitProcedureSection(ctx);
            }

            Cobol85Parser.ProcedureSectionHeaderContext header = ctx.procedureSectionHeader();
            String headerText = getOriginalText(header);
            if (header.sectionName() == null || headerText == null || !headerText.toUpperCase(Locale.ROOT).contains("SECTION")) {
                return super.visitProcedureSection(ctx);
            }

            COBOLSection previousSection = currentSection;

            COBOLSection section = new COBOLSection();
            section.name = normalizeName(header.sectionName().getText());
            if (section.name == null) {
                return super.visitProcedureSection(ctx);
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
                // Extract CALL USING argument identifiers into dataReferences
                if (paragraph != null) {
                    paragraph.dataReferences.addAll(extractCallUsingIdentifiers(ctx.callUsingPhrase()));
                }
            }

            if (paragraph != null) {
                paragraph.externalCalls.add(externalCall);
            }
            // Note: CALL statements outside paragraphs are handled by prologue mechanism

            return super.visitCallStatement(ctx);
        }

        private void addFileOperation(COBOLParagraph paragraph, String verb, String rawFileName) {
            if (paragraph == null || rawFileName == null) {
                return;
            }
            COBOLFileOperation op = new COBOLFileOperation();
            op.verb = verb;
            op.target = normalizeName(rawFileName);
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
            addSimpleFileOperation(ctx.fileName(), "READ");
            return super.visitReadStatement(ctx);
        }

        // Extract WRITE file operations (verb and record name).
        @Override
        public Void visitWriteStatement(Cobol85Parser.WriteStatementContext ctx) {
            addSimpleFileOperation(ctx.recordName(), "WRITE");
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
            addSimpleFileOperation(ctx.recordName(), "REWRITE");
            return super.visitRewriteStatement(ctx);
        }

        // Extract DELETE file operations (verb and file name).
        @Override
        public Void visitDeleteStatement(Cobol85Parser.DeleteStatementContext ctx) {
            addSimpleFileOperation(ctx.fileName(), "DELETE");
            return super.visitDeleteStatement(ctx);
        }

        // Extract START file operations (verb and file name).
        @Override
        public Void visitStartStatement(Cobol85Parser.StartStatementContext ctx) {
            addSimpleFileOperation(ctx.fileName(), "START");
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
                        addDataReference(paragraph, moveTo.moveToSendingArea().identifier());
                    }
                    
                    // Extract target identifiers
                    for (Cobol85Parser.IdentifierContext id : moveTo.identifier()) {
                        addDataReference(paragraph, id);
                    }
                }
            }
            
            if (ctx.moveCorrespondingToStatement() != null) {
                Cobol85Parser.MoveCorrespondingToStatementContext moveCorr = ctx.moveCorrespondingToStatement();
                
                // Extract source identifier from moveCorrespondingToSendingArea
                if (moveCorr.moveCorrespondingToSendingArea() != null && moveCorr.moveCorrespondingToSendingArea().identifier() != null) {
                    addDataReference(paragraph, moveCorr.moveCorrespondingToSendingArea().identifier());
                }
                
                // Extract target identifiers
                for (Cobol85Parser.IdentifierContext id : moveCorr.identifier()) {
                    addDataReference(paragraph, id);
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
                        addDataReference(paragraph, store.identifier());
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
                            addDataReference(paragraph, from.identifier());
                        }
                    }
                    // Extract from addTo (target operands)
                    for (Cobol85Parser.AddToContext to : addTo.addTo()) {
                        if (to.identifier() != null) {
                            addDataReference(paragraph, to.identifier());
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
                        if (subtrahend.identifier() != null) {
                            addDataReference(paragraph, subtrahend.identifier());
                        }
                    }
                    for (Cobol85Parser.SubtractMinuendContext minuend : subtractFrom.subtractMinuend()) {
                        if (minuend.identifier() != null) {
                            addDataReference(paragraph, minuend.identifier());
                        }
                    }
                }

                if (ctx.subtractFromGivingStatement() != null) {
                    Cobol85Parser.SubtractFromGivingStatementContext giving = ctx.subtractFromGivingStatement();
                    for (Cobol85Parser.SubtractSubtrahendContext subtrahend : giving.subtractSubtrahend()) {
                        if (subtrahend.identifier() != null) {
                            addDataReference(paragraph, subtrahend.identifier());
                        }
                    }
                    if (giving.subtractMinuendGiving().identifier() != null) {
                        addDataReference(paragraph, giving.subtractMinuendGiving().identifier());
                    }
                    for (Cobol85Parser.SubtractGivingContext result : giving.subtractGiving()) {
                        if (result.identifier() != null) {
                            addDataReference(paragraph, result.identifier());
                        }
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
                if (ctx.identifier() != null) {
                    addDataReference(paragraph, ctx.identifier());
                }

                if (ctx.multiplyRegular() != null) {
                    for (Cobol85Parser.MultiplyRegularOperandContext operand : ctx.multiplyRegular().multiplyRegularOperand()) {
                        if (operand.identifier() != null) {
                            addDataReference(paragraph, operand.identifier());
                        }
                    }
                }

                if (ctx.multiplyGiving() != null) {
                    if (ctx.multiplyGiving().multiplyGivingOperand().identifier() != null) {
                        addDataReference(paragraph, ctx.multiplyGiving().multiplyGivingOperand().identifier());
                    }
                    for (Cobol85Parser.MultiplyGivingResultContext result : ctx.multiplyGiving().multiplyGivingResult()) {
                        if (result.identifier() != null) {
                            addDataReference(paragraph, result.identifier());
                        }
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
                if (ctx.identifier() != null) {
                    addDataReference(paragraph, ctx.identifier());
                }

                if (ctx.divideIntoStatement() != null) {
                    for (Cobol85Parser.DivideIntoContext into : ctx.divideIntoStatement().divideInto()) {
                        if (into.identifier() != null) {
                            addDataReference(paragraph, into.identifier());
                        }
                    }
                }

                if (ctx.divideIntoGivingStatement() != null) {
                    Cobol85Parser.DivideIntoGivingStatementContext intoGiving = ctx.divideIntoGivingStatement();
                    if (intoGiving.identifier() != null) {
                        addDataReference(paragraph, intoGiving.identifier());
                    }
                    if (intoGiving.divideGivingPhrase() != null) {
                        for (Cobol85Parser.DivideGivingContext giving : intoGiving.divideGivingPhrase().divideGiving()) {
                            if (giving.identifier() != null) {
                                addDataReference(paragraph, giving.identifier());
                            }
                        }
                    }
                }

                if (ctx.divideByGivingStatement() != null) {
                    Cobol85Parser.DivideByGivingStatementContext byGiving = ctx.divideByGivingStatement();
                    if (byGiving.identifier() != null) {
                        addDataReference(paragraph, byGiving.identifier());
                    }
                    if (byGiving.divideGivingPhrase() != null) {
                        for (Cobol85Parser.DivideGivingContext giving : byGiving.divideGivingPhrase().divideGiving()) {
                            if (giving.identifier() != null) {
                                addDataReference(paragraph, giving.identifier());
                            }
                        }
                    }
                }

                if (ctx.divideRemainder() != null) {
                    if (ctx.divideRemainder().identifier() != null) {
                        addDataReference(paragraph, ctx.divideRemainder().identifier());
                    }
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
                        if (setTo.identifier() != null) {
                            addDataReference(paragraph, setTo.identifier());
                        }
                    }
                    for (Cobol85Parser.SetToValueContext value : setToStatement.setToValue()) {
                        for (Cobol85Parser.IdentifierContext identifier : value.getRuleContexts(Cobol85Parser.IdentifierContext.class)) {
                            addDataReference(paragraph, identifier);
                        }
                    }
                }

                if (ctx.setUpDownByStatement() != null) {
                    Cobol85Parser.SetUpDownByStatementContext upDown = ctx.setUpDownByStatement();
                    for (Cobol85Parser.SetToContext setTo : upDown.setTo()) {
                        if (setTo.identifier() != null) {
                            addDataReference(paragraph, setTo.identifier());
                        }
                    }
                    if (upDown.setByValue().identifier() != null) {
                        addDataReference(paragraph, upDown.setByValue().identifier());
                    }
                }
            }
            return super.visitSetStatement(ctx);
        }

        // Extract control-flow statements (GOBACK, STOP RUN, EXIT PROGRAM, RETURN).
        @Override
        public Void visitGobackStatement(Cobol85Parser.GobackStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            addControlFlowStatement(paragraph, "GOBACK", null);
            return super.visitGobackStatement(ctx);
        }

        @Override
        public Void visitStopStatement(Cobol85Parser.StopStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                String type = "STOP";
                String target = null;
                if (ctx.RUN() != null) {
                    type = "STOP_RUN";
                } else if (ctx.literal() != null) {
                    type = "STOP_LITERAL";
                    target = normalizeName(ctx.literal().getText());
                }
                addControlFlowStatement(paragraph, type, target);
            }
            return super.visitStopStatement(ctx);
        }

        @Override
        public Void visitExitStatement(Cobol85Parser.ExitStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null && ctx.PROGRAM() != null) {
                // Only capture EXIT PROGRAM, not bare EXIT (which is a no-op)
                addControlFlowStatement(paragraph, "EXIT_PROGRAM", null);
            }
            return super.visitExitStatement(ctx);
        }

        @Override
        public Void visitReturnStatement(Cobol85Parser.ReturnStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                String target = null;
                // RETURN works with fileName, not identifier
                if (ctx.fileName() != null) {
                    target = normalizeName(ctx.fileName().getText());
                }
                addControlFlowStatement(paragraph, "RETURN", target);
            }
            return super.visitReturnStatement(ctx);
        }

        private void addControlFlowStatement(COBOLParagraph paragraph, String type, String target) {
            if (paragraph == null) {
                return;
            }
            COBOLControlFlowStatement controlFlow = new COBOLControlFlowStatement();
            controlFlow.type = type;
            controlFlow.target = target;
            paragraph.controlFlowStatements.add(controlFlow);
        }

        private void addSimpleFileOperation(Cobol85Parser.FileNameContext fileName, String verb) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null && fileName != null) {
                addFileOperation(paragraph, verb, fileName.getText());
            }
        }

        private void addSimpleFileOperation(Cobol85Parser.RecordNameContext recordName, String verb) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null && recordName != null) {
                addFileOperation(paragraph, verb, recordName.getText());
            }
        }

        private void addDataReference(COBOLParagraph paragraph, Cobol85Parser.IdentifierContext identifier) {
            if (paragraph == null || identifier == null) {
                return;
            }
            paragraph.dataReferences.add(normalizeDataReference(getOriginalText(identifier)));
        }

        // Get original text from token stream, preserving whitespace
        private String getOriginalText(ParserRuleContext ctx) {
            if (ctx == null || tokens == null) {
                return "";
            }
            int start = ctx.start.getStartIndex();
            int stop = ctx.stop.getStopIndex();
            if (start >= 0 && stop >= 0 && stop >= start) {
                CharStream charStream = tokens.getTokenSource().getInputStream();
                return charStream.getText(Interval.of(start, stop));
            }
            return ctx.getText(); // fallback to regular getText()
        }

        private void addQualifiedNameReference(COBOLParagraph paragraph, Cobol85Parser.QualifiedDataNameContext qualifiedDataName) {
            if (paragraph == null || qualifiedDataName == null) {
                return;
            }
            paragraph.dataReferences.add(normalizeDataReference(qualifiedDataName.getText()));
        }

        // Extract data references from STRING statements (simplified).
        @Override
        public Void visitStringStatement(Cobol85Parser.StringStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph == null) {
                return super.visitStringStatement(ctx);
            }
            // Extract from stringSendingPhrase (source operands)
            for (Cobol85Parser.StringSendingPhraseContext sendingPhrase : ctx.stringSendingPhrase()) {
                for (Cobol85Parser.StringSendingContext sending : sendingPhrase.stringSending()) {
                    if (sending.identifier() != null) {
                        addDataReference(paragraph, sending.identifier());
                    }
                }
            }
            // Extract from stringIntoPhrase (target operand)
            if (ctx.stringIntoPhrase() != null && ctx.stringIntoPhrase().identifier() != null) {
                addDataReference(paragraph, ctx.stringIntoPhrase().identifier());
            }
            return super.visitStringStatement(ctx);
        }

        // Extract data references from UNSTRING statements (simplified).
        @Override
        public Void visitUnstringStatement(Cobol85Parser.UnstringStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph == null) {
                return super.visitUnstringStatement(ctx);
            }
            // Extract from unstringSendingPhrase (source identifier)
            if (ctx.unstringSendingPhrase() != null && ctx.unstringSendingPhrase().identifier() != null) {
                addDataReference(paragraph, ctx.unstringSendingPhrase().identifier());
            }
            // Extract from unstringIntoPhrase (target identifiers)
            if (ctx.unstringIntoPhrase() != null) {
                for (Cobol85Parser.UnstringIntoContext unstringInto : ctx.unstringIntoPhrase().unstringInto()) {
                    if (unstringInto.identifier() != null) {
                        addDataReference(paragraph, unstringInto.identifier());
                    }
                }
            }
            // Extract from unstringDelimitedByPhrase (delimiter identifier)
            if (ctx.unstringSendingPhrase() != null && ctx.unstringSendingPhrase().unstringDelimitedByPhrase() != null) {
                Cobol85Parser.UnstringDelimitedByPhraseContext delimitedBy = ctx.unstringSendingPhrase().unstringDelimitedByPhrase();
                if (delimitedBy.identifier() != null) {
                    addDataReference(paragraph, delimitedBy.identifier());
                }
            }
            return super.visitUnstringStatement(ctx);
        }

        // Extract data references from EVALUATE statements (simplified).
        @Override
        public Void visitEvaluateStatement(Cobol85Parser.EvaluateStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                // Extract from evaluateSelect (subject identifier)
                if (ctx.evaluateSelect().identifier() != null) {
                    addDataReference(paragraph, ctx.evaluateSelect().identifier());
                }
                // Extract from evaluateAlsoSelect (additional subjects)
                for (Cobol85Parser.EvaluateAlsoSelectContext alsoSelect : ctx.evaluateAlsoSelect()) {
                    if (alsoSelect.evaluateSelect().identifier() != null) {
                        addDataReference(paragraph, alsoSelect.evaluateSelect().identifier());
                    }
                }
            }
            return super.visitEvaluateStatement(ctx);
        }

        // Extract data references from INITIALIZE statements (simplified).
        @Override
        public Void visitInitializeStatement(Cobol85Parser.InitializeStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                // Extract target identifiers
                for (Cobol85Parser.IdentifierContext identifier : ctx.identifier()) {
                    addDataReference(paragraph, identifier);
                }
            }
            return super.visitInitializeStatement(ctx);
        }

        // Extract data references from DISPLAY statements (simplified).
        @Override
        public Void visitDisplayStatement(Cobol85Parser.DisplayStatementContext ctx) {
            COBOLParagraph paragraph = targetParagraph();
            if (paragraph != null) {
                // Extract from displayOperand (identifier operands)
                for (Cobol85Parser.DisplayOperandContext operand : ctx.displayOperand()) {
                    if (operand.identifier() != null) {
                        addDataReference(paragraph, operand.identifier());
                    }
                }
                // Extract from displayAt (identifier for AT clause)
                if (ctx.displayAt() != null && ctx.displayAt().identifier() != null) {
                    addDataReference(paragraph, ctx.displayAt().identifier());
                }
            }
            return super.visitDisplayStatement(ctx);
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

        // Extract identifier names from CALL USING phrase for dataReferences.
        private List<String> extractCallUsingIdentifiers(Cobol85Parser.CallUsingPhraseContext usingPhrase) {
            List<String> identifiers = new ArrayList<>();
            for (Cobol85Parser.CallUsingParameterContext parameter : usingPhrase.callUsingParameter()) {
                if (parameter.callByReferencePhrase() != null) {
                    collectIdentifiers(parameter.callByReferencePhrase().callByReference(), identifiers);
                }
                if (parameter.callByValuePhrase() != null) {
                    collectIdentifiers(parameter.callByValuePhrase().callByValue(), identifiers);
                }
                if (parameter.callByContentPhrase() != null) {
                    collectIdentifiers(parameter.callByContentPhrase().callByContent(), identifiers);
                }
            }
            return identifiers;
        }

        private <T extends ParserRuleContext> void collectIdentifiers(List<T> contexts, List<String> identifiers) {
            for (T ctx : contexts) {
                Cobol85Parser.IdentifierContext id = ctx.getRuleContext(Cobol85Parser.IdentifierContext.class, 0);
                if (id != null) {
                    identifiers.add(normalizeName(id.getText()));
                }
            }
        }

        private boolean shouldSkipDataDescription() {
            return currentDataSection == null || (hasFdEntries && currentDataSection == DataSection.FILE);
        }

        // Extract data items with hierarchy and section context (format 1 entries).
        @Override
        public Void visitDataDescriptionEntryFormat1(Cobol85Parser.DataDescriptionEntryFormat1Context ctx) {
            if (shouldSkipDataDescription()) {
                return null;
            }

            COBOLDataItem dataItem = toDataItem(ctx);
            if (dataItem == null) {
                return null;
            }

            while (!hierarchy.isEmpty() && dataItem.level <= hierarchy.peek().level) {
                hierarchy.pop();
            }

            assignSectionAndAdd(dataItem);

            if (dataItem.level != 77) {
                hierarchy.push(dataItem);
            }

            return null;
        }

        // Extract 88-level condition names as children in dataItems hierarchy.
        @Override
        public Void visitDataDescriptionEntryFormat3(Cobol85Parser.DataDescriptionEntryFormat3Context ctx) {
            if (shouldSkipDataDescription()) {
                return null;
            }

            COBOLDataItem dataItem = toDataItemFromFormat3(ctx);
            if (dataItem == null) {
                return null;
            }

            assignSectionAndAdd(dataItem);

            // Note: 88-level items are not pushed to hierarchy (they can't have children)

            return null;
        }

        private List<COBOLDataItem> targetListFor(DataSection section) {
            switch (section) {
                case WORKING_STORAGE: return workingStorageDataItems;
                case LINKAGE:         return linkageDataItems;
                case LOCAL_STORAGE:   return localStorageDataItems;
                case FILE:            return fileSectionDataItems;
                default:              return null;
            }
        }

        private void assignSectionAndAdd(COBOLDataItem dataItem) {
            dataItem.section = currentDataSection.label;
            List<COBOLDataItem> targetList = targetListFor(currentDataSection);
            if (targetList != null) {
                if (hierarchy.isEmpty()) {
                    targetList.add(dataItem);
                } else {
                    hierarchy.peek().children.add(dataItem);
                }
            }
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

        // Convert DataDescriptionEntryFormat3Context (88-level) to COBOLDataItem.
        private COBOLDataItem toDataItemFromFormat3(Cobol85Parser.DataDescriptionEntryFormat3Context ctx) {
            COBOLDataItem item = new COBOLDataItem();
            item.level = 88; // 88-level entries are always level 88

            if (ctx.conditionName() != null) {
                item.name = ctx.conditionName().getText();
            }

            // Note: VALUE clause is intentionally excluded per Decision #12 in research doc

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
