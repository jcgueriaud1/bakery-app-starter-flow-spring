# Findings: Unified UI Tests 0.1.0-alpha4 on the Bakery app

Issues, surprises and suggestions found while migrating the Bakery's TestBench suite with
`org.vaadin.addons:unified-test-*:0.1.0-alpha4`. Process: [MIGRATION_LOG.md](MIGRATION_LOG.md).

Severity: **High** blocks the migration or gives a wrong result without warning; **Medium** costs real time or
needs a workaround; **Low** is friction, wording or docs.

| # | Severity | Area | Summary |
|---|---|---|---|

| F1 | Low | `FindTestClassesToMigrate` | The dry run reports every class as `migrates` but doesn't list the page objects/custom elements they depend on |

### F1: the dry run is silent about page objects

`FindTestClassesToMigrate` lists the 5 Bakery ITs and `AbstractIT` as `migrates`, with an empty reason. All 17 tests
go through 18 TestBench page objects and custom `TestBenchElement`s (`@Element("order-card")`, a
`LoginOverlayElement` subclass, a crud view element, ...), which the default recipe (`includePageObjects: false`,
and options are ignored anyway up to alpha4, #353) won't convert. So the forecast says "everything migrates" when
the outcome is mostly Escape Hatches.

**Suggestion:** add a row (or a column) for page objects and custom elements the test classes use, with the status
`stays TestBench (Escape Hatch)` / `converts with includePageObjects` and the reason, and a count of statements
that will get a Migration Marker if possible.

