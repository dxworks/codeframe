using System;

namespace Samples.Extensions;

public static class StringExtensions
{
    public static bool IsBlank(this string? s) => string.IsNullOrWhiteSpace(s);

    public static bool IsBlank2(this string? s) => Samples.Extensions.StringExtensions.IsBlank(s);
}

public static class Demo
{
    public static bool Check(string? value) => value.IsBlank();
}
