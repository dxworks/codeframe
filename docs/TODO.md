# TODO

Shared backlog for documentation, analyzer rules, and follow-up design decisions.

## Active

### Qualified method-call model extension (Option 1)

- [ ] Define and approve dedicated qualifier metadata in call model
  - [ ] propose `qualifierName` (text before the leaf method)
  - [ ] propose `qualifierKind` (`namespace|type|module|package|unknown`)
- [ ] Update canonical extraction contract to use qualifier metadata (without overloading `objectName`/`objectType`)
- [ ] Update language specs with examples for qualified calls:
  - [ ] C++ `util::declared_ns(2)`
  - [ ] C# `System.Console.WriteLine(a)`
  - [ ] Java `Math.max(a, b)`
  - [ ] PHP `Class::method()`
  - [ ] Rust `module::function()`
- [ ] Implement analyzer/model changes only after spec updates are approved
- [ ] Refresh affected approval outputs after behavior changes

## Future candidates

- [ ] Periodically audit docs links so extraction-rule references point to `docs/EXTRACTION_CONTRACT.md` and workflow/testing references point to `docs/CONTRIBUTING.md`.

## Notes

- If only docs change, no approval-test refresh is required.
- If analyzer behavior changes, update samples + approvals accordingly.
