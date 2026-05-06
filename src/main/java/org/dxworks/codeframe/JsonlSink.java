package org.dxworks.codeframe;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dxworks.codeframe.model.Analysis;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns the JSONL output stream for an analysis run: opens the file, writes the
 * {@code run}/{@code analysis}/{@code error}/{@code done} envelopes, serializes
 * with the project's Jackson configuration, and synchronizes concurrent writes
 * from parallel analyzers.
 */
public final class JsonlSink implements AutoCloseable {

    private final BufferedWriter writer;
    private final ObjectMapper mapper;
    private final Instant startedAt;

    public JsonlSink(Path output) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        this.writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
        this.mapper = new ObjectMapper()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY);
        this.startedAt = Instant.now();
    }

    public Instant startedAt() {
        return startedAt;
    }

    public void writeRun(Path input, int totalFiles) throws IOException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("kind", "run");
        envelope.put("started_at", startedAt.toString());
        envelope.put("input_path", input.toString());
        envelope.put("total_files", totalFiles);
        writeLine(envelope);
    }

    public void writeAnalysis(Analysis analysis) throws IOException {
        writeLine(analysis);
    }

    public void writeError(Path file, String language, String message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("kind", "error");
        envelope.put("file", file.toString());
        envelope.put("language", language);
        envelope.put("error", message);
        try {
            writeLine(envelope);
        } catch (IOException e) {
            System.err.println("Failed to write error for " + file + ": " + e.getMessage());
        }
    }

    public void writeDone(int filesAnalyzed, int filesWithErrors) throws IOException {
        Instant endedAt = Instant.now();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("kind", "done");
        envelope.put("ended_at", endedAt.toString());
        envelope.put("files_analyzed", filesAnalyzed);
        envelope.put("files_with_errors", filesWithErrors);
        envelope.put("duration_seconds", Duration.between(startedAt, endedAt).getSeconds());
        writeLine(envelope);
    }

    private synchronized void writeLine(Object envelope) throws IOException {
        writer.write(mapper.writeValueAsString(envelope));
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
