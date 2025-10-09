namespace Samples.Interfaces;

[Obsolete]
public interface IRepository<T, ID>
{
    T FindById(ID id);
    void Save(T entity);
}

[Obsolete]
public interface INamed
{
    string GetName();
}
