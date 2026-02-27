package org.dxworks.codeframe;

public enum Language {
    JAVA("java", ".java"),
    JAVASCRIPT("javascript", ".js", ".jsx"),
    TYPESCRIPT("typescript", ".ts", ".tsx"),
    PYTHON("python", ".py"),
    CSHARP("csharp", ".cs"),
    PHP("php", ".php"),
    SQL("sql", ".sql"),
    COBOL("cobol", ".cbl", ".cob", ".cobol"),
    RUBY("ruby", ".rb"),
    RUST("rust", ".rs"),
    MARKDOWN("markdown", ".md", ".markdown");

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
}
