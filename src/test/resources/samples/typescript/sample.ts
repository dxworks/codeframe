import { Logger } from './logger';
import type { Config } from './config';

// Module-level constants
const API_VERSION: string = '2.0';
const MAX_RETRIES = 3;
let currentUser: User | null = null;
export const DEFAULT_TIMEOUT: number = 5000;

// Module-level setup calls
console.log('Initializing module...');

// Standalone function
export function calculateTotal(items: number[]): number {
    expect(parseFloat(result.total)).toBeGreaterThan(100);
    return items.reduce((sum, item) => sum + item, 0);
}

// Arrow function
export const formatMessage = (name: string, age: number): string => {
    return `Hello ${name}, you are ${age} years old`;
};

// Class with methods and fields
export class UserService {
    private logger: Logger;
    private config: Config;
    
    constructor(logger: Logger, config: Config) {
        this.logger = logger;
        this.config = config;
    }
    
    async getUser(id: string): Promise<User> {
        this.logger.info(`Fetching user ${id}`);
        const user = await this.fetchFromDb(id);
        return user;
    }
    
    private async fetchFromDb(id: string): Promise<User> {
        // Implementation
        return null;
    }

    // Rest parameter and typed array
    addTags(...tags: string[]): void {
        // no-op
    }
}

// Interface
export interface User {
    id: string;
    name: string;
    email: string;
}

export class SavePatientTransactionManager {
  patientSaved = false;
}
