package org.dxworks.codeframe;

public enum Language {
    JAVA("java"),
    JAVASCRIPT("javascript"),
    TYPESCRIPT("typescript"),
    PYTHON("python"),
    CSHARP("csharp"),
    PHP("php");

    private final String name;

    Language(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
