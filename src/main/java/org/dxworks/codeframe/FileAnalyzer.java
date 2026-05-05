package org.dxworks.codeframe;

import org.dxworks.codeframe.analyzer.*;
import org.dxworks.codeframe.analyzer.cobol.COBOLAnalyzer;
import org.dxworks.codeframe.analyzer.cobol.CobolCopybookRepository;
import org.dxworks.codeframe.analyzer.markdown.MarkdownAnalyzer;
import org.dxworks.codeframe.analyzer.sql.SQLAnalyzer;
import org.dxworks.codeframe.analyzer.xml.XmlAnalyzer;
import org.dxworks.codeframe.model.Analysis;
import org.dxworks.codeframe.model.sql.AlterTableOperation;
import org.dxworks.codeframe.model.sql.CreateTableOperation;
import org.dxworks.codeframe.model.sql.SQLFileAnalysis;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class FileAnalyzer {

    private final Map<Language, LanguageAnalyzer> analyzers;
    private final CodeframeConfig config;

    public FileAnalyzer(CodeframeConfig config, CobolCopybookRepository cobolCopybooks) {
        this.config = config;
        this.analyzers = buildAnalyzers(config, cobolCopybooks);
    }

    public Analysis analyze(Path filePath, Language language) throws IOException {
        LanguageAnalyzer analyzer = analyzers.get(language);
        if (analyzer == null) {
            throw new IllegalStateException("No analyzer available for: " + language);
        }

        String sourceCode = SourceCodeReader.read(filePath);
        Analysis analysis = analyzer.analyze(filePath.toString(), sourceCode);
        filterSqlColumnsIfNeeded(analysis);
        return analysis;
    }

    private static Map<Language, LanguageAnalyzer> buildAnalyzers(
            CodeframeConfig config,
            CobolCopybookRepository cobolCopybooks) {

        Map<Language, LanguageAnalyzer> analyzers = new EnumMap<>(Language.class);
        for (Language lang : Language.values()) {
            if (config.isAnalyzerEnabled(lang.getName())) {
                analyzers.put(lang, createAnalyzer(lang, cobolCopybooks));
            }
        }
        return Collections.unmodifiableMap(analyzers);
    }

    private static LanguageAnalyzer createAnalyzer(Language lang, CobolCopybookRepository cobolCopybooks) {
        return switch (lang) {
            case JAVA -> new JavaAnalyzer();
            case JAVASCRIPT -> new JavaScriptAnalyzer();
            case TYPESCRIPT -> new TypeScriptAnalyzer();
            case PYTHON -> new PythonAnalyzer();
            case CSHARP -> new CSharpAnalyzer();
            case C -> new CAnalyzer();
            case CPP -> new CppAnalyzer();
            case PHP -> new PHPAnalyzer();
            case SQL -> new SQLAnalyzer();
            case COBOL -> new COBOLAnalyzer(cobolCopybooks);
            case RUBY -> new RubyAnalyzer();
            case RUST -> new RustAnalyzer();
            case MARKDOWN -> new MarkdownAnalyzer();
            case XML -> new XmlAnalyzer();
        };
    }

    private void filterSqlColumnsIfNeeded(Analysis analysis) {
        if (!(analysis instanceof SQLFileAnalysis sqlAnalysis)) {
            return;
        }
        if (!config.isHideSqlTableColumns()) {
            return;
        }

        for (CreateTableOperation op : sqlAnalysis.createTables) {
            op.columns.clear();
        }
        for (AlterTableOperation op : sqlAnalysis.alterTables) {
            op.addedColumns.clear();
        }
    }

    public Map<Language, LanguageAnalyzer> enabledAnalyzers() {
        return analyzers;
    }
}
