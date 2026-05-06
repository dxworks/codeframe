package org.dxworks.codeframe;

import org.dxworks.codeframe.analyzer.cobol.CobolCopybookRepository;
import org.dxworks.codeframe.model.Analysis;
import org.dxworks.utils.ignorer.Ignorer;
import org.dxworks.utils.ignorer.IgnorerBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class App {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java -jar codeframe.jar <input-folder> <output-file>");
            System.err.println("  <input-folder>: Path to source code directory or file");
            System.err.println("  <output-file>:  Path to output JSONL file");
            System.err.println("Supported languages: Java, JavaScript, TypeScript, Python, C#, C, C++, PHP, SQL, COBOL, Ruby, Rust, Markdown");
            System.exit(2);
        }

        Path input = Paths.get(args[0]);
        if (!Files.exists(input)) {
            System.err.println("Error: Input path does not exist: " + input);
            System.exit(1);
        }

        Path jsonlOutput = Paths.get(args[1]);

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
                        .filter(p -> Language.detectFor(p).isPresent())
                        .toList();

        // Determine whether we have any COBOL "program" files in scope.
        // If none, do not treat .cpy files as copybooks (they might belong to other ecosystems).
        boolean hasCobolPrograms =
                files.stream()
                        .anyMatch(p -> Language.detectFor(p).orElse(null) == Language.COBOL);

        List<Path> copyFilesForRun = hasCobolPrograms
                ? scopedFiles.stream().filter(CobolCopybookRepository::isCopybook).toList()
                : List.of();

        // 3) Build run-scoped copybook repository (only if COBOL programs exist)
        CobolCopybookRepository cobolCopybooks = new CobolCopybookRepository(
                copyFilesForRun.stream().map(Path::toFile).toList()
        );

        // 4) Build per-run file analyzer (COBOL gets the copybook repository)
        FileAnalyzer fileAnalyzer = new FileAnalyzer(config, cobolCopybooks);

        System.out.println("Enabled analyzers: " + String.join(", ", fileAnalyzer.enabledAnalyzers().keySet().stream()
                .map(lang -> lang.getName().toLowerCase())
                .sorted()
                .toList()));

        System.out.println("Found " + files.size() + " source files with at most " + maxFileLines + " lines");
        if (!copyFilesForRun.isEmpty()) {
            System.out.println("Found " + copyFilesForRun.size() + " COBOL copybooks in scope");
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger progressCounter = new AtomicInteger(0);

        try (JsonlSink sink = new JsonlSink(jsonlOutput)) {
            sink.writeRun(input, files.size());

            files.parallelStream().forEach(file -> {
                Optional<Language> langOpt = Language.detectFor(file);
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
                    Analysis analysis = fileAnalyzer.analyze(file, language);
                    sink.writeAnalysis(analysis);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    sink.writeError(file, language.getName(), e.getMessage());
                    errorCount.incrementAndGet();
                    synchronized (System.err) {
                        System.err.println("  Error analyzing " + file.getFileName() + ": " + e.getMessage());
                    }
                }
            });

            sink.writeDone(successCount.get(), errorCount.get());
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
        Ignorer ignorer = new IgnorerBuilder(Paths.get(".ignore")).compile();

        if (Files.isRegularFile(input)) {
            if (ignorer.accepts(input.toAbsolutePath().toString())
                    && isRelevantSourceOrDependency(input)
                    && withinMaxLines(input, maxFileLines)) {
                return List.of(input);
            }
            return List.of();
        }

        if (!Files.isDirectory(input)) {
            return List.of();
        }

        List<Path> candidates;
        try (Stream<Path> stream = Files.walk(input)) {
            candidates = stream.filter(Files::isRegularFile)
                    .filter(p -> ignorer.accepts(p.toAbsolutePath().toString()))
                    .filter(App::isRelevantSourceOrDependency)
                    .toList();
        }

        int total = candidates.size();
        System.out.println("Discovered " + total + " candidate files; checking line counts...");

        AtomicInteger checked = new AtomicInteger(0);
        return candidates.parallelStream()
                .filter(p -> {
                    boolean ok = withinMaxLines(p, maxFileLines);
                    int n = checked.incrementAndGet();
                    if (n % 5000 == 0 || n == total) {
                        System.out.println("  Checked " + n + "/" + total + " files");
                    }
                    return ok;
                })
                .toList();
    }

    private static boolean isRelevantSourceOrDependency(Path p) {
        return Language.detectFor(p).isPresent() || CobolCopybookRepository.isCopybook(p);
    }

    private static boolean withinMaxLines(Path path, int maxFileLines) {
        try {
            if (Files.size(path) <= maxFileLines) {
                return true;
            }
        } catch (IOException ignored) {
        }
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

}
