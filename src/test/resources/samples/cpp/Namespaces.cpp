namespace math {

class Vector {
public:
    int magnitude() { return 1; }
};

int sum(int a, int b) {
    return a + b;
}

template <typename T>
T passthrough(T value) {
    return value;
}

}

int run() {
    return math::sum(1, 2);
}
