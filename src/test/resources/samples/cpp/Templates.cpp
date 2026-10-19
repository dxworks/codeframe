template <typename T>
class Box {
public:
    T value;
    T get() { return value; }
};

template <typename T>
T identity(T input) {
    return input;
}

int use_templates() {
    Box<int> b;
    b.get();
    return identity(7);
}
