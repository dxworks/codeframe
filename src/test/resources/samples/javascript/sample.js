import axios from 'axios';
import { formatDate } from './utils';

// Standalone function
export function processData(data) {
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
