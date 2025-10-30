import { Logger } from './logger';
import type { Config } from './config';

// Standalone function
export function calculateTotal(items: number[]): number {
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
