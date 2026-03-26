#ifndef BASIC_HEADER_H
#define BASIC_HEADER_H

#include <stddef.h>

typedef enum HeaderStatus {
    HEADER_OK = 0,
    HEADER_FAIL = 1
} HeaderStatus;

typedef struct HeaderPoint {
    int x;
    int y;
} HeaderPoint;

extern const int default_limit;
extern int global_total;

HeaderStatus init_header(HeaderPoint *point);
int trace_header(const char *msg, ...);

#endif
