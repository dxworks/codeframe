package org.dxworks.codeframe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dxworks.codeframe.analyzer.*;
import org.dxworks.codeframe.analyzer.cobol.COBOLAnalyzer;
import org.dxworks.codeframe.analyzer.cobol.CobolCopybookRepository;
import org.dxworks.codeframe.analyzer.sql.SQLAnalyzer;
import org.dxworks.codeframe.model.Analysis;
import org.dxworks.codeframe.model.sql.AlterTableOperation;
import org.dxworks.codeframe.model.sql.CreateTableOperation;
import org.dxworks.codeframe.model.sql.SQLFileAnalysis;
import org.dxworks.utils.ignorer.Ignorer;
import org.dxworks.utils.ignorer.IgnorerBuilder;
import org.treesitter.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class App {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY);

    private static final Map<Language, TSLanguage> TREE_SITTER_LANGUAGES = new HashMap<>();

    /**
     * Built per run (in main) because COBOL needs run-scoped copybook dependencies.
     * Also initialized in tests via initAnalyzersForTests(...).
     */
    private static volatile Map<Language, LanguageAnalyzer> ANALYZERS = Map.of();

    static {
        // Initialize Tree-sitter languages (stable, can remain static)
        try {
            TREE_SITTER_LANGUAGES.put(Language.JAVA, new TreeSitterJava());
            TREE_SITTER_LANGUAGES.put(Language.JAVASCRIPT, (TSLanguage) Class.forName("org.treesitter.TreeSitterJavascript").getDeclaredConstructor().newInstance());
            TREE_SITTER_LANGUAGES.put(Language.TYPESCRIPT, (TSLanguage) Class.forName("org.treesitter.TreeSitterTypescript").getDeclaredConstructor().newInstance());
            TREE_SITTER_LANGUAGES.put(Language.PYTHON, (TSLanguage) Class.forName("org.treesitter.TreeSitterPython").getDeclaredConstructor().newInstance());
            TREE_SITTER_LANGUAGES.put(Language.CSHARP, (TSLanguage) Class.forName("org.treesitter.TreeSitterCSharp").getDeclaredConstructor().newInstance());
            TREE_SITTER_LANGUAGES.put(Language.PHP, (TSLanguage) Class.forName("org.treesitter.TreeSitterPhp").getDeclaredConstructor().newInstance());
            TREE_SITTER_LANGUAGES.put(Language.RUBY, (TSLanguage) Class.forName("org.treesitter.TreeSitterRuby").getDeclaredConstructor().newInstance());
            TREE_SITTER_LANGUAGES.put(Language.RUST, (TSLanguage) Class.forName("org.treesitter.TreeSitterRust").getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Tree-sitter languages", e);
        }
    }

    private static Map<Language, LanguageAnalyzer> buildAnalyzers(CobolCopybookRepository cobolCopybooks) {
        Map<Language, LanguageAnalyzer> analyzers = new HashMap<>();

        analyzers.put(Language.JAVA, new JavaAnalyzer());
        analyzers.put(Language.JAVASCRIPT, new JavaScriptAnalyzer());
        analyzers.put(Language.TYPESCRIPT, new TypeScriptAnalyzer());
        analyzers.put(Language.PYTHON, new PythonAnalyzer());
        analyzers.put(Language.CSHARP, new CSharpAnalyzer());
        analyzers.put(Language.PHP, new PHPAnalyzer());
        analyzers.put(Language.SQL, new SQLAnalyzer());

        // COBOL analyzer gets the run-scoped repository (constructor injection)
        analyzers.put(Language.COBOL, new COBOLAnalyzer(cobolCopybooks));

        analyzers.put(Language.RUBY, new RubyAnalyzer());
        analyzers.put(Language.RUST, new RustAnalyzer());

        return Collections.unmodifiableMap(analyzers);
    }

    /**
     * Convenience overload for tests: pass copybook paths.
     */
    public static void initAnalyzersForTestsFromPaths(List<Path> copybookPaths) {
        ANALYZERS = buildAnalyzers(new CobolCopybookRepository(copybookPaths.stream().map(Path::toFile).toList()));
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java -jar codeframe.jar <input-folder> <output-file>");
            System.err.println("  <input-folder>: Path to source code directory or file");
            System.err.println("  <output-file>:  Path to output JSONL file");
            System.err.println("Supported languages: Java, JavaScript, TypeScript, Python, C#, PHP, SQL, COBOL, Ruby, Rust");
            System.exit(2);
        }

        Path input = Paths.get(args[0]);
        if (!Files.exists(input)) {
            System.err.println("Error: Input path does not exist: " + input);
            System.exit(1);
        }

        Path jsonlOutput = Paths.get(args[1]);
        if (jsonlOutput.getParent() != null) {
            Files.createDirectories(jsonlOutput.getParent());
        }

        System.out.println("Starting code analysis...");
        System.out.println("Input: " + input.toAbsolutePath());

        CodeframeConfig config = CodeframeConfig.load();
        int maxFileLines = config.getMaxFileLines();
        System.out.println("Configuration: maxFileLines=" + maxFileLines
                + ", hideSqlTableColumns=" + config.isHideSqlTableColumns());

        // 1) Collect run-scoped files (includes analyzable sources + potential COBOL copybooks)
        List<Path> scopedFiles = collectSourceFiles(input, maxFileLines);

        // 2) Split: analyzable targets (anything with a Language) vs copybooks (Option A)
        List<Path> files =
                scopedFiles.stream()
                        .filter(p -> LanguageDetector.detectLanguage(p).isPresent())
                        .toList();

        // Determine whether we have any COBOL "program" files in scope.
        // If none, do not treat .cpy files as copybooks (they might belong to other ecosystems).
        boolean hasCobolPrograms =
                files.stream()
                        .anyMatch(p -> LanguageDetector.detectLanguage(p).orElse(null) == Language.COBOL);

        List<Path> copyFilesForRun = hasCobolPrograms
                ? scopedFiles.stream().filter(LanguageDetector::isCobolCopybook).toList()
                : List.of();

        // 3) Build run-scoped copybook repository (only if COBOL programs exist)
        CobolCopybookRepository cobolCopybooks = new CobolCopybookRepository(
                copyFilesForRun.stream().map(Path::toFile).toList()
        );

        // 4) Register analyzers for this run (COBOL gets the repository)
        ANALYZERS = buildAnalyzers(cobolCopybooks);

        System.out.println("Found " + files.size() + " source files with at most " + maxFileLines + " lines");
        if (!copyFilesForRun.isEmpty()) {
            System.out.println("Found " + copyFilesForRun.size() + " COBOL copybooks in scope");
        }

        Instant startTime = Instant.now();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger progressCounter = new AtomicInteger(0);

        try (BufferedWriter writer = Files.newBufferedWriter(jsonlOutput, StandardCharsets.UTF_8)) {
            Map<String, Object> runInfo = new HashMap<>();
            runInfo.put("kind", "run");
            runInfo.put("started_at", startTime.toString());
            runInfo.put("input_path", input.toString());
            runInfo.put("total_files", files.size());
            writer.write(MAPPER.writeValueAsString(runInfo));
            writer.newLine();

            files.parallelStream().forEach(file -> {
                Optional<Language> langOpt = LanguageDetector.detectLanguage(file);
                if (langOpt.isEmpty()) {
                    return;
                }

                Language language = langOpt.get();
                int current = progressCounter.incrementAndGet();

                synchronized (System.out) {
                    System.out.println("[" + current + "/" + files.size() + "] Analyzing " +
                            language.getName() + ": " + file.getFileName());
                }

                try {
                    Analysis analysis = analyzeFile(file, language, config);

                    synchronized (writer) {
                        writer.write(MAPPER.writeValueAsString(analysis));
                        writer.newLine();
                        writer.flush();
                    }

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    Map<String, String> error = new HashMap<>();
                    error.put("kind", "error");
                    error.put("file", file.toString());
                    error.put("language", language.getName());
                    error.put("error", e.getMessage());

                    try {
                        synchronized (writer) {
                            writer.write(MAPPER.writeValueAsString(error));
                            writer.newLine();
                            writer.flush();
                        }
                    } catch (IOException ioException) {
                        System.err.println("Failed to write error for " + file + ": " + ioException.getMessage());
                    }

                    errorCount.incrementAndGet();
                    synchronized (System.err) {
                        System.err.println("  Error analyzing " + file.getFileName() + ": " + e.getMessage());
                    }
                }
            });

            Instant endTime = Instant.now();
            Map<String, Object> doneInfo = new HashMap<>();
            doneInfo.put("kind", "done");
            doneInfo.put("ended_at", endTime.toString());
            doneInfo.put("files_analyzed", successCount.get());
            doneInfo.put("files_with_errors", errorCount.get());
            doneInfo.put("duration_seconds",
                    java.time.Duration.between(startTime, endTime).getSeconds());
            writer.write(MAPPER.writeValueAsString(doneInfo));
            writer.newLine();
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Analysis complete!");
        System.out.println("Successfully analyzed: " + successCount.get() + " files");
        if (errorCount.get() > 0) {
            System.out.println("Errors: " + errorCount.get());
        }
        System.out.println("Output written to: " + jsonlOutput.toAbsolutePath());
        System.out.println("=".repeat(60));
    }

    private static List<Path> collectSourceFiles(Path input, int maxFileLines) throws IOException {
        List<Path> files = new ArrayList<>();
        Ignorer ignorer = new IgnorerBuilder(Paths.get(".ignore")).compile();

        if (Files.isDirectory(input)) {
            try (Stream<Path> stream = Files.walk(input)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> ignorer.accepts(p.toAbsolutePath().toString()))
                        .filter(LanguageDetector::isRelevantSourceOrDependency)
                        .filter(p -> withinMaxLines(p, maxFileLines))
                        .forEach(files::add);
            }
        } else if (Files.isRegularFile(input)) {
            if (ignorer.accepts(input.toAbsolutePath().toString())
                    && LanguageDetector.isRelevantSourceOrDependency(input)
                    && withinMaxLines(input, maxFileLines)) {
                files.add(input);
            }
        }

        return files;
    }

    private static boolean withinMaxLines(Path path, int maxFileLines) {
        try (InputStream in = Files.newInputStream(path)) {
            int count = 0;
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    if (buffer[i] == '\n' && ++count > maxFileLines) {
                        return false;
                    }
                }
            }
            return true;
        } catch (IOException e) {
            return true;
        }
    }

    public static Analysis analyzeFile(Path filePath, Language language) throws IOException {
        CodeframeConfig config = CodeframeConfig.load();
        return analyzeFile(filePath, language, config);
    }

    public static Analysis analyzeFile(Path filePath, Language language, CodeframeConfig config) throws IOException {
        String sourceCode = Files.readString(filePath, StandardCharsets.UTF_8);

        if (sourceCode.startsWith("\uFEFF")) {
            sourceCode = sourceCode.substring(1);
        }

        LanguageAnalyzer analyzer = ANALYZERS.get(language);
        if (analyzer == null) {
            throw new IllegalStateException(
                    "Analyzers not initialized or no analyzer available for: " + language
            );
        }

        Analysis analysis;

        if (language == Language.SQL || language == Language.COBOL) {
            analysis = analyzer.analyze(filePath.toString(), sourceCode, null);
        } else {
            TSLanguage tsLanguage = TREE_SITTER_LANGUAGES.get(language);
            if (tsLanguage == null) {
                throw new IllegalArgumentException("No Tree-sitter language available for: " + language);
            }

            TSParser parser = new TSParser();
            parser.setLanguage(tsLanguage);

            TSTree tree = parser.parseString(null, sourceCode);
            TSNode rootNode = tree.getRootNode();

            analysis = analyzer.analyze(filePath.toString(), sourceCode, rootNode);
        }

        filterSqlColumnsIfNeeded(analysis, config);
        return analysis;
    }

    private static void filterSqlColumnsIfNeeded(Analysis analysis, CodeframeConfig config) {
        if (!(analysis instanceof SQLFileAnalysis)) {
            return;
        }
        if (!config.isHideSqlTableColumns()) {
            return;
        }

        SQLFileAnalysis sqlAnalysis = (SQLFileAnalysis) analysis;

        for (CreateTableOperation op : sqlAnalysis.createTables) {
            op.columns.clear();
        }
        for (AlterTableOperation op : sqlAnalysis.alterTables) {
            op.addedColumns.clear();
        }
    }
}
