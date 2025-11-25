// Classic JavaScript patterns: IIFE, prototype inheritance, constructor functions, mixins

// 1. Immediately Invoked Function Expression (IIFE)
const Counter = (function() {
    let count = 0;
    
    return {
        increment() {
            return ++count;
        },
        decrement() {
            return --count;
        },
        getCount() {
            return count;
        }
    };
})();

// IIFE with parameters
const Logger = (function(prefix) {
    const logs = [];
    
    function formatMessage(level, message) {
        return `[${prefix}] ${level}: ${message}`;
    }
    
    return {
        log(message) {
            const formatted = formatMessage('LOG', message);
            logs.push(formatted);
            console.log(formatted);
        },
        error(message) {
            const formatted = formatMessage('ERROR', message);
            logs.push(formatted);
            console.error(formatted);
        },
        getLogs() {
            return [...logs];
        }
    };
})('APP');

// 2. Named function expressions
const factorial = function calculateFactorial(n) {
    if (n <= 1) return 1;
    return n * calculateFactorial(n - 1);
};

const fibonacci = function fib(n, memo = {}) {
    if (n in memo) return memo[n];
    if (n <= 1) return n;
    memo[n] = fib(n - 1, memo) + fib(n - 2, memo);
    return memo[n];
};

// 3. Constructor functions (pre-class syntax)
function Person(name, age) {
    this.name = name;
    this.age = age;
}

Person.prototype.greet = function() {
    return `Hello, I'm ${this.name}`;
};

Person.prototype.haveBirthday = function() {
    this.age++;
    return this.age;
};

Person.create = function(name, age) {
    return new Person(name, age);
};

// 4. Prototype-based inheritance
function Employee(name, age, department) {
    Person.call(this, name, age);
    this.department = department;
}

Employee.prototype = Object.create(Person.prototype);
Employee.prototype.constructor = Employee;

Employee.prototype.getDetails = function() {
    return `${this.name}, ${this.age}, ${this.department}`;
};

Employee.prototype.greet = function() {
    return `${Person.prototype.greet.call(this)}, I work in ${this.department}`;
};

// 5. Mixins using Object.assign
const Serializable = {
    toJSON() {
        return JSON.stringify(this);
    },
    fromJSON(json) {
        return Object.assign(this, JSON.parse(json));
    }
};

const Comparable = {
    equals(other) {
        return JSON.stringify(this) === JSON.stringify(other);
    },
    compare(other) {
        const thisStr = JSON.stringify(this);
        const otherStr = JSON.stringify(other);
        return thisStr.localeCompare(otherStr);
    }
};

const Observable = {
    _observers: null,
    
    initObservable() {
        this._observers = new Set();
    },
    
    subscribe(observer) {
        if (!this._observers) this.initObservable();
        this._observers.add(observer);
        return () => this._observers.delete(observer);
    },
    
    notify(data) {
        if (this._observers) {
            this._observers.forEach(observer => observer(data));
        }
    }
};

// Applying mixins to a constructor
function DataStore(initialData) {
    this.data = initialData || {};
    this.initObservable();
}

Object.assign(DataStore.prototype, Serializable, Observable);

DataStore.prototype.set = function(key, value) {
    this.data[key] = value;
    this.notify({ type: 'set', key, value });
};

DataStore.prototype.get = function(key) {
    return this.data[key];
};

// 6. Module pattern variations
const EventBus = (function() {
    const events = {};
    
    function on(event, callback) {
        if (!events[event]) {
            events[event] = [];
        }
        events[event].push(callback);
    }
    
    function off(event, callback) {
        if (events[event]) {
            events[event] = events[event].filter(cb => cb !== callback);
        }
    }
    
    function emit(event, ...args) {
        if (events[event]) {
            events[event].forEach(callback => callback(...args));
        }
    }
    
    function once(event, callback) {
        const wrapper = (...args) => {
            callback(...args);
            off(event, wrapper);
        };
        on(event, wrapper);
    }
    
    return { on, off, emit, once };
})();

// 7. Factory functions
function createValidator(rules) {
    const validators = {
        required: (value) => value !== null && value !== undefined && value !== '',
        minLength: (value, min) => String(value).length >= min,
        maxLength: (value, max) => String(value).length <= max,
        pattern: (value, regex) => regex.test(String(value)),
        range: (value, min, max) => value >= min && value <= max
    };
    
    return {
        validate(data) {
            const errors = [];
            
            for (const [field, fieldRules] of Object.entries(rules)) {
                for (const [rule, param] of Object.entries(fieldRules)) {
                    const validator = validators[rule];
                    if (validator && !validator(data[field], param)) {
                        errors.push({ field, rule, param });
                    }
                }
            }
            
            return {
                isValid: errors.length === 0,
                errors
            };
        }
    };
}

// 8. Revealing module pattern
const Calculator = (function() {
    let result = 0;
    
    function add(value) {
        result += value;
        return this;
    }
    
    function subtract(value) {
        result -= value;
        return this;
    }
    
    function multiply(value) {
        result *= value;
        return this;
    }
    
    function divide(value) {
        if (value !== 0) {
            result /= value;
        }
        return this;
    }
    
    function getResult() {
        return result;
    }
    
    function reset() {
        result = 0;
        return this;
    }
    
    return {
        add,
        subtract,
        multiply,
        divide,
        getResult,
        reset
    };
})();

// Export for module systems
export {
    Counter,
    Logger,
    factorial,
    fibonacci,
    Person,
    Employee,
    Serializable,
    Comparable,
    Observable,
    DataStore,
    EventBus,
    createValidator,
    Calculator
};
