# Findings: Unified UI Tests 0.1.0-alpha4 on the Bakery app

Issues, surprises and suggestions found while migrating the Bakery's TestBench suite with
`org.vaadin.addons:unified-test-*:0.1.0-alpha4`. Process: [MIGRATION_LOG.md](MIGRATION_LOG.md).

Migration PR (draft): https://github.com/jcgueriaud1/bakery-app-starter-flow-spring/pull/1

Filed upstream as [parttio/unified-testing-tool#524](https://github.com/parttio/unified-testing-tool/issues/524) and its sub-issues: F3, F7 → #525; F4 → #526; F1, F5 → #527; F6, F16 → #528; F14, F18 → #529; F10 → #530; F11, F12, part of F15 → #531; F13, F17 → #532; F8 → #533 and #512; F9 → #347 (comment added). F2 and `withTimeout` (F15) are listed in #524.

Severity: **High** blocks the migration or gives a wrong result without warning; **Medium** costs real time or
needs a workaround; **Low** is friction, wording or docs.

**18 findings: 5 High, 5 Medium, 8 Low.** The two that limit the result most are F9 (nothing inside a LitTemplate can
be found) and F13 (no Wrapper for `Crud`, `ConfirmDialog`, `EmailField`, `PasswordField`, `LoginOverlay`). The recipe's
output needed F3–F6 fixed by hand before it compiled and ran.

| # | Severity | Area | Summary |
|---|---|---|---|
| F1 | Low | `FindTestClassesToMigrate` | The dry run reports every class as `migrates` but doesn't list the page objects/custom elements they depend on |
| F2 | Low | Docs | The guide says recipe options are ignored up to alpha4, but alpha4 applies them |
| F3 | High | Recipe (build) | Removing `vaadin-testbench-junit5` drops 7 element jars still used by unconverted page objects → no compile |
| F4 | High | Recipe (code) | The migrated tests don't compile: `page` name clash, leftover `getDriver()` override, abstract method not updated, a call not updated, a rename on an unconverted type |
| F5 | High | Recipe (code) | `TestBenchElement`-style page objects are not converted and calls to them get no marker or `@RequiresTestingTool`, so 8 markers hide 16 unconverted tests |
| F6 | Medium | Recipe (code) | A Spring Boot app whose ITs hit an externally started server becomes `@UnifiedUITest` with no marker; `APP_URL` navigation is not recognised |
| F7 | Low | Recipe (build) | The pom rewrite drops comments and puts the test dependencies among the runtime ones |
| F8 | Low | Reports | Evidence goes under `surefire-reports` in a failsafe run; "scenarios without a check" is all noise after a migration; root navigation reads `open “”` |
| F9 | High | Unified API (all tools) | Components mapped with `@Id` in a LitTemplate, and LitTemplate components themselves, can't be found on any Testing Tool |
| F10 | High | Selenium adapter | `navigate(View.class)` on a route with an optional parameter opens the literal template; `isAt(View.class)` fails on that route and on its `@RouteAlias`, on Selenium only |
| F11 | Medium | Playwright adapter | `selectTab(..)` on a tab whose selection navigates away fails with a raw Playwright `TimeoutError` |
| F12 | Medium | Wrappers | `UnifiedTabs.selectTab(..)` doesn't follow a `RouterLink` inside the tab, the usual menu pattern |
| F13 | Medium | Core Set | No Wrapper for `LoginOverlay`/`LoginForm`, `Crud`, `ConfirmDialog`, `EmailField`, `PasswordField`; the Crud's buttons have a name only in a browser |
| F14 | Medium | `resolve-migration-markers` skill | The skill's full-suite proof (`mvn test`) runs no UI test in the TestBench `*IT` layout, and the skill never runs Selenium, where the kept Escape Hatches live |
| F15 | Low | API | `withTimeout(..)` loses the Wrapper type; `findComponent(..)` has no `click()`; `findComponent(RouterLink.class)` also matches `Anchor`s in browsers |
| F16 | Low | Docs | `@WithMockUser` needs `spring-boot-starter-security-test`, which the docs and the recipe don't mention |
| F17 | Low | Playwright vs Selenium | `findComponent(ConfirmDialog.class).visible()` finds the open dialog on Selenium, none on Playwright |
| F18 | Low | Style rules / recipe | The recipe and the marker skill leave TestBench waits that `UnifiedStyleRules` then rejects |

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
| f | `@BeforeEach void setup() throws Exception` becomes a `setup(page)` call at the start of each test, but the tests don't declare `throws Exception` | `unreported exception java.lang.Exception` |

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

### F8: report details on a migrated, failsafe-run suite

- `vaadin.testing.evidence-dir` defaults to `target/surefire-reports/unified-evidence` even when failsafe runs the
  tests (the `*IT` layout the TestBench recipe keeps). Next to `target/failsafe-reports` you'd expect
  `target/failsafe-reports/unified-evidence`.
- Right after migration, "Scenarios without a check" lists 13 of 16 scenarios. Their checks are TestBench/JUnit
  `Assertions` the recorder can't see. That's correct per spec, but a migrated suite always starts with this noise.
  Suggestion: in the summary, say how many of those scenarios call `Assertions.*`/`assert*` after the last step
  (e.g. "13 scenarios … (12 of them use JUnit assertions, not recorded)").
- `page.navigate("")` (the root route) is written as the User Step `open “”`. Suggestion: `open “/”` or the root
  view's name.

### F9: nothing inside a LitTemplate can be found

On Bakery's `StorefrontView` and `DashboardView` (both `LitTemplate`s), `page.describe()` lists only the menu tabs,
on Browserless and on Playwright. Direct lookups fail on both with `Expected exactly one grid, Found none`:

- `page.find(UnifiedGrid.class)` and `page.findComponent(Grid.class)` (the `@Id("grid") Grid<Order>`);
- the `@Id("search") SearchBar`'s text field and `@Id("action")` button (`withText("New order")`,
  `withText("New product")`);
