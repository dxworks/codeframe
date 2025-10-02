namespace SampleApp
{
    public class OuterClass
    {
        public string Name { get; set; }

        public OuterClass(string name)
        {
            Name = name;
        }

        public void Print()
        {
            Console.WriteLine($"OuterClass: {Name}");
        }

        // First inner class
        public class InnerClassA
        {
            public int Value { get; set; }

            public InnerClassA(int value)
            {
                Value = value;
            }

            public void ShowValue()
            {
                Console.WriteLine($"InnerClassA value: {Value}");
            }

            // Nested inner class inside InnerClassA
            public class InnerClassB
            {
                public void Display()
                {
                    Console.WriteLine("Hello from InnerClassB!");
                }
            }
        }

        // Another inner class
        private class InnerHelper
        {
            public void DoWork()
            {
                Console.WriteLine("InnerHelper is doing work.");
            }
        }

        public void RunHelper()
        {
            var helper = new InnerHelper();
            helper.DoWork();
        }
    }
}