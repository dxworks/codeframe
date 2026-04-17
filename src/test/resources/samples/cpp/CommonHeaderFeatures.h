#ifndef COMMON_HEADER_FEATURES_H
#define COMMON_HEADER_FEATURES_H

#include <stddef.h>
#include <stdint.h>

#define DEFAULT_TIMEOUT_MS 1000
#define ARRAY_LEN(arr) (sizeof(arr) / sizeof((arr)[0]))

typedef enum StatusCode {
    STATUS_OK = 0,
    STATUS_ERROR = 1
} StatusCode;

typedef struct Point {
    int x;
    int y;
} Point;

typedef struct HeaderConfig {
    size_t buffer_size;
    uint32_t flags;
    Point origin;
} HeaderConfig;

typedef int (*CompareFn)(const void *lhs, const void *rhs);

extern const HeaderConfig DEFAULT_CONFIG;
extern int global_counter;
extern int (*global_callback_cpp)(int);

#ifdef __cplusplus
extern "C" {
#endif

StatusCode init_config(HeaderConfig *config);
int validate_name(const char *name);
int sort_points(Point *items, size_t count, CompareFn compare);
int legacy_sum(int a, int b);

static inline int clamp_int(int value, int min_value, int max_value) {
    if (value < min_value) {
        return min_value;
    }
    if (value > max_value) {
        return max_value;
    }
    return value;
}

#ifdef __cplusplus
}

int touch_point(Point &point);
int touch_point_const(const Point &point);
int consume_point(Point &&point);

namespace sample {
template<typename T> class ForwardDecl;
class Math final {
public:
    static int add(int a, int b);
};
}
#endif

#endif
