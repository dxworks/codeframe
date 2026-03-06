# Link Coverage Sample

Intro paragraph with a relative [README](README.md) and an absolute [Docs](https://example.com/docs).
Also a protocol-relative [CDN](//cdn.example.com/lib.js) and a mail link [Support](mailto:support@example.com).

## Heading With [Spec](docs/specs/MARKDOWN_SPEC.md) Link

## Inline Links In Paragraph

This paragraph has a relative link to [Config](./config/settings.yml) and another to [Parent](../PARENT.md).
Absolute link to [Site](http://example.org) should be skipped.

## List Links

- Relative list item [Guide](guides/getting-started.md)
- Absolute list item [External](https://external.example.org)
- Mixed item with [Local](docs/local.md) and [Remote](https://remote.example.org)

## Block Quote Links

> See [Changelog](CHANGELOG.md) for details.
> Avoid [Remote Docs](https://docs.example.org) here.
>
> - Quoted list item [Quoted Guide](docs/quoted-guide.md)
> - Quoted list item without link
>
> ```text
> quoted code
> ```

## Reference Links

Reference style [Spec][spec] and [Remote Spec][remote_spec].

[spec]: docs/specs/MARKDOWN_SPEC.md
[remote_spec]: https://example.com/specs

## Code Block (ignored)

```markdown
Here is a [Code Link](SHOULD_NOT_BE_CAPTURED.md)
```

## Image (ignored destination)

![Alt text](images/logo.png)
