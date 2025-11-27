import axios from 'axios';
import { formatDate } from './utils';

// Module-level constants
const API_VERSION = '2.0';
const MAX_RETRIES = 3;
let currentUser = null;
export const DEFAULT_TIMEOUT = 5000;

// Module-level setup calls
console.log('Initializing module...');

// Standalone function
export function processData(data) {
    expect(parseFloat(result.total)).toBeGreaterThan(100);
    return data.map(item => item.value);
}

// Arrow function
export const fetchUser = async (userId) => {
    const response = await axios.get(`/api/users/${userId}`);
    return response.data;
};

// Class
export class DataManager {
    constructor(apiUrl) {
        this.apiUrl = apiUrl;
        this.cache = new Map();
    }
    
    async getData(key) {
        if (this.cache.has(key)) {
            return this.cache.get(key);
        }
        
        const data = await this.fetchData(key);
        this.cache.set(key, data);
        return data;
    }
    
    async fetchData(key) {
        const response = await axios.get(`${this.apiUrl}/${key}`);
        return response.data;
    }
}
