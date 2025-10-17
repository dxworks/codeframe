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

public class Demo<T> {

    public void greet<T>(T name, int times)
    {
        for (var i = 0; i < times; i++)
        {
            Console.WriteLine($"Hello, {name}"); // uses name?.ToString()
            var x = new ExtraClass();
            x.DoWork();
        }
    }
    
    public void greet<T, U>(T name, U surname, int times)
    {
        for (var i = 0; i < times; i++)
        {
            Console.WriteLine($"Hello, {name} {surname}"); // uses name?.ToString()
            var x = new ExtraClass();
            x.DoWork();
        }
    }

}
