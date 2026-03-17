#include <stdio.h>

int global_count = 0;

int add(int a, int b) {
    int sum = a + b;
    printf("sum: %d", sum);
    return sum;
}

void boot() {
    add(1, 2);
}

int main() {
    boot();
    return 0;
}
