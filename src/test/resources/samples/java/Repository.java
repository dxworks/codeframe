package samples.java;

@Deprecated
public interface Repository<T, ID> {
    public T findById(ID id);
    public void save(T entity);
    public default int countAll() { return 0; }
}
