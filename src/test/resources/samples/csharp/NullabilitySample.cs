#nullable enable
using System;

namespace Samples.Nullability;

public static class N
{
    public static int LengthOrDefault(string? s)
    {
        var len = s?.Length ?? 0;
        return len;
    }

    public static string NotNull(string? s) => s ?? throw new ArgumentNullException(nameof(s));

    public static string Suppress(string? s) => s!;
}