- `findComponent(DashboardCounterLabel.class)` (`@Id`-mapped `LitTemplate`s) → 0.

`page.findComponent(SearchBar.class)` is found where the search bar is a plain child (Products), but `describe()`
inside it says "no component a Unified UI Test can find".

These are ordinary Flow components with server-side state (a `Grid` with a data provider, `Button`s with
listeners). TestBench finds them in the browser. Nothing in the docs mentions this gap: neither *Known limitations
by Testing Tool* nor the spec's gaps appendix. For Bakery it's the main blocker: 7 of 16 tests stay on the TestBench
Escape Hatch only because of it, and it also blocks creating products or users (the "New …" button) in 4 more.

**Repro:** a `@Route` `LitTemplate` with `@Id("grid") private Grid<X> grid;` →
`page.navigate(View.class); page.find(UnifiedGrid.class).getRowCount();` on any Testing Tool.

**Suggestion:** support `@Id`-mapped components (on Browserless, walk the template's mapped children; in browsers,
look inside the template's shadow root). Until then, list it as a known limitation, and have `describe()` say "N
components inside LitTemplate X are not findable" instead of nothing.

### F10: route templates on Selenium

`StorefrontView` is `@Route("storefront/:orderID?")` with `@RouteAlias("storefront/:orderID/edit")` and
`@RouteAlias("")`.

- On **Selenium**, `page.navigate(StorefrontView.class)` opens `storefront/:orderID`. The server logs `There was an
  exception while trying to navigate to 'storefront/:orderID' (IllegalArgumentException: Couldn't parse 'orderID'
  parameter value ':orderID' as long)`, which is shown nicely under the User Step, and the next lookup fails. The same
  call works on Browserless and Playwright.
- On **Selenium**, `assertThat(page).isAt(StorefrontView.class)` fails with `Found “storefront”` (after the login
  view forwards there) and `Found “”` (after login, at the root alias). It passes on Browserless and Playwright for
  the same scenario.

So a test that is green on two tools is red on the third, for a reason that has nothing to do with the app.

**Suggestion:** on Selenium, build the URL like the other adapters do (Flow's `RouteConfiguration.getUrl(..)`,
dropping an optional parameter), and match `isAt` against the route's template and its aliases. Add a route with
an optional parameter and an alias to the adapter conformance tests.

### F11: `selectTab(..)` when the selection navigates away

Bakery's menu has a "Logout" tab whose selection listener calls `Page.setLocation(..)`. On Playwright,
`page.find(UnifiedTabs.class).selectTab("Logout")` clicks, the page leaves, and then the adapter reads the
selected tab (`PlaywrightCoreSetAccess$Tabs.selected`). That times out with `com.microsoft.playwright.TimeoutError:
Timeout 10000ms exceeded`: not a `UnifiedAssertionError`, with no User Step or evidence.

**Suggestion:** don't verify the selection when the page navigated (as alpha4 already does for combo boxes the app
replaces, #377), and wrap any Testing Tool error from an action in a `UnifiedAssertionError`.

### F12: Tabs used as a navigation menu

`MainView` builds its menu as `Tabs` whose `Tab`s each hold a `RouterLink`. That's Bakery and many older Vaadin apps:
`AppLayout` + `Tabs` was the recommended menu before `SideNav`. A user clicks the tab, the click lands on the link,
and the app navigates. `selectTab("Users")` selects the tab and stays on the same view (Selenium: `Expected the page
to show “UsersView”, Found “storefront”`). The form that works is
`page.findComponent(RouterLink.class).withAccessibleName("Users").element().click()`, which needs `element()` and
reads `click router link …` in the reports.

**Suggestion:** have `selectTab` click what a user clicks (the tab's center, i.e. its link). Or add a Wrapper for
`RouterLink`/`Anchor` (`UnifiedLink`, with `withText` and `click()`): links are among the most common things a test
clicks, and `findComponent(..)` gives neither.

### F13: Core Set gaps that a typical business app hits

The Bakery uses these, and its tests touch them:

- **`LoginOverlay`:** the login tests can only stay on TestBench. The form fields exist only in the browser, but a
  Wrapper with `login(user, password)` for Playwright/Selenium (`@RequiresBrowser`) would cover the "type the
  password" scenarios.
- **`Crud`:** editor open or closed, the edit column's button per row, the Save/Cancel/Delete buttons, and the
  Crud's own discard/delete confirm dialogs. On Browserless the Crud's buttons are `button (no name)` ×4, because
  their text comes from client-side i18n. On Playwright they're `Save`, `Cancel`, `Delete`, `Discard`. So even
  `withText("Save")` can't be the same lookup on both tools.
- **`ConfirmDialog`:** `UnifiedDialog` is for `Dialog` only.
- **`EmailField`, `PasswordField`:** siblings of `TextField`, very common in forms, with no Wrapper. A sign-up form
  can't be written with the Core Set.

**Suggestion:** add `UnifiedEmailField`/`UnifiedPasswordField` (same API as `UnifiedTextField`),
`UnifiedConfirmDialog`, a `UnifiedCrud` (`openEditor(row)`, `save()`, `cancel()`, `delete()`, `isEditorOpen()`), and
a browser-only `UnifiedLoginOverlay`.

### F14: the skill's proof step can be green on 0 UI tests

Step 3 of `resolve-migration-markers` ends with `mvn test` and `mvn test -Dvaadin.testing.tool=playwright`.
TestBench projects name their browser tests `*IT` and run them with failsafe, and the TestBench recipe keeps those
names. Here `mvn test` ran 15 unit tests and **0 UI tests**, and passed. The per-class runs (`-Dtest=LoginIT`) do
run `*IT` classes, so only the full-suite check is hollow. The cross-class data check it exists for doesn't happen.

The skill also proves every change on Browserless and Playwright only. A test that keeps a TestBench Escape Hatch
(`@RequiresTestingTool(SELENIUM)`) is skipped on both, so a broken Escape Hatch test passes the skill's proof. In
this migration 14 of 16 tests run on Selenium only.

**Suggestion:** have the skill detect the runner (`*IT` + failsafe → `mvn verify`, or the profile that binds
failsafe) and check that the run's summary counts more than 0 UI tests. Add a Selenium run of the classes that
still have `@RequiresTestingTool(SELENIUM)`. And have the recipe either rename `*IT` to `*Test` or leave a marker in
the pom.

### F15: small API frictions

- `page.find(UnifiedGrid.class).withTimeout(Duration.ofSeconds(2))` returns `UnifiedWrapper`, so `.getRowCount()`
  and `.row(0)` don't compile after it. Expected a self-typed return.
- `page.findComponent(Tab.class)` and `findComponent(RouterLink.class)` return `UnifiedGenericComponent`, which has
  no `click()` (use `.element().click()`), though it has `withText(..)` and is a Scope.
- `findComponent(RouterLink.class)` matched 3 components on Browserless and 4 on Playwright/Selenium. In the
  browser, the logout `Anchor` (same `<a>` tag) counts as a router link too (`router link in container (id
  “logout-tab”)`), so the same lookup has a different count per tool.
- Browserless doesn't follow `Page.setLocation(..)` (Bakery's logout), so the test needs `@RequiresBrowser`. This
  isn't in *Known limitations*.

### F16: the test dependency for `@WithMockUser`

*Navigation, users and windows* says to use Spring's `@WithMockUser`, which is the natural replacement for a
TestBench test's login steps. But the annotation is in `spring-security-test`, which a TestBench project usually
doesn't have (Bakery didn't). The demo has `spring-boot-starter-security-test` in its pom, but no page says so.

