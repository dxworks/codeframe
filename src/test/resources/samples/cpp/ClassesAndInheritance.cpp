#include <string>

class Counter {
public:
    Counter() {}
    explicit Counter(int start) : count(start) {}
    Counter(const Counter&) = delete;
    Counter& operator=(const Counter&) = default;
    ~Counter() {}
    virtual int value() const { return count; }
    virtual int score() const = 0;
    friend int inspect(const Counter& c) { return c.count; }

    constexpr int base() const noexcept { return 7; }
    consteval static int meta() { return 11; }
private:
    mutable int cache;
    int count;
};

class AdvancedCounter final : public Counter {
public:
    explicit AdvancedCounter(int start) : Counter(start) {}
    int value() const override { return 1; }
    int score() const override { return value(); }
    int operator+(int other) { return value() + other; }
};

class Printable {
public:
    int label() { return 0; }
};

class Widget : public Counter, public Printable {
public:
    static int instances;
    Counter* self() { return this; }
    int score() const override { return 2; }
};

static inline int helper(int x) {
    return x + 1;
}

int main() {
    Widget w;
    AdvancedCounter ac(3);
    w.self()->value();
    ac.score();
    inspect(ac);
    helper(3);
    return 0;
}
