package samples.java;

public class AnonymousInnerClassesSample {
    private final Logger logger = new Logger();

    public Runnable createTask(String name) {
        // Anonymous class
        return new Runnable() {
            @Override
            public void run() {
                logger.info("Task " + name + " running");
                process();
            }

            private void process() {
                logger.info("Processing");
            }
        };
    }

    public void executeWithCallback(Callback callback) {
        callback.onComplete("result");
    }

    public void demonstrateLocalClass() {
        // Local class
        class LocalProcessor {
            void process(String data) {
                logger.info("Local: " + data);
            }
        }

        LocalProcessor processor = new LocalProcessor();
        processor.process("test");
    }

    // Inner class
    public class InnerHelper {
        public void assist() {
            logger.info("Inner helper assisting");
        }
    }

    // Static nested class
    public static class StaticHelper {
        public void help() {
            System.out.println("Static helper");
        }
    }
}

interface Callback {
    void onComplete(String result);
}

class Logger {
    public void info(String s) { }
}
