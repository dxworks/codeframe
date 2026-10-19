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

typedef struct OldPoint {
    int x;
    int y;
} OldPoint;

enum class Color {
    RED,
    BLUE
};

enum Priority : int {
    LOW,
    HIGH
};

auto declared_auto() {
    return 9;
}

int run_type_coverage() {
    Outer::Inner in;
    Number n;
    auto local_auto = declared_auto();
    n.i = 1;
    return declared_ref() + n.i + in.v + local_auto;
}
