package org.dxworks.codeframe;

public enum Language {
    JAVA("java"),
    JAVASCRIPT("javascript"),
    TYPESCRIPT("typescript"),
    PYTHON("python"),
    CSHARP("csharp"),
    PHP("php"),
    SQL("sql"),
    RUBY("ruby"),
    RUST("rust");

    private final String name;

    Language(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
