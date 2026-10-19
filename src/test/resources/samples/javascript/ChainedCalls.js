new Promise((resolve) => resolve())
    .catch(handle);

resource.fetch()
    .then(parse)
    .catch(handle);

function run(resource) {
    resource.fetch()
        .then(parse)
        .catch(handle);

    return new Promise((resolve) => resolve()).finally(cleanup);
}
