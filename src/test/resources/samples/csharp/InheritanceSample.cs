using System;

namespace Samples.Inheritance;

public abstract class Animal
{
    public string Name { get; }
    protected Animal(string name) => Name = name;
    public virtual string Speak() => "…";
}

public class Dog : Animal
{
    public Dog(string name) : base(name) { }
    public sealed override string Speak() => "Woof";
}

public class LoudDog : Dog
{
    public LoudDog(string name) : base(name) { }
}
