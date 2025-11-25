// Modern JavaScript syntax: optional chaining, nullish coalescing, 
// tagged templates, try/catch/finally, class expressions, object literal patterns

// 1. Optional chaining (?.)
export function getUserCity(user) {
    return user?.address?.city;
}

export function getFirstItemName(order) {
    return order?.items?.[0]?.name;
}

export function safelyCallMethod(obj) {
    return obj?.process?.();
}

export function getNestedValue(data) {
    const street = data?.user?.profile?.address?.street;
    const zipCode = data?.user?.profile?.address?.zipCode;
    const callback = data?.handlers?.onSuccess;
    
    callback?.({ street, zipCode });
    
    return { street, zipCode };
}

// 2. Nullish coalescing (??)
export function getConfigValue(config, key, defaultValue) {
    return config[key] ?? defaultValue;
}

export function mergeSettings(userSettings, defaults) {
    return {
        theme: userSettings?.theme ?? defaults.theme ?? 'light',
        fontSize: userSettings?.fontSize ?? defaults.fontSize ?? 14,
        language: userSettings?.language ?? defaults.language ?? 'en',
        notifications: userSettings?.notifications ?? defaults.notifications ?? true
    };
}

// Combining optional chaining and nullish coalescing
export function getSafeValue(obj, path, defaultValue) {
    const parts = path.split('.');
    let current = obj;
    
    for (const part of parts) {
        current = current?.[part];
        if (current === undefined) {
            return defaultValue ?? null;
        }
    }
    
    return current ?? defaultValue;
}

// 3. Tagged template literals
export function html(strings, ...values) {
    const escaped = values.map(val => 
        String(val)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
    );
    
    return strings.reduce((result, str, i) => 
        result + str + (escaped[i] ?? ''), ''
    );
}

export function sql(strings, ...values) {
    const sanitized = values.map(val => {
        if (typeof val === 'string') {
            return `'${val.replace(/'/g, "''")}'`;
        }
        if (val === null) {
            return 'NULL';
        }
        return String(val);
    });
    
    return strings.reduce((result, str, i) => 
        result + str + (sanitized[i] ?? ''), ''
    ).trim();
}

export function css(strings, ...values) {
    return strings.reduce((result, str, i) => 
        result + str + (values[i] ?? ''), ''
    );
}

// Using tagged templates
export function renderUserCard(user) {
    const cardHtml = html`
        <div class="user-card">
            <h2>${user.name}</h2>
            <p>${user.bio}</p>
            <span>${user.email}</span>
        </div>
    `;
    return cardHtml;
}

export function buildQuery(tableName, conditions) {
    const { id, status } = conditions;
    return sql`SELECT * FROM ${tableName} WHERE id = ${id} AND status = ${status}`;
}

// 4. Try/catch/finally with local variables
export function parseJSON(jsonString) {
    let result = null;
    let error = null;
    
    try {
        const parsed = JSON.parse(jsonString);
        const validated = validateStructure(parsed);
        result = validated;
    } catch (e) {
        const errorMessage = e.message;
        const errorStack = e.stack;
        error = { message: errorMessage, stack: errorStack };
    } finally {
        const timestamp = Date.now();
        console.log(`Parse attempt at ${timestamp}`);
    }
    
    return { result, error };
}

function validateStructure(obj) {
    return obj;
}

export async function fetchWithRetry(url, maxRetries = 3) {
    let lastError = null;
    let attempts = 0;
    
    while (attempts < maxRetries) {
        try {
            const response = await fetch(url);
            const data = await response.json();
            return { success: true, data };
        } catch (error) {
            const retryCount = attempts + 1;
            lastError = error;
            attempts = retryCount;
            
            const delay = Math.pow(2, attempts) * 100;
            await new Promise(resolve => setTimeout(resolve, delay));
        } finally {
            const attemptNumber = attempts;
            console.log(`Attempt ${attemptNumber} completed`);
        }
    }
    
    return { success: false, error: lastError };
}