**Suggestion:** mention the dependency where `@WithMockUser` is introduced. Have the TestBench recipe add it when
the project has Spring Security, and turn a login page-object call into a marker suggesting `@WithMockUser`.

### F17: `.visible()` on a confirm dialog differs between Playwright and Selenium

With the Crud's delete confirmation open, `page.findComponent(ConfirmDialog.class).visible()` finds it on Selenium
and finds none on Playwright (`Expected exactly one confirm dialog (visible), Found none`). In the browser the
`vaadin-confirm-dialog` host has no box; what the user sees is its overlay. `UnifiedDialog` presumably handles this
for `Dialog`. A generic lookup on an overlay component doesn't, and the two browser adapters disagree.

**Suggestion:** treat overlay components (`*-dialog`, `*-overlay` hosts) as visible when their overlay is open, the
same on every adapter. That would also come for free with a `UnifiedConfirmDialog` (F13).

### F18: the migration's own output fails the style rules

`write-unified-ui-tests` says to run `UnifiedStyleRules.ALL` after writing tests. After the recipe and
`resolve-migration-markers`, the rules failed 7 times on "No raw waits": `ElementQuery.waitForFirst(..)` and
`TestBenchEscapeHatch.waitUntil(..)` in TestBench Escape Hatch code, all in tests or helpers of a class with
`@RequiresTestingTool(SELENIUM)`. The recipe keeps `waitForFirst()`, and the marker skill's "Escape Hatch kept"
outcome keeps it too, so the toolchain's two halves disagree.

**Suggestion:** either allow the Testing Tool's own waits inside code that declares that Testing Tool (it's the
Escape Hatch's point), or have the recipe write `first()` after a Unified `navigate(..)` and `page.waitUntil(..)` for a
TestBench `waitUntil(..)`, and have the marker skill run the style rules in its proof step.
