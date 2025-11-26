// TypeScript class features: static, getters/setters, constructor param properties, default params

// Constructor parameter properties (shorthand field declaration)
export class Point {
    constructor(
        public x: number,
        public y: number,
        private label?: string
    ) {}

    distanceTo(other: Point): number {
        const dx = this.x - other.x;
        const dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}

// Static members and getters/setters
export class Counter {
    private static instanceCount: number = 0;
    public static readonly MAX_VALUE: number = 1000;

    private _value: number;

    constructor(initial: number = 0) {
        this._value = initial;
        Counter.instanceCount++;
    }

    // Getter
    get value(): number {
        return this._value;
    }

    // Setter with validation
    set value(newValue: number) {
        if (newValue > Counter.MAX_VALUE) {
            throw new Error(`Value cannot exceed ${Counter.MAX_VALUE}`);
        }
        this._value = newValue;
    }

    // Static method
    static getInstanceCount(): number {
        return Counter.instanceCount;
    }

    static resetInstances(): void {
        Counter.instanceCount = 0;
    }

    increment(amount: number = 1): void {
        this.value = this._value + amount;
    }
}

// Default parameter values
export class Logger {
    constructor(
        private prefix: string = 'LOG',
        private enabled: boolean = true
    ) {}

    log(message: string, level: string = 'INFO'): void {
        if (this.enabled) {
            console.log(`[${this.prefix}] [${level}] ${message}`);
        }
    }

    // Multiple default params
    format(template: string, separator: string = ': ', suffix: string = ''): string {
        return `${this.prefix}${separator}${template}${suffix}`;
    }
}

// Readonly modifier
export class Configuration {
    readonly version: string;
    readonly settings: ReadonlyArray<string>;

    constructor(version: string, settings: string[]) {
        this.version = version;
        this.settings = settings;
    }

    getVersion(): string {
        return this.version;
    }
}
