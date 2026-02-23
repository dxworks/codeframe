package org.dxworks.codeframe.analyzer.cobol;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Run-scoped repository of COBOL copybooks.
 *
 * - Built from the filtered list of copybook files.
 * - Detects duplicate copybook names across multiple paths and prints them.
 * - Provides the deduplicated list of copybook files for the preprocessor.
 */
public final class CobolCopybookRepository {

    private final Map<String, File> byNormalizedName;

    public CobolCopybookRepository(List<File> copybookFiles) {
        Objects.requireNonNull(copybookFiles, "copybookFiles");

        // Track candidates by key so we can both (a) warn about duplicates and (b) choose a winner deterministically.
        Map<String, List<File>> candidatesByKey = new HashMap<>();

        for (File f : copybookFiles) {
            if (f == null) continue;
            if (!f.isFile()) continue;

            String fileName = f.getName();

            // Index under base-name (strip extension) and full filename
            String keyBase = normalizeCopybookToken(stripExtension(fileName));
            String keyFull = normalizeCopybookToken(fileName);

            candidatesByKey.computeIfAbsent(keyBase, k -> new ArrayList<>()).add(f);
            candidatesByKey.computeIfAbsent(keyFull, k -> new ArrayList<>()).add(f);
        }

        printDuplicates(candidatesByKey);

        Map<String, File> index = new HashMap<>();
        for (Map.Entry<String, List<File>> e : candidatesByKey.entrySet()) {
            index.put(e.getKey(), pickWinner(e.getValue()));
        }

        this.byNormalizedName = Collections.unmodifiableMap(index);
    }

    /**
     * Returns the resolved copybook files (deduplicated, one per normalized key).
     */
    public Collection<File> copyFiles() {
        return byNormalizedName.values();
    }

    private static void printDuplicates(Map<String, List<File>> candidatesByKey) {
        Map<String, List<File>> dupes = new TreeMap<>();

        for (Map.Entry<String, List<File>> e : candidatesByKey.entrySet()) {
            String key = e.getKey();

            // Unique by absolute path
            LinkedHashMap<String, File> unique = new LinkedHashMap<>();
            for (File f : e.getValue()) {
                unique.put(f.getAbsolutePath(), f);
            }

            if (unique.size() > 1) {
                dupes.put(key, new ArrayList<>(unique.values()));
            }
        }

        if (dupes.isEmpty()) {
            return;
        }

        synchronized (System.out) {
            System.out.println("Warning: duplicate COBOL copybook names detected (multiple paths for same key):");
            for (Map.Entry<String, List<File>> e : dupes.entrySet()) {
                System.out.println("  - " + e.getKey());
                for (File f : e.getValue()) {
                    System.out.println("      " + f.getAbsolutePath());
                }
            }
        }
    }

    private static File pickWinner(List<File> candidates) {
        // De-dupe by absolute path, then pick deterministically:
        // 1) shortest absolute path length
        // 2) lexicographically smallest absolute path
        Collection<File> unique = candidates.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        f -> f.getAbsolutePath(),
                        f -> f,
                        (a, b) -> a,
                        LinkedHashMap::new
                ))
                .values();

        return unique.stream()
                .min(Comparator
                        .comparingInt((File f) -> f.getAbsolutePath().length())
                        .thenComparing(File::getAbsolutePath))
                .orElseThrow(() -> new IllegalArgumentException("No copybook candidates"));
    }

    private static String normalizeCopybookToken(String token) {
        String t = token.trim().toLowerCase(Locale.ROOT);

        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            t = t.substring(1, t.length() - 1).trim();
        }

        // Remove trailing punctuation (common with naive tokenization)
        t = t.replaceAll("[.;,]+$", "");

        // Normalize separators
        t = t.replace('\\', '/');

        // Keep only last segment
        int slash = t.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < t.length()) {
            t = t.substring(slash + 1);
        }

        return t;
    }

    private static String stripExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0) return fileName;
        return fileName.substring(0, lastDot);
    }
}
