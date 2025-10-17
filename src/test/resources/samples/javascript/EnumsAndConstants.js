// Enum-like patterns in JavaScript

// 1. Object freeze pattern (most common)
export const UserRole = Object.freeze({
    ADMIN: 'admin',
    MODERATOR: 'moderator',
    USER: 'user',
    GUEST: 'guest'
});

export const HttpStatus = Object.freeze({
    OK: 200,
    CREATED: 201,
    BAD_REQUEST: 400,
    UNAUTHORIZED: 401,
    FORBIDDEN: 403,
    NOT_FOUND: 404,
    INTERNAL_ERROR: 500
});

// 2. Class-based enum with methods
export class OrderStatus {
    static PENDING = new OrderStatus('PENDING', 'Pending', 0);
    static PROCESSING = new OrderStatus('PROCESSING', 'Processing', 1);
    static SHIPPED = new OrderStatus('SHIPPED', 'Shipped', 2);
    static DELIVERED = new OrderStatus('DELIVERED', 'Delivered', 3);
    static CANCELLED = new OrderStatus('CANCELLED', 'Cancelled', -1);

    constructor(key, label, order) {
        this.key = key;
        this.label = label;
        this.order = order;
    }

    static values() {
        return [
            OrderStatus.PENDING,
            OrderStatus.PROCESSING,
            OrderStatus.SHIPPED,
            OrderStatus.DELIVERED,
            OrderStatus.CANCELLED
        ];
    }

    static fromKey(key) {
        return OrderStatus.values().find(status => status.key === key);
    }

    isTerminal() {
        return this === OrderStatus.DELIVERED || this === OrderStatus.CANCELLED;
    }

    canTransitionTo(nextStatus) {
        if (this.isTerminal()) {
            return false;
        }
        return nextStatus.order > this.order || nextStatus === OrderStatus.CANCELLED;
    }

    toString() {
        return this.label;
    }
}

// Usage example class
export class Order {
    constructor(id, items) {
        this.id = id;
        this.items = items;
        this.status = OrderStatus.PENDING;
        this.statusHistory = [OrderStatus.PENDING];
    }

    updateStatus(newStatus) {
        if (this.status.canTransitionTo(newStatus)) {
            this.status = newStatus;
            this.statusHistory.push(newStatus);
            return true;
        }
        return false;
    }

    getStatusLabel() {
        return this.status.toString();
    }

    isComplete() {
        return this.status.isTerminal();
    }
}

// Helper functions using enums
export function getRolePermissions(role) {
    const permissions = {
        [UserRole.ADMIN]: ['read', 'write', 'delete', 'manage'],
        [UserRole.MODERATOR]: ['read', 'write', 'moderate'],
        [UserRole.USER]: ['read', 'write'],
        [UserRole.GUEST]: ['read']
    };
    return permissions[role] || [];
}

export function getHttpStatusMessage(status) {
    const messages = new Map([
        [HttpStatus.OK, 'Success'],
        [HttpStatus.CREATED, 'Resource created'],
        [HttpStatus.BAD_REQUEST, 'Invalid request'],
        [HttpStatus.UNAUTHORIZED, 'Authentication required'],
        [HttpStatus.FORBIDDEN, 'Access denied'],
        [HttpStatus.NOT_FOUND, 'Resource not found'],
        [HttpStatus.INTERNAL_ERROR, 'Server error']
    ]);
    return messages.get(status) || 'Unknown status';
}
