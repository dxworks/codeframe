package org.dxworks.codeframe;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public enum Language {
    JAVA("java", ".java"),
    JAVASCRIPT("javascript", ".js", ".jsx"),
    TYPESCRIPT("typescript", ".ts", ".tsx"),
    PYTHON("python", ".py"),
    CSHARP("csharp", ".cs"),
    C("c", ".c"),
    CPP("cpp", ".cpp", ".cc", ".cxx", ".h", ".hpp", ".hh", ".hxx"),
    PHP("php", ".php"),
    SQL("sql", ".sql"),
    COBOL("cobol", ".cbl", ".cob", ".cobol"),
    RUBY("ruby", ".rb"),
    RUST("rust", ".rs"),
    MARKDOWN("markdown", ".md", ".markdown", ".mkd", ".mkdn", ".mdwn", ".mdown"),
    XML("xml", ".xml");

    private final String name;
    private final String[] extensions;

    Language(String name, String... extensions) {
        this.name = name;
        this.extensions = extensions;
    }

    public String getName() {
        return name;
    }

    public String[] getExtensions() {
        return extensions;
    }

    public boolean matchesFileName(String lowerCaseFileName) {
        for (String ext : extensions) {
            if (lowerCaseFileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<Language> detectFor(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        for (Language lang : values()) {
            if (lang.matchesFileName(fileName)) {
                return Optional.of(lang);
            }
        }
        return Optional.empty();
    }
}
