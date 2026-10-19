package org.dxworks.codeframe.analyzer;

import java.util.List;

public final class CCppAnalysisOptions {
    public final boolean allowQualifiedIdentifierCalls;
    public final boolean deepBaseObjectLookup;
    public final boolean includeAutoInDeclarationType;
    public final boolean allowFunctionReturningFunctionPointer;
    public final boolean includeAnonymousSpecifiers;
    public final List<String> functionSpecifierNodeTypes;
    public final List<String> fieldSpecifierNodeTypes;
    public final List<String> typeSpecifierNodeTypes;

    private CCppAnalysisOptions(Builder builder) {
        this.allowQualifiedIdentifierCalls = builder.allowQualifiedIdentifierCalls;
        this.deepBaseObjectLookup = builder.deepBaseObjectLookup;
        this.includeAutoInDeclarationType = builder.includeAutoInDeclarationType;
        this.allowFunctionReturningFunctionPointer = builder.allowFunctionReturningFunctionPointer;
        this.includeAnonymousSpecifiers = builder.includeAnonymousSpecifiers;
        this.functionSpecifierNodeTypes = List.copyOf(builder.functionSpecifierNodeTypes);
        this.fieldSpecifierNodeTypes = List.copyOf(builder.fieldSpecifierNodeTypes);
        this.typeSpecifierNodeTypes = List.copyOf(builder.typeSpecifierNodeTypes);
    }

    public static final CCppAnalysisOptions C = new Builder()
        .allowQualifiedIdentifierCalls(false)
        .deepBaseObjectLookup(true)
        .includeAutoInDeclarationType(false)
        .allowFunctionReturningFunctionPointer(true)
        .includeAnonymousSpecifiers(false)
        .functionSpecifierNodeTypes(List.of("storage_class_specifier", "function_specifier"))
        .fieldSpecifierNodeTypes(List.of("storage_class_specifier", "type_qualifier"))
        .typeSpecifierNodeTypes(List.of())
        .build();

    public static final CCppAnalysisOptions CPP = new Builder()
        .allowQualifiedIdentifierCalls(true)
        .deepBaseObjectLookup(false)
        .includeAutoInDeclarationType(true)
        .allowFunctionReturningFunctionPointer(false)
        .includeAnonymousSpecifiers(true)
        .functionSpecifierNodeTypes(List.of(
            "storage_class_specifier",
            "function_specifier",
            "virtual",
            "noexcept",
            "type_qualifier",
            "virtual_specifier",
            "explicit_function_specifier"
        ))
        .fieldSpecifierNodeTypes(List.of("storage_class_specifier", "type_qualifier"))
        .typeSpecifierNodeTypes(List.of("virtual_specifier"))
        .build();

    public static final class Builder {
        private boolean allowQualifiedIdentifierCalls;
        private boolean deepBaseObjectLookup;
        private boolean includeAutoInDeclarationType;
        private boolean allowFunctionReturningFunctionPointer;
        private boolean includeAnonymousSpecifiers;
        private List<String> functionSpecifierNodeTypes = List.of();
        private List<String> fieldSpecifierNodeTypes = List.of();
        private List<String> typeSpecifierNodeTypes = List.of();

        public Builder allowQualifiedIdentifierCalls(boolean v) { this.allowQualifiedIdentifierCalls = v; return this; }
        public Builder deepBaseObjectLookup(boolean v) { this.deepBaseObjectLookup = v; return this; }
        public Builder includeAutoInDeclarationType(boolean v) { this.includeAutoInDeclarationType = v; return this; }
        public Builder allowFunctionReturningFunctionPointer(boolean v) { this.allowFunctionReturningFunctionPointer = v; return this; }
        public Builder includeAnonymousSpecifiers(boolean v) { this.includeAnonymousSpecifiers = v; return this; }
        public Builder functionSpecifierNodeTypes(List<String> v) { this.functionSpecifierNodeTypes = v; return this; }
        public Builder fieldSpecifierNodeTypes(List<String> v) { this.fieldSpecifierNodeTypes = v; return this; }
        public Builder typeSpecifierNodeTypes(List<String> v) { this.typeSpecifierNodeTypes = v; return this; }

        public CCppAnalysisOptions build() { return new CCppAnalysisOptions(this); }
    }
}
