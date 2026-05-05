package org.dxworks.codeframe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dxworks.codeframe.analyzer.cobol.CobolCopybookRepository;
import org.dxworks.codeframe.model.Analysis;
import org.dxworks.utils.ignorer.Ignorer;
import org.dxworks.utils.ignorer.IgnorerBuilder;

import java.io.BufferedWriter;
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
            .setDefaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY);

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
                        .filter(App::isRelevantSourceOrDependency)
                        .filter(p -> withinMaxLines(p, maxFileLines))
                        .forEach(files::add);
            }
        } else if (Files.isRegularFile(input)) {
            if (ignorer.accepts(input.toAbsolutePath().toString())
                    && isRelevantSourceOrDependency(input)
                    && withinMaxLines(input, maxFileLines)) {
                files.add(input);
            }
        }

        return files;
    }

    private static boolean isRelevantSourceOrDependency(Path p) {
        return Language.detectFor(p).isPresent() || CobolCopybookRepository.isCopybook(p);
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

}
