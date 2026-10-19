#ifndef HEADER_GUARDED_CLASS_DECLARATIONS_H
#define HEADER_GUARDED_CLASS_DECLARATIONS_H

#include <windows.h>
#include <ctime>

class GuardedScheduler {
public:
    static GuardedScheduler* Acquire();

    GuardedScheduler();
    virtual void Start();
    virtual float GetStepSeconds();
    virtual void SetTargetStep(long targetStep);

    clock_t SleepFor(clock_t ticks);

private:
    static GuardedScheduler* instance;

    long targetStep;
    long previousTick;
};

#endif // HEADER_GUARDED_CLASS_DECLARATIONS_H
