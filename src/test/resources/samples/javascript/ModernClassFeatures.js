// Modern JavaScript class features: static, private fields, getters/setters, inheritance

export class BaseEntity {
    #id;
    #createdAt;

    constructor(id) {
        this.#id = id;
        this.#createdAt = new Date();
    }

    get id() {
        return this.#id;
    }

    get createdAt() {
        return this.#createdAt;
    }

    toJSON() {
        return {
            id: this.#id,
            createdAt: this.#createdAt.toISOString()
        };
    }
}

export class User extends BaseEntity {
    static #instanceCount = 0;
    static MAX_NAME_LENGTH = 100;

    #name;
    #email;
    #role;

    constructor(id, name, email, role = 'user') {
        super(id);
        this.#name = name;
        this.#email = email;
        this.#role = role;
        User.#instanceCount++;
    }

    get name() {
        return this.#name;
    }

    set name(value) {
        if (value.length > User.MAX_NAME_LENGTH) {
            throw new Error(`Name too long: ${value.length}`);
        }
        this.#name = value;
    }

    get email() {
        return this.#email;
    }

    set email(value) {
        if (!value.includes('@')) {
            throw new Error('Invalid email');
        }
        this.#email = value;
    }

    get role() {
        return this.#role;
    }

    static getInstanceCount() {
        return User.#instanceCount;
    }

    static resetCount() {
        User.#instanceCount = 0;
    }

    #validatePermission(action) {
        const permissions = {
            admin: ['read', 'write', 'delete'],
            user: ['read']
        };
        return permissions[this.#role]?.includes(action) ?? false;
    }

    canPerform(action) {
        return this.#validatePermission(action);
    }

    toJSON() {
        return {
            ...super.toJSON(),
            name: this.#name,
            email: this.#email,
            role: this.#role
        };
    }
}

export class AdminUser extends User {
    #permissions;

    constructor(id, name, email) {
        super(id, name, email, 'admin');
        this.#permissions = new Set(['read', 'write', 'delete', 'manage']);
    }

    get permissions() {
        return Array.from(this.#permissions);
    }

    addPermission(permission) {
        this.#permissions.add(permission);
    }

    removePermission(permission) {
        this.#permissions.delete(permission);
    }

    hasPermission(permission) {
        return this.#permissions.has(permission);
    }
}
