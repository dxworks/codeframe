// Dynamic imports and module patterns

// Lazy loading with dynamic import
export async function loadUserModule(userId) {
    const userModule = await import('./users/UserManager.js');
    return new userModule.UserManager(userId);
}

// Conditional module loading
export async function loadAnalytics(environment) {
    if (environment === 'production') {
        const analytics = await import('./analytics/prod.js');
        return analytics.default;
    } else {
        const analytics = await import('./analytics/dev.js');
        return analytics.default;
    }
}

// Dynamic import with error handling
export async function safeImport(modulePath) {
    try {
        const module = await import(modulePath);
        return { success: true, module };
    } catch (error) {
        console.error(`Failed to load ${modulePath}:`, error);
        return { success: false, error: error.message };
    }
}

// Module loader class
export class ModuleLoader {
    constructor() {
        this.cache = new Map();
    }

    async load(moduleName) {
        if (this.cache.has(moduleName)) {
            return this.cache.get(moduleName);
        }

        const module = await import(`./${moduleName}.js`);
        this.cache.set(moduleName, module);
        return module;
    }

    async loadMultiple(moduleNames) {
        const promises = moduleNames.map(name => this.load(name));
        return Promise.all(promises);
    }

    clearCache() {
        this.cache.clear();
    }

    has(moduleName) {
        return this.cache.has(moduleName);
    }
}

// Plugin system using dynamic imports
export class PluginManager {
    constructor() {
        this.plugins = new Map();
    }

    async register(pluginName, pluginPath) {
        try {
            const pluginModule = await import(pluginPath);
            const plugin = new pluginModule.default();
            
            if (typeof plugin.init === 'function') {
                await plugin.init();
            }
            
            this.plugins.set(pluginName, plugin);
            return true;
        } catch (error) {
            console.error(`Failed to register plugin ${pluginName}:`, error);
            return false;
        }
    }

    async execute(pluginName, method, ...args) {
        const plugin = this.plugins.get(pluginName);
        if (!plugin) {
            throw new Error(`Plugin ${pluginName} not found`);
        }
        
        if (typeof plugin[method] !== 'function') {
            throw new Error(`Method ${method} not found in plugin ${pluginName}`);
        }
        
        return plugin[method](...args);
    }

    getPlugin(pluginName) {
        return this.plugins.get(pluginName);
    }

    listPlugins() {
        return Array.from(this.plugins.keys());
    }
}

// Feature flag with lazy loading
export class FeatureFlags {
    constructor() {
        this.features = new Map();
    }

    async enableFeature(featureName) {
        if (this.features.has(featureName)) {
            return this.features.get(featureName);
        }

        const featureModule = await import(`./features/${featureName}.js`);
        const feature = featureModule.default;
        this.features.set(featureName, feature);
        
        if (typeof feature.onEnable === 'function') {
            await feature.onEnable();
        }
        
        return feature;
    }

    isEnabled(featureName) {
        return this.features.has(featureName);
    }

    async disableFeature(featureName) {
        const feature = this.features.get(featureName);
        if (feature && typeof feature.onDisable === 'function') {
            await feature.onDisable();
        }
        this.features.delete(featureName);
    }
}

// Route-based code splitting
export class Router {
    constructor() {
        this.routes = new Map();
    }

    register(path, importFn) {
        this.routes.set(path, importFn);
    }

    async navigate(path) {
        const importFn = this.routes.get(path);
        if (!importFn) {
            throw new Error(`Route ${path} not found`);
        }

        const module = await importFn();
        return module.default;
    }
}

// Usage example
export function setupRouter() {
    const router = new Router();
    
    router.register('/home', () => import('./pages/Home.js'));
    router.register('/about', () => import('./pages/About.js'));
    router.register('/contact', () => import('./pages/Contact.js'));
    
    return router;
}

// Preloading strategy
export class PreloadManager {
    constructor() {
        this.preloaded = new Set();
    }

    async preload(modulePaths) {
        const promises = modulePaths
            .filter(path => !this.preloaded.has(path))
            .map(async path => {
                try {
                    await import(path);
                    this.preloaded.add(path);
                } catch (error) {
                    console.warn(`Failed to preload ${path}:`, error);
                }
            });
        
        await Promise.all(promises);
    }

    isPreloaded(modulePath) {
        return this.preloaded.has(modulePath);
    }
}
