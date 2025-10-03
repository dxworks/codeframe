package org.example.demo;

// Public class must match the file name
public class MultipleClasses {

    // Example of method overloading (same name, different parameters)
    public void greet(String name) {
        System.out.println("Hello, " + name);
    }

    public void greet(String name, int times) {
        for (int i = 0; i < times; i++) {
            System.out.println("Hello, " + name);
			ExtraClass x = new ExtraClass();
			x.doWork();
        }
    }

    // Example of an inner class
    public class InnerHelper {
        public void assist() {
            System.out.println("InnerHelper assisting Demo instance");
        }
    }
}

// Another top-level class (package-private, not public)
class ExtraClass {
    public void doWork() {
        System.out.println("ExtraClass doing work");
    }
}
