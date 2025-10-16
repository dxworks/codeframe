package samples.java;

public class ConstructorSample {
    private final Dependency dep;
    private int count;

    public ConstructorSample(Dependency dep, int initial) {
        this.dep = dep;
        this.count = initial;
        dep.init();
    }

    public int getCount() { return count; }
}

class Dependency {
    public void init() { }
}
