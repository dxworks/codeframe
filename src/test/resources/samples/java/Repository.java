package samples.java;

interface Identifiable<ID> {
    ID getId();
}

interface Nameable {
    String getName();
}

@Deprecated
public interface Repository<T, ID> extends Identifiable<ID>, Nameable {
    public T findById(ID id);
    public void save(T entity);
    public default int countAll() { return 0; }
}
