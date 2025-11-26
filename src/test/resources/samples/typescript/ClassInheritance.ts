// TypeScript class inheritance: extends, implements, abstract classes

// Base interface
export interface Serializable {
    serialize(): string;
}

export interface Identifiable {
    getId(): string;
}

// Abstract base class
export abstract class Entity implements Identifiable {
    protected id: string;

    constructor(id: string) {
        this.id = id;
    }

    getId(): string {
        return this.id;
    }

    // Abstract method - must be implemented by subclasses
    abstract validate(): boolean;
}

// Concrete class extending abstract class and implementing interface
export class User extends Entity implements Serializable {
    private name: string;
    private email: string;

    constructor(id: string, name: string, email: string) {
        super(id);
        this.name = name;
        this.email = email;
    }

    validate(): boolean {
        return this.name.length > 0 && this.email.includes('@');
    }

    serialize(): string {
        return JSON.stringify({ id: this.id, name: this.name, email: this.email });
    }

    getName(): string {
        return this.name;
    }
}

// Multiple interface implementation
export class AdminUser extends User implements Serializable, Identifiable {
    private permissions: string[];

    constructor(id: string, name: string, email: string, permissions: string[]) {
        super(id, name, email);
        this.permissions = permissions;
    }

    hasPermission(permission: string): boolean {
        return this.permissions.includes(permission);
    }

    // Override parent method
    validate(): boolean {
        return super.validate() && this.permissions.length > 0;
    }
}
