#include <cstddef>

int& declared_ref();
class ForwardOnly;
struct Pending;
enum class Waiting;

class Outer {
public:
    class Inner {
    public:
        int v;
    };
};

struct Plain {
    int x;
};

union Number {
    int i;
    float f;
};

enum class Color {
    RED,
    BLUE
};

int run_type_coverage() {
    Outer::Inner in;
    Number n;
    n.i = 1;
    return declared_ref() + n.i + in.v;
}
