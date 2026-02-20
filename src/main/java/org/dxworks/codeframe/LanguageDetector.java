package org.dxworks.codeframe;

import java.nio.file.Path;
import java.util.Optional;

public class LanguageDetector {
    
    public static Optional<Language> detectLanguage(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        
        if (fileName.endsWith(".java")) {
            return Optional.of(Language.JAVA);
        } else if (fileName.endsWith(".ts") || fileName.endsWith(".tsx")) {
            return Optional.of(Language.TYPESCRIPT);
        } else if (fileName.endsWith(".js") || fileName.endsWith(".jsx")) {
            return Optional.of(Language.JAVASCRIPT);
        } else if (fileName.endsWith(".py")) {
            return Optional.of(Language.PYTHON);
        } else if (fileName.endsWith(".cs")) {
            return Optional.of(Language.CSHARP);
        } else if (fileName.endsWith(".php")) {
            return Optional.of(Language.PHP);
        } else if (fileName.endsWith(".sql")) {
            return Optional.of(Language.SQL);
        } else if (fileName.endsWith(".cbl") || fileName.endsWith(".cob") || fileName.endsWith(".cobol")) {
            return Optional.of(Language.COBOL);
        } else if (fileName.endsWith(".rb")) {
            return Optional.of(Language.RUBY);
        } else if (fileName.endsWith(".rs")) {
            return Optional.of(Language.RUST);
        }
        
        return Optional.empty();
    }

    public static boolean isCobolCopybook(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".cpy");
    }

    public static boolean isRelevantSourceOrDependency(Path p) {
        return detectLanguage(p).isPresent() || isCobolCopybook(p);
    }
}
