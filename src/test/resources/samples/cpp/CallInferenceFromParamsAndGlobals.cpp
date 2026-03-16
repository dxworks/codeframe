#include <vector>

template <typename T>
T declared_identity(T input);

namespace util {

template <typename U>
U declared_ns(U value);

}

class Logger {
public:
    int writer(int value) { return value; }
};

Logger global_logger;
std::vector<int> global_values = {};
int (*global_callback)(int);

int log_message(Logger logger) {
    std::vector<int> local_values = {};
    return logger.writer(2)
        + global_logger.writer(3)
        + global_values.size()
        + local_values.size();
}

int compute() {
    return 42;
}

int boot_value = compute();

int run_decl() {
    return declared_identity(1) + util::declared_ns(2);
}
