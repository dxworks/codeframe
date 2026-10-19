#ifndef MACRO_HEAVY_HEADER_H
#define MACRO_HEAVY_HEADER_H

#include <stddef.h>
#include <stdint.h>

#define LOG_TAG "macro-heavy"
#define FLAG_ENABLED(mask, bit) (((mask) & (bit)) != 0)
#define SAFE_APPLY(cb, value) \
    do { \
        if ((cb) != nullptr) { \
            (cb)(value); \
        } \
    } while (0)

#if !defined(MAX_RETRIES)
#define MAX_RETRIES 3
#endif

typedef struct MacroRecord {
    uint32_t flags;
    size_t size;
} MacroRecord;

extern uint32_t global_mask;
extern int (*macro_handler)(int);

int run_macro_flow(MacroRecord *record, int (*handler)(int));

#ifdef __cplusplus
int bind_macro_record(const MacroRecord &record);
#endif

#endif
