// Generators, iterators, and async iteration

// Basic generator
export function* numberGenerator(start, end) {
    for (let i = start; i <= end; i++) {
        yield i;
    }
}

// Generator with yield*
export function* flattenArray(arr) {
    for (const item of arr) {
        if (Array.isArray(item)) {
            yield* flattenArray(item);
        } else {
            yield item;
        }
    }
}

// Generator for infinite sequence
export function* fibonacci() {
    let [prev, curr] = [0, 1];
    while (true) {
        yield curr;
        [prev, curr] = [curr, prev + curr];
    }
}

// Generator with two-way communication
export function* dataProcessor() {
    let value;
    while (true) {
        value = yield value ? value * 2 : 0;
    }
}

// Class with generator method
export class Range {
    constructor(start, end, step = 1) {
        this.start = start;
        this.end = end;
        this.step = step;
    }

    *[Symbol.iterator]() {
        for (let i = this.start; i <= this.end; i += this.step) {
            yield i;
        }
    }

    *reverse() {
        for (let i = this.end; i >= this.start; i -= this.step) {
            yield i;
        }
    }
}

// Async generator
export async function* fetchPages(baseUrl, maxPages) {
    for (let page = 1; page <= maxPages; page++) {
        const response = await fetch(`${baseUrl}?page=${page}`);
        const data = await response.json();
        yield data;
    }
}

// Async generator with error handling
export async function* streamData(source) {
    let hasMore = true;
    let offset = 0;
    
    while (hasMore) {
        try {
            const batch = await source.fetch(offset);
            if (batch.length === 0) {
                hasMore = false;
            } else {
                yield* batch;
                offset += batch.length;
            }
        } catch (error) {
            console.error('Stream error:', error);
            throw error;
        }
    }
}

// Custom iterable class
export class PaginatedData {
    constructor(items, pageSize = 10) {
        this.items = items;
        this.pageSize = pageSize;
    }

    *pages() {
        for (let i = 0; i < this.items.length; i += this.pageSize) {
            yield this.items.slice(i, i + this.pageSize);
        }
    }

    [Symbol.iterator]() {
        return this.items[Symbol.iterator]();
    }
}

// Generator for tree traversal
export class TreeNode {
    constructor(value, children = []) {
        this.value = value;
        this.children = children;
    }

    *preOrder() {
        yield this.value;
        for (const child of this.children) {
            yield* child.preOrder();
        }
    }

    *postOrder() {
        for (const child of this.children) {
            yield* child.postOrder();
        }
        yield this.value;
    }

    *breadthFirst() {
        const queue = [this];
        while (queue.length > 0) {
            const node = queue.shift();
            yield node.value;
            queue.push(...node.children);
        }
    }
}

// Using generators for lazy evaluation
export function* map(iterable, fn) {
    for (const item of iterable) {
        yield fn(item);
    }
}

export function* filter(iterable, predicate) {
    for (const item of iterable) {
        if (predicate(item)) {
            yield item;
        }
    }
}

export function* take(iterable, n) {
    let count = 0;
    for (const item of iterable) {
        if (count++ >= n) break;
        yield item;
    }
}

// Async iteration example
export async function processAsyncStream(asyncIterable) {
    const results = [];
    for await (const item of asyncIterable) {
        results.push(item);
    }
    return results;
}
