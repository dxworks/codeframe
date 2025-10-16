using System;
using System.Collections.Generic;

namespace Samples.TuplesRanges;

public static class Utils
{
    public static (int sum, int count) SumAndCount(ReadOnlySpan<int> data)
    {
        var sum = 0;
        for (var i = 0; i < data.Length; i++) sum += data[i];
        return (sum, data.Length);
    }

    public static int[] Tail(int[] xs)
    {
        var tail = xs[1..];
        return tail;
    }

    public static void Demo()
    {
        Dictionary<string, int> dict = new();
        List<int> list = new();
        var arr = new[] { 1, 2, 3, 4 };
        var (sum, cnt) = SumAndCount(arr);
        var last = arr[^1];
    }
}
