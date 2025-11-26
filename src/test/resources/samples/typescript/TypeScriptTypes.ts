// TypeScript-specific type constructs: enums, type aliases, interface extension

// String enum
export enum Status {
    Active = 'ACTIVE',
    Inactive = 'INACTIVE',
    Pending = 'PENDING'
}

// Numeric enum (default)
export enum Priority {
    Low,
    Medium,
    High,
    Critical
}

// Const enum (inlined at compile time)
export const enum Direction {
    Up = 'UP',
    Down = 'DOWN',
    Left = 'LEFT',
    Right = 'RIGHT'
}

// Type alias - primitive
export type ID = string | number;

// Type alias - object shape
export type Coordinates = {
    x: number;
    y: number;
    z?: number;
};

// Type alias - union
export type Result<T> = { success: true; data: T } | { success: false; error: string };

// Base interface
export interface Named {
    name: string;
}

// Interface extension (single)
export interface Person extends Named {
    age: number;
}

// Interface extension (multiple)
export interface Employee extends Named, Person {
    employeeId: string;
    department: string;
}

// Interface with index signature
export interface Dictionary<T> {
    [key: string]: T;
}

// Function using these types
export function processItem(id: ID, status: Status): Result<string> {
    if (status === Status.Active) {
        return { success: true, data: `Processed ${id}` };
    }
    return { success: false, error: 'Item not active' };
}

// Class using enum and type alias
export class Task {
    private id: ID;
    private status: Status;
    private priority: Priority;

    constructor(id: ID) {
        this.id = id;
        this.status = Status.Pending;
        this.priority = Priority.Medium;
    }

    setStatus(status: Status): void {
        this.status = status;
    }

    setPriority(priority: Priority): void {
        this.priority = priority;
    }
}
