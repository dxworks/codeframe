package org.dxworks.codeframe;

import org.dxworks.codeframe.analyzer.*;
import org.dxworks.codeframe.analyzer.cobol.COBOLAnalyzer;
import org.dxworks.codeframe.analyzer.cobol.CobolCopybookRepository;
import org.dxworks.codeframe.analyzer.markdown.MarkdownAnalyzer;
import org.dxworks.codeframe.analyzer.sql.SQLAnalyzer;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class LanguageRegistry {

    public static Optional<Language> detectLanguage(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        for (Language lang : Language.values()) {
            if (lang.matchesFileName(fileName)) {
                return Optional.of(lang);
            }
        }
        return Optional.empty();
    }

    public static boolean isCobolCopybook(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".cpy");
    }

    public static boolean isRelevantSourceOrDependency(Path p) {
        return detectLanguage(p).isPresent() || isCobolCopybook(p);
    }

    public static Set<String> allLanguageNames() {
        return Arrays.stream(Language.values())
            .map(Language::getName)
            .collect(Collectors.toSet());
    }

    public static Map<Language, LanguageAnalyzer> buildAnalyzers(
            CobolCopybookRepository cobolCopybooks,
            CodeframeConfig config) {

        Map<Language, LanguageAnalyzer> analyzers = new HashMap<>();

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
            case PHP -> new PHPAnalyzer();
            case SQL -> new SQLAnalyzer();
            case COBOL -> new COBOLAnalyzer(cobolCopybooks);
            case RUBY -> new RubyAnalyzer();
            case RUST -> new RustAnalyzer();
            case MARKDOWN -> new MarkdownAnalyzer();
        };
    }
}
