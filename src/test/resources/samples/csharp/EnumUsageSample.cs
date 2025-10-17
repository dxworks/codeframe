using System;

namespace Samples.Enums;

public enum Status
{
    Pending = 0,
    Active = 1,
    Closed = 2
}

public class EnumUsageSample
{
    public string GetStatusMessage(Status status)
    {
        switch (status)
        {
            case Status.Pending:
                return "Waiting";
            case Status.Active:
                return "Running";
            case Status.Closed:
                return "Done";
            default:
                return "Unknown";
        }
    }

    public void PrintAll()
    {
        foreach (var s in Enum.GetValues(typeof(Status)))
        {
            Console.WriteLine(s.ToString());
        }
    }
}
