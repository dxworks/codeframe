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

int helper(int x) {
    return x + 1;
}

int main() {
    Counter c;
    helper(3);
    return 0;
}
