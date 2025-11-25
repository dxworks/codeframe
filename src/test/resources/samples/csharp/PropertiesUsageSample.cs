using System;

namespace Samples.Properties;

public class Person
{
    private int _age;
    public string FirstName { get; }
    public string LastName { get; }

    public Person(string firstName, string lastName, int age)
    {
        FirstName = firstName;
        LastName = lastName;
        Age = age;
    }

    public string DisplayName
    {
        get
        {
            var space = " ";
            var name = string.Concat(FirstName, space, LastName);
            return name.ToUpperInvariant();
        }
    }

    public int Age
    {
        get => _age;
        set
        {
            var v = value < 0 ? 0 : value;
            _age = v;
        }
    }

    public int NameLength => DisplayName.Length;
}

public static class PersonService
{
    public static void Promote(Person p)
    {
        if (p.Age >= 30 && p.NameLength > 5)
        {
            p.Age = p.Age + 1;
        }
    }

    public static string Describe(Person p)
    {
        var label = p.DisplayName;
        for (var i = 0; i < p.NameLength % 3; i++)
        {
            label = label + "!";
        }
        return $"{label} ({p.Age})";
    }
}
