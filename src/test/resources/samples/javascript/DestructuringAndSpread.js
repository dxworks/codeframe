// Destructuring, spread operators, and modern syntax patterns

// Object destructuring in parameters
export function createUser({ name, email, age = 18, role = 'user' }) {
    return {
        name,
        email,
        age,
        role,
        createdAt: new Date()
    };
}

// Array destructuring
export function getFirstAndLast(items) {
    const [first, ...rest] = items;
    const last = rest[rest.length - 1];
    return { first, last, rest };
}

// Nested destructuring
export function processApiResponse({ data: { user, settings }, meta: { timestamp } }) {
    return {
        userName: user.name,
        userEmail: user.email,
        theme: settings.theme,
        receivedAt: timestamp
    };
}

// Spread in objects
export function mergeUserData(baseUser, updates) {
    return {
        ...baseUser,
        ...updates,
        updatedAt: new Date()
    };
}

// Spread in arrays
export function combineArrays(arr1, arr2, arr3) {
    return [...arr1, ...arr2, ...arr3];
}

// Rest parameters
export function sum(...numbers) {
    return numbers.reduce((acc, num) => acc + num, 0);
}

export function logWithPrefix(prefix, ...messages) {
    messages.forEach(msg => console.log(`${prefix}: ${msg}`));
}

// Complex destructuring with renaming
export function extractUserInfo({ 
    name: userName, 
    email: userEmail, 
    profile: { bio, avatar: avatarUrl } = {},
    preferences: { theme = 'light', language = 'en' } = {}
}) {
    return {
        userName,
        userEmail,
        bio,
        avatarUrl,
        theme,
        language
    };
}

// Destructuring in loops
export function processUsers(users) {
    const results = [];
    for (const { id, name, email } of users) {
        results.push({ id, name, email });
    }
    return results;
}

// Object property shorthand and computed properties
export function createConfig(env, port, debug) {
    const timestamp = Date.now();
    return {
        env,
        port,
        debug,
        timestamp,
        [`${env}_url`]: `http://localhost:${port}`,
        [`${env}_ready`]: true
    };
}

// Destructuring with default values and rest
export function parseOptions({ 
    timeout = 5000, 
    retries = 3, 
    headers = {}, 
    ...otherOptions 
}) {
    return {
        timeout,
        retries,
        headers: {
            'Content-Type': 'application/json',
            ...headers
        },
        ...otherOptions
    };
}

// Array destructuring with skipping
export function getOddIndexed(items) {
    const [, second, , fourth, , sixth] = items;
    return [second, fourth, sixth].filter(Boolean);
}

// Swap variables using destructuring
export function swapValues(a, b) {
    [a, b] = [b, a];
    return { a, b };
}

// Destructuring function return values
export function getDimensions() {
    return { width: 1920, height: 1080, aspectRatio: 16/9 };
}

export function useDimensions() {
    const { width, height } = getDimensions();
    return width * height;
}
