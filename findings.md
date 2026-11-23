# Findings: Unified UI Tests 0.1.0-alpha4 on the Bakery app

Issues, surprises and suggestions found while migrating the Bakery's TestBench suite with
`org.vaadin.addons:unified-test-*:0.1.0-alpha4`. Process: [MIGRATION_LOG.md](MIGRATION_LOG.md).

Severity: **High** blocks the migration or gives a wrong result without warning; **Medium** costs real time or
needs a workaround; **Low** is friction, wording or docs.

| # | Severity | Area | Summary |
|---|---|---|---|
| F1 | Low | `FindTestClassesToMigrate` | The dry run reports every class as `migrates` but doesn't list the page objects/custom elements they depend on |
| F2 | Low | Docs | The guide says recipe options are ignored up to alpha4, but alpha4 applies them |
| F3 | High | Recipe (build) | Removing `vaadin-testbench-junit5` drops 7 element jars still used by unconverted page objects → no compile |
| F4 | High | Recipe (code) | The migrated tests don't compile: `page` name clash, leftover `getDriver()` override, abstract method not updated, a call not updated, a rename on an unconverted type |
| F5 | High | Recipe (code) | `TestBenchElement`-style page objects are not converted and calls to them get no marker or `@RequiresTestingTool`, so 8 markers hide 16 unconverted tests |
| F6 | Medium | Recipe (code) | A Spring Boot app whose ITs hit an externally started server becomes `@UnifiedUITest` with no marker; `APP_URL` navigation is not recognised |
| F7 | Low | Recipe (build) | The pom rewrite drops comments and puts the test dependencies among the runtime ones |

## Details

### F1: the dry run is silent about page objects

