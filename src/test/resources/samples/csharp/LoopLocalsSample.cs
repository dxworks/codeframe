using System;
using System.Collections.Generic;

namespace Samples.LoopLocals;

public static class LoopLocals
{
    public static int SumUpTo(int n)
    {
        var sum = 0;
        for (var i = 0; i <= n; i++) sum += i;
        return sum;
    }

    public static int SumList(List<int> xs)
    {
        var sum = 0;
        foreach (var x in xs) sum += x;
        return sum;
    }

    public static void Process(ref int n, out int result, params int[] values)
    {
        result = 0;
        foreach (var v in values) result += v;
        n += result;
    }
}
