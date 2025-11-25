package org.dxworks.codeframe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dxworks.codeframe.analyzer.*;
import org.dxworks.codeframe.analyzer.sql.SQLAnalyzer;
import org.dxworks.codeframe.model.Analysis;
import org.treesitter.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import org.dxworks.ignorerLibrary.Ignorer;
import org.dxworks.ignorerLibrary.IgnorerBuilder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class App {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private static final Map<Language, TSLanguage> TREE_SITTER_LANGUAGES = new HashMap<>();
    private static final Map<Language, LanguageAnalyzer> ANALYZERS = new HashMap<>();
    
    static {
        // Initialize Tree-sitter languages
        try {
            TREE_SITTER_LANGUAGES.put(Language.JAVA, new TreeSitterJava());
            TREE_SITTER_LANGUAGES.put(Language.JAVASCRIPT, (TSLanguage) Class.forName("org.treesitter.TreeSitterJavascript").getDeclaredConstructor().newInstance());
            TREE_SITTER_LANGUAGES.put(Language.TYPESCRIPT, (TSLanguage) Class.forName("org.treesitter.TreeSitterTypescript").getDeclaredConstructor().newInstance());
            TREE_SITTER_LANGUAGES.put(Language.PYTHON, (TSLanguage) Class.forName("org.treesitter.TreeSitterPython").getDeclaredConstructor().newInstance());
            TREE_SITTER_LANGUAGES.put(Language.CSHARP, (TSLanguage) Class.forName("org.treesitter.TreeSitterCSharp").getDeclaredConstructor().newInstance());
            TREE_SITTER_LANGUAGES.put(Language.PHP, (TSLanguage) Class.forName("org.treesitter.TreeSitterPhp").getDeclaredConstructor().newInstance());
            TREE_SITTER_LANGUAGES.put(Language.RUBY, (TSLanguage) Class.forName("org.treesitter.TreeSitterRuby").getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Tree-sitter languages", e);
        }
        
        // Initialize analyzers
        ANALYZERS.put(Language.JAVA, new JavaAnalyzer());
        ANALYZERS.put(Language.JAVASCRIPT, new JavaScriptAnalyzer());
        ANALYZERS.put(Language.TYPESCRIPT, new TypeScriptAnalyzer());
        ANALYZERS.put(Language.PYTHON, new PythonAnalyzer());
        ANALYZERS.put(Language.CSHARP, new CSharpAnalyzer());
        ANALYZERS.put(Language.PHP, new PHPAnalyzer());
        ANALYZERS.put(Language.SQL, new SQLAnalyzer());
        ANALYZERS.put(Language.RUBY, new RubyAnalyzer());
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java -jar codeframe.jar <input-folder> <output-file>");
            System.err.println("  <input-folder>: Path to source code directory or file");
            System.err.println("  <output-file>:  Path to output JSONL file");
            System.err.println("Supported languages: Java, JavaScript, TypeScript, Python, C#, PHP, SQL, Ruby");
            System.exit(2);
        }
        
        Path input = Paths.get(args[0]);
        if (!Files.exists(input)) {
            System.err.println("Error: Input path does not exist: " + input);
            System.exit(1);
        }
        
        Path jsonlOutput = Paths.get(args[1]);
        // Create parent directories if they don't exist
        if (jsonlOutput.getParent() != null) {
            Files.createDirectories(jsonlOutput.getParent());
        }

        System.out.println("Starting code analysis...");
        System.out.println("Input: " + input.toAbsolutePath());
        
        int maxFileLines = CodeframeConfig.load().getMaxFileLines();
        List<Path> files = collectSourceFiles(input, maxFileLines);
        System.out.println("Found " + files.size() + " source files");
        
        Instant startTime = Instant.now();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger progressCounter = new AtomicInteger(0);
        
        // Write metadata header
        try (BufferedWriter writer = Files.newBufferedWriter(jsonlOutput, StandardCharsets.UTF_8)) {
            Map<String, Object> runInfo = new HashMap<>();
            runInfo.put("kind", "run");
            runInfo.put("started_at", startTime.toString());
            runInfo.put("input_path", input.toString());
            runInfo.put("total_files", files.size());
            writer.write(MAPPER.writeValueAsString(runInfo));
            writer.newLine();
            
            // Process files in parallel with progress reporting
            files.parallelStream().forEach(file -> {
                Optional<Language> langOpt = LanguageDetector.detectLanguage(file);
                if (langOpt.isEmpty()) {
                    return; // Skip unsupported files
                }
                
                Language language = langOpt.get();
                int current = progressCounter.incrementAndGet();
                
                synchronized (System.out) {
                    System.out.println("[" + current + "/" + files.size() + "] Analyzing " + 
                                     language.getName() + ": " + file.getFileName());
                }
                
                try {
                    Analysis analysis = analyzeFile(file, language);
                    
                    // Write result immediately (synchronized to avoid concurrent writes)
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
                      .filter(p -> withinMaxLines(p, maxFileLines))
                      .filter(p -> LanguageDetector.detectLanguage(p).isPresent())
                      .forEach(files::add);
            }
        } else if (Files.isRegularFile(input)) {
            if (ignorer.accepts(input.toAbsolutePath().toString())
                    && withinMaxLines(input, maxFileLines)
                    && LanguageDetector.detectLanguage(input).isPresent()) {
                files.add(input);
            }
        }
        
        return files;
    }
    
    private static boolean withinMaxLines(Path path, int maxFileLines) {
        try (Stream<String> lines = Files.lines(path)) {
            long count = lines.limit((long) maxFileLines + 1L).count();
            return count <= maxFileLines;
        } catch (IOException e) {
            return true;
        }
    }
    
    public static Analysis analyzeFile(Path filePath, Language language) throws IOException {
        String sourceCode = Files.readString(filePath, StandardCharsets.UTF_8);
        
        // Remove BOM if present (common in C# files)
        if (sourceCode.startsWith("\uFEFF")) {
            sourceCode = sourceCode.substring(1);
        }
        
        LanguageAnalyzer analyzer = ANALYZERS.get(language);
        if (analyzer == null) {
            throw new IllegalArgumentException("No analyzer available for: " + language);
        }

        // SQL is handled via JSqlParserAnalyzer and does not use Tree-sitter
        if (language == Language.SQL) {
            return analyzer.analyze(filePath.toString(), sourceCode, null);
        }

        TSLanguage tsLanguage = TREE_SITTER_LANGUAGES.get(language);
        if (tsLanguage == null) {
            throw new IllegalArgumentException("No Tree-sitter language available for: " + language);
        }
        
        TSParser parser = new TSParser();
        parser.setLanguage(tsLanguage);
        
        TSTree tree = parser.parseString(null, sourceCode);
        TSNode rootNode = tree.getRootNode();
        
        return analyzer.analyze(filePath.toString(), sourceCode, rootNode);
    }
}