export function processFile(fileContent) {
    const resources = [];
    
    try {
        const lines = fileContent.split('\n');
        const header = lines[0];
        const body = lines.slice(1);
        
        for (const line of body) {
            const resource = parseLine(line);
            resources.push(resource);
        }
        
        return { header, resources };
    } catch (parseError) {
        const errorLine = parseError.lineNumber;
        const errorMessage = parseError.message;
        throw new Error(`Failed at line ${errorLine}: ${errorMessage}`);
    } finally {
        const resourceCount = resources.length;
        console.log(`Processed ${resourceCount} resources`);
    }
}

function parseLine(line) {
    return { raw: line };
}

// 5. Class expressions
export const Point = class {
    constructor(x, y) {
        this.x = x;
        this.y = y;
    }
    
    distanceTo(other) {
        const dx = this.x - other.x;
        const dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    toString() {
        return `(${this.x}, ${this.y})`;
    }
};

// Named class expression
export const Rectangle = class RectangleClass {
    constructor(width, height) {
        this.width = width;
        this.height = height;
    }
    
    get area() {
        return this.width * this.height;
    }
    
    get perimeter() {
        return 2 * (this.width + this.height);
    }
    
    scale(factor) {
        return new RectangleClass(this.width * factor, this.height * factor);
    }
};

// Class expression with inheritance
const Shape = class {
    constructor(name) {
        this.name = name;
    }
    
    describe() {
        return `This is a ${this.name}`;
    }
};

export const Circle = class extends Shape {
    constructor(radius) {
        super('circle');
        this.radius = radius;
    }
    
    get area() {
        return Math.PI * this.radius * this.radius;
    }
    
    get circumference() {
        return 2 * Math.PI * this.radius;
    }
};

// 6. Object literal patterns: methods, getters/setters, computed properties
export const mathUtils = {
    PI: Math.PI,
    E: Math.E,
    
    // Method shorthand
    add(a, b) {
        return a + b;
    },
    
    subtract(a, b) {
        return a - b;
    },
    
    multiply(a, b) {
        return a * b;
    },
    
    divide(a, b) {
        return b !== 0 ? a / b : NaN;
    },
    
    // Getter
    get tau() {
        return this.PI * 2;
    },
    
    // Methods using other methods
    power(base, exp) {
        return Math.pow(base, exp);
    },
    
    sqrt(value) {
        return Math.sqrt(value);
    }
};

// Object with getters and setters
export function createPerson(initialName, initialAge) {
    let _name = initialName;
    let _age = initialAge;
    
    return {
        get name() {
            return _name;
        },
        
        set name(value) {
            if (typeof value === 'string' && value.length > 0) {
                _name = value;
            }
        },
        
        get age() {
            return _age;
        },
        
        set age(value) {
            if (typeof value === 'number' && value >= 0) {
                _age = value;
            }
        },
        
        get isAdult() {
            return _age >= 18;
        },
        
        greet() {
            return `Hello, I'm ${_name}`;
        },
        
        haveBirthday() {
            _age++;
            return _age;
        }
    };
}

// Computed property names
export function createDynamicObject(keyPrefix, values) {
    const result = {};
    
    values.forEach((value, index) => {
        const key = `${keyPrefix}_${index}`;
        result[key] = value;
    });
    
    return result;
}

export function createAccessors(propertyName) {
    const privateKey = `_${propertyName}`;
    
    return {
        [privateKey]: null,
        
        [`get${propertyName.charAt(0).toUpperCase() + propertyName.slice(1)}`]() {
            return this[privateKey];
        },
        
        [`set${propertyName.charAt(0).toUpperCase() + propertyName.slice(1)}`](value) {
            this[privateKey] = value;
        }
    };
}

// 7. Logical assignment operators (&&=, ||=, ??=)
export function updateConfig(config) {
    config.debug ??= false;
    config.timeout ??= 5000;
    config.retries ??= 3;
    
    config.enabled &&= config.isValid;
    config.fallback ||= 'default';
    
    return config;
}

export function initializeDefaults(options) {
    const result = { ...options };
    
    result.name ??= 'unnamed';
    result.count ??= 0;
    result.items ??= [];
    result.metadata ??= {};
    
    return result;
}
