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
        }
        
        return Optional.empty();
    }
}
