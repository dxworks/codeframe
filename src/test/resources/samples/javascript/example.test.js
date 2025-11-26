// This shows that top level method calls are not extracted

describe('Result total validation', () => {
  test('total should be greater than 100', () => {
    const result = { total: '123.45' };
    expect(parseFloat(result.total)).toBeGreaterThan(100);
  });

  test('total should still be a number after parsing', () => {
    const result = { total: '123.45' };
    const totalValue = parseFloat(result.total);
    expect(typeof totalValue).toBe('number');
  });
});

describe('Array operations', () => {
  test('should filter even numbers', () => {
    const numbers = [1, 2, 3, 4, 5, 6];
    const evens = numbers.filter(n => n % 2 === 0);
    expect(evens).toEqual([2, 4, 6]);
  });

  test('should map values correctly', () => {
    const items = [{ value: 1 }, { value: 2 }];
    const values = items.map(item => item.value);
    expect(values).toContain(1);
  });
});

describe('Async operations', () => {
  test('should resolve promise', async () => {
    const fetchData = () => Promise.resolve({ data: 'test' });
    const result = await fetchData();
    expect(result.data).toBe('test');
  });

  test('should handle rejected promise', async () => {
    const failingFetch = () => Promise.reject(new Error('Network error'));
    await expect(failingFetch()).rejects.toThrow('Network error');
  });
});

// Nested describe blocks
describe('User validation', () => {
  describe('when user is valid', () => {
    test('should have name', () => {
      const user = { name: 'John', email: 'john@example.com' };
      expect(user.name).toBeDefined();
    });

    test('should have email', () => {
      const user = { name: 'John', email: 'john@example.com' };
      expect(user.email).toContain('@');
    });
  });

  describe('when user is invalid', () => {
    test('should fail without name', () => {
      const user = { email: 'john@example.com' };
      expect(user.name).toBeUndefined();
    });
  });
});

// Using beforeEach and afterEach
describe('Counter tests', () => {
  let counter;

  beforeEach(() => {
    counter = { value: 0 };
  });

  afterEach(() => {
    counter = null;
  });

  test('should increment', () => {
    counter.value++;
    expect(counter.value).toBe(1);
  });

  test('should decrement', () => {
    counter.value--;
    expect(counter.value).toBe(-1);
  });
});
