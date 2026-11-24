# Findings: Unified Testing Tool 0.1.0-alpha5 on Bakery

Issues, surprises and suggestions found while migrating the Bakery TestBench tests with 0.1.0-alpha5.
Severity: **bug** (wrong behaviour), **recipe** (migration recipe missed / produced wrong code), **docs**, **api** (friction / missing feature), **info**.
The step where it was found refers to [MIGRATION_LOG.md](MIGRATION_LOG.md).

## F1: `FindTestClassesToMigrate` reports no page objects and no "migrates partly" (recipe)

- **Step:** 1 (dry run)
- **Expected** (docs, *Which classes it converts*): a TestBench class lists the page objects and custom elements it uses
  (`LoginViewElement: stays TestBench (Escape Hatch), a custom element extending LoginOverlayElement: write a Wrapper`),
  and a class using one that stays TestBench is `migrates partly`.
- **Actual:** all six classes `migrates`, `pageObjects` column empty. Bakery's `LoginViewElement extends LoginOverlayElement`
  is literally the example the docs use. The tests reach it via `AbstractIT.openLoginView()` (inherited helper), and via
  `HasApp`/`HasCrudView` default methods on the page objects.
- **Guess:** the analysis only looks at element types used directly in the class's own body, not through the base class or
  through the return types of page-object methods.
- **Impact:** the dry run overstates how much will migrate; a user can't plan the Wrapper work from it.

