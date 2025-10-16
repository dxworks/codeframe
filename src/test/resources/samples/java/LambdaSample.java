package samples.java;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LambdaSample {
    private final Logger logger = new Logger();

    public List<String> namesLongerThan3(List<User> users) {
        // Inline lambda
        users.forEach(u -> logger.info(u.getName()));
        // Method reference
        users.forEach(System.out::println);
        // Stream pipeline with lambdas
        List<String> names = users.stream()
                .map(User::getName)
                .filter(n -> n.length() > 3)
                .collect(Collectors.toList());
        return names;
    }

    public void incrementAll(List<Counter> counters) {
        counters.forEach(c -> c.increment());
    }
}

class User {
    private String name;
    public String getName() { return name; }
}

class Logger {
    public void info(String s) { }
}

class Counter {
    public void increment() { }
}
