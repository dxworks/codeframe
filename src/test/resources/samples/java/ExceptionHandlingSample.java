package samples.java;

import java.io.FileReader;
import java.io.IOException;

public class ExceptionHandlingSample {
    private final Logger logger = new Logger();

    public void processFile(String path) {
        try (FileReader reader = new FileReader(path)) {
            int data = reader.read();
            logger.info("Read: " + data);
        } catch (IOException | IllegalArgumentException e) {
            logger.error(e.getMessage());
            throw new RuntimeException(e);
        } finally {
            cleanup();
        }
    }

    public void validateInput(String input) throws ValidationException {
        if (input == null || input.isEmpty()) {
            throw new ValidationException("Input cannot be empty");
        }
        logger.info("Valid input: " + input);
    }

    private void cleanup() {
        logger.info("Cleanup completed");
    }
}

class ValidationException extends Exception {
    public ValidationException(String message) {
        super(message);
    }
}

class Logger {
    public void info(String s) { }
    public void error(String s) { }
}
