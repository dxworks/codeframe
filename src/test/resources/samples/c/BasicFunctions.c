#include <stdio.h>

static int global_count = 0;
const int max_size = 100;
extern volatile int global_limit;
extern void tracef(const char *fmt, ...);

inline int add(int a, int b) {
    int sum = a + b;
    printf("sum: %d", sum);
    return sum;
}

static void boot() {
    add(1, 2);
}

int main() {
    boot();
    return 0;
}
