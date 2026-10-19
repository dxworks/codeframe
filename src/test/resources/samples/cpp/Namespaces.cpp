using GlobalCount = int;

namespace math {

using Value = int;

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

namespace detail {
int scale(int value) {
    return value * 2;
}
}

}

namespace {
int hidden_value() {
    return 5;
}
}

int run() {
    return math::sum(1, 2) + math::detail::scale(3) + hidden_value();
}
