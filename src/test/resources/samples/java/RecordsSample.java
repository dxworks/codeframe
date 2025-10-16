package samples.java;

// Java 14+ Records
public record Point(int x, int y) {
    // Compact constructor
    public Point {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Coordinates must be non-negative");
        }
    }

    // Additional constructor
    public Point(int value) {
        this(value, value);
    }

    // Instance method
    public double distanceFromOrigin() {
        return Math.sqrt(x * x + y * y);
    }

    // Static method
    public static Point origin() {
        return new Point(0, 0);
    }
}

record Person(String name, int age) {
    // Canonical constructor with validation
    public Person(String name, int age) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name required");
        }
        this.name = name;
        this.age = age;
    }

    public boolean isAdult() {
        return age >= 18;
    }
}
