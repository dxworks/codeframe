using System.Collections;
using System.Collections.Generic;

namespace Samples.Iterators;

public static class Seq
{
    public static IEnumerable<int> UpTo(int n)
    {
        for (var i = 0; i <= n; i++) yield return i;
    }
}

public class SimpleMap<K, V> : IEnumerable<KeyValuePair<K, V>>
{
    private readonly Dictionary<K, V> _inner = new();

    public V this[K key]
    {
        get => _inner[key];
        set => _inner[key] = value;
    }

    public IEnumerator<KeyValuePair<K, V>> GetEnumerator() => _inner.GetEnumerator();
    IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();
}
