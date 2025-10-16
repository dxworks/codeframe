using System;

namespace Samples.Records;

public record Person(string Name, int Age);

public static class Classifier
{
    public static string Describe(Person p) => p switch
    {
        { Age: < 13 } => "Child",
        { Age: >= 13 and < 20 } => "Teen",
        { Name: "Alice" } => "Special",
        _ => "Adult"
    };

    public static Person Birthday(Person p) => p with { Age = p.Age + 1 };
}
