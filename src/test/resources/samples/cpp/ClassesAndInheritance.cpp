#include <string>

class Counter {
public:
    Counter() {}
    ~Counter() {}
    virtual int value() const { return count; }
private:
    int count;
};

class AdvancedCounter final : public Counter {
public:
    int value() const override { return 1; }
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
};

static inline int helper(int x) {
    return x + 1;
}

int main() {
    Widget w;
    w.self()->value();
    helper(3);
    return 0;
}
