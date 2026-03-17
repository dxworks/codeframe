typedef struct Point {
    int x;
    int y;
} Point;

typedef union Number {
    int i;
    float f;
} Number;

enum Status {
    STATUS_OK,
    STATUS_FAIL
};

typedef int (*cmp_fn)(const void* a, const void* b);
