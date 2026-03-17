typedef struct Logger {
    int (*writer)(int);
} Logger;

int write_impl(int value) {
    return value;
}

int declared_only(const char *value);
int dispatch_values(void *restrict context, int (*cb)(int));
int (*make_dispatcher(void))(int);
int (*global_callback)(int);

Logger global_logger;

void log_message(Logger logger) {
    logger.writer(2);
}

int compute() {
    log_message(global_logger);
    return 0;
}

int init_value = compute();
