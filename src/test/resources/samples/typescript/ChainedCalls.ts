type Resource = {
    fetch(): Promise<string>;
};

declare const resource: Resource;
declare function parse(value: string): string;
declare function handle(error: unknown): void;
declare function cleanup(): void;

new Promise<void>((resolve) => resolve())
    .catch(handle);

resource.fetch()
    .then(parse)
    .catch(handle);

function run(resource: Resource): Promise<void> {
    resource.fetch()
        .then(parse)
        .catch(handle);

    return new Promise<void>((resolve) => resolve()).finally(cleanup);
}
