#include <string>

class Counter {
public:
    Counter() {}
    ~Counter() {}
    int value() { return count; }
private:
    int count;
};

class AdvancedCounter : public Counter {
public:
    int operator+(int other) { return value() + other; }
};

class Printable {
public:
    int label() { return 0; }
};

class Widget : public Counter, public Printable {
public:
    Counter* self() { return this; }
};

int helper(int x) {
    return x + 1;
}

int main() {
    Widget w;
    w.self()->value();
    helper(3);
    return 0;
}
