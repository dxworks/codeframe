using System;

namespace Samples.Events;

public class ValueChangedEventArgs : EventArgs
{
    public int OldValue { get; }
    public int NewValue { get; }
    public ValueChangedEventArgs(int oldValue, int newValue)
    {
        OldValue = oldValue;
        NewValue = newValue;
    }
}

public class Counter
{
    private int _value;
    public event EventHandler<ValueChangedEventArgs>? Changed;

    public void Increment(Func<int, int> step)
    {
        var oldV = _value;
        _value = step(_value);
        Changed?.Invoke(this, new ValueChangedEventArgs(oldV, _value));
    }
}

public static class Demo
{
    public static void WireUp()
    {
        var c = new Counter();
        Action<object?, ValueChangedEventArgs> onChanged = (_, e) => Console.WriteLine(e.NewValue);
        c.Changed += (s, e) => onChanged(s, e);
        c.Increment(v => v + 1);
    }
}
