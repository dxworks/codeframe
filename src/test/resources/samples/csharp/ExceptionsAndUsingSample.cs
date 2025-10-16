using System;
using System.IO;

namespace Samples.Exceptions;

public class InvalidInputException : Exception
{
    public InvalidInputException(string message) : base(message) { }
}

public static class Runner
{
    public static int ParsePositive(string s)
    {
        try
        {
            var value = int.Parse(s);
            if (value <= 0) throw new InvalidInputException("Must be positive");
            return value;
        }
        catch (FormatException ex)
        {
            throw new InvalidInputException($"Bad format: {ex.Message}");
        }
        finally
        {
        }
    }

    public static int ReadFirstByteLength(string path)
    {
        using var stream = File.OpenRead(path);
        return stream.ReadByte();
    }
}