`FindTestClassesToMigrate` lists the 5 Bakery ITs and `AbstractIT` as `migrates`, with an empty reason. All 17 tests
go through 18 TestBench page objects and custom `TestBenchElement`s (`@Element("order-card")`, a
`LoginOverlayElement` subclass, a crud view element, ...), which the default recipe (`includePageObjects: false`,
and options are ignored anyway up to alpha4, #353) won't convert. So the forecast says "everything migrates" when
the outcome is mostly Escape Hatches.

**Suggestion:** add a row (or a column) for page objects and custom elements the test classes use, with the status
`stays TestBench (Escape Hatch)` / `converts with includePageObjects` and the reason, and a count of statements
that will get a Migration Marker if possible.

### F2: the guide says alpha4 ignores recipe options, but it applies them

The guide (Migrating from TestBench → Run it) says: "Up to 0.1.0-alpha4 the recipe ignores the options a
`rewrite.yml` sets, without a warning ... (#353)". With the `rewrite.yml` in MIGRATION_LOG step 2, the 0.1.0-alpha4
recipe **did** apply `addPlaywright` (it added `unified-test-playwright`) and `addAgentSkill` (it wrote
`resolve-migration-markers/SKILL.md`), and the log prints the options. Whether `includePageObjects` was applied
can't be told, since no page object changed (F5).

**Suggestion:** fix the note (either #353 was fixed in alpha4 or the note's version is off by one).

### F3: the build migration removes TestBench element jars that unconverted code still needs

`MigrateTestBenchBuild` removes `vaadin-testbench-junit5` because "the Selenium adapter brings TestBench". It
brings `vaadin-testbench-core` and 15 component element jars, but not `html`, `app-layout`, `board`,
`confirm-dialog`, `crud`, `form-layout` or `login` (Bakery uses all 7). Every page object the recipe leaves as
TestBench (which is all of them here, F5), and every Escape Hatch that uses those elements, then fails to compile:
`package com.vaadin.flow.component.html.testbench does not exist`, etc.

**Repro:** a TestBench project using `DivElement`/`SpanElement`, `CrudElement`, `BoardElement`, `LoginOverlayElement`
→ run `MigrateTestBenchToUnified` → `mvn test-compile`.

**Suggestion:** keep `vaadin-testbench-junit5` (or add `vaadin-testbench` at test scope) whenever an Escape Hatch or
an unconverted TestBench type remains after the code migration, and remove it only when none is left (the same rule
the recipe already uses for `junit-vintage-engine`). Or have the Selenium adapter depend on all element jars.

### F4: the recipe's output doesn't compile on a common base-class layout

Bakery's layout: `AbstractIT<E extends TestBenchElement> extends BrowserTestBase` with `@BeforeEach setup()`,
an overridden `getDriver()`, `protected abstract E openView()`, and helpers `openLoginView(WebDriver, String)`.
After the recipe:

| # | What | Error |
|---|---|---|
| a | 3 tests in `UsersViewIT` had a local `UsersViewElement page = openView();`. The recipe adds a `UnifiedPage page` parameter and rewrites the call to `openView(page)` | `variable page is already defined` |
| b | `@Override public TestBenchDriverProxy getDriver() { return (TestBenchDriverProxy) super.getDriver(); }` kept after `extends BrowserTestBase` is removed; `getDriver()` calls in tests and the base class left as they were (no Escape Hatch, no marker) | `method does not override`, `cannot find symbol` |
| c | Overrides of `openView()` became `openView(UnifiedPage page)`, the abstract declaration in the base class didn't | `is not abstract and does not override abstract method openView()` |
| d | A test calling another test method (`loginToNotDefaultUrl()` from `openLoginAfterLoggedIn`): the callee now needs `page`, the call isn't updated | `cannot be applied to given types` |
| e | `grid.getCell(text).getRow()` on a TestBench `GridElement` that stays TestBench is renamed to `getIndex()`, which `GridTHTDElement` doesn't have. Elsewhere in the same file (inside an Escape Hatch) `getRow()` is kept | `cannot find symbol` |

**Suggestion:** (a) pick a free name for the parameter (`unifiedPage`) or rename the clashing local; (b) when a
class loses `BrowserTestBase`, rewrite `getDriver()` to `testBench.getDriver()` with a marker, and drop an override
of a method the class no longer inherits; (c) change the abstract declaration with its overrides; (d) pass `page`
to every call of a method that gained the parameter; (e) apply TestBench→Unified renames only on receivers whose
type the recipe converted. And a recipe integration test with an `abstract` base class like this one.

### F5: page objects are neither converted nor marked; tests that only use them look fully migrated

With `includePageObjects: true`, none of the 18 page objects changed. They're TestBench `TestBenchElement`
subclasses (`LoginViewElement extends LoginOverlayElement`, `@Element("order-card") OrderCardElement extends
DivElement`, `StorefrontViewElement`, ...) found by `$(X.class)` and chained (`login(..)` returns
`StorefrontViewElement`, `getMenu().navigateToUsers()` returns `UsersViewElement`). That's TestBench's
recommended page-object style, but the guide's page-object conversion only describes an object constructed with
the test (`new LoginPage(this)`).

The worse part: the tests that use them got no marker and no `@RequiresTestingTool`. `LoginIT.loginWorks`,
`DashboardViewIT.*`, `UsersViewIT.updatePassword`, ... have a `UnifiedPage` parameter and no
`TestBenchEscapeHatch`, yet every statement is TestBench. The marker count (8) suggests a small amount of
leftover work. In fact all 16 enabled tests are still TestBench and would fail, not be skipped, on Browserless and
Playwright.

**Suggestion:** treat a call on a value whose type is a project `TestBenchElement` subclass (or a method returning
one) like a `$(..)` on the test: put it behind the Escape Hatch with a marker such as `TestBench page object:
LoginViewElement (custom element extending LoginOverlayElement): write a Wrapper or convert the page object`, and add
`@RequiresTestingTool(SELENIUM)`. When `includePageObjects` can't convert a page object, say why in a marker on the
class.

### F6: a Spring Boot app tested against an externally started server becomes `@UnifiedUITest` without a word

The Bakery ITs have no `@SpringBootTest`: Maven starts the app (`spring-boot:start` in the `it` profile) and the tests
open `APP_URL = "http://localhost:8080/"` (or the site-local IP on a grid). This is the layout of Vaadin's starters.
The recipe made `AbstractIT` `@UnifiedUITest` (no Spring), kept `APP_URL` and `driver.get(url)`, and left no marker.
On Browserless, `@UnifiedUITest` has no Spring context, so the Spring views can't be instantiated. The tests need
`@UnifiedSpringBootTest`, and the `it` profile's server start becomes redundant (and would clash on port 8080).

**Suggestion:** when the project has a `@SpringBootApplication` and `browserless-test-spring` is added, use
`@UnifiedSpringBootTest`, or leave a marker (`no @SpringBootTest: the tests ran against a server started outside
the test; use @UnifiedSpringBootTest`). Recognise `driver.get(<field or param> + "path")` as navigation, and mention
the build's `spring-boot:start/stop` executions and `failsafe` `*IT` naming in the migration notes.

### F7: the pom rewrite drops comments and interleaves test dependencies with runtime ones

`MigrateTestBenchBuild` removed the `<!-- End Spring -->` and `<!-- Testing -->` comments around the removed
`vaadin-testbench-junit5` and inserted the new test dependencies in alphabetical position among the application's
dependencies (`browserless-test-spring` before `h2`, the adapters after `spring-boot-starter-data-jpa`).

**Suggestion:** insert the new test-scope dependencies where the removed TestBench dependency was, keeping the
surrounding comments.
