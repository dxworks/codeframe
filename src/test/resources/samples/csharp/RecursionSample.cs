namespace RecursionTest
{
    public class RecursionSample
    {
        // Simple recursion - should be "yes"
        public int Factorial(int n)
        {
            if (n <= 1) return 1;
            return n * Factorial(n - 1);
        }

        public int CallingOverlaods(int a, int b)
        {
            DoStuff(a);
            DoStuff(a, b);
        }

        // Overloaded methods - different signatures
        public void DoStuff(int a)
        {
            System.Console.WriteLine(a);
        }

        public void DoStuff(int a, int b)
        {
            DoStuff(a); // Not recursive - different signature
            System.Console.WriteLine(b);
        }

        // Mutual recursion (not detected as recursive since different method names)
        public int Even(int n)
        {
            if (n == 0) return 1;
            return Odd(n - 1);
        }

        public int Odd(int n)
        {
            if (n == 0) return 0;
            return Even(n - 1);
        }

        // Recursion with this qualifier - should be "yes"
        public int Sum(int n)
        {
            if (n <= 0) return 0;
            return n + this.Sum(n - 1);
        }

        // Recursion with unknown argument type - should be "maybe"
        public string Process(string input)
        {
            var transformed = input.ToUpper();
            return Process(transformed.Substring(0, 1)); // argument type known from inference
        }

        // Non-recursive call to another object's method
        public void CallOther(RecursionSample other)
        {
            other.DoStuff(5); // Not recursive - different object
        }

        // Static recursion
        public static int StaticFactorial(int n)
        {
            if (n <= 1) return 1;
            return n * RecursionSample.StaticFactorial(n - 1); // Should be "yes"
        }
    }
}
