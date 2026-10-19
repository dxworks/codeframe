typedef struct Point {
    int x;
    int y;
} Point;

struct ForwardOnly;
enum Pending;

typedef union Number {
    int i;
    float f;
} Number;

enum Status {
    STATUS_OK,
    STATUS_FAIL
};

struct Flags {
    unsigned int readable : 1;
    unsigned int writable : 1;
};

typedef int (*cmp_fn)(const void* a, const void* b);
