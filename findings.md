# Findings: Unified Testing Tool 0.1.0-alpha5 on Bakery

Issues, surprises and suggestions found while migrating the Bakery TestBench tests with 0.1.0-alpha5.
Severity: **bug** (wrong behaviour), **recipe** (migration recipe missed / produced wrong code), **docs**, **api** (friction / missing feature), **info**.
The step where it was found refers to [MIGRATION_LOG.md](MIGRATION_LOG.md).

**18 findings: 4 High, 4 Medium, 9 Low, 1 positive.** The ones that limit the result most are F9 (Flow `Crud` can't
be tested on Browserless and has no Wrapper) and F13 (on Browserless, `UnifiedLink` lookups crash on pages with a
`Text`). F10 is the nastiest: a Playwright-only race that saves the wrong data, and it disappears when snapshots are on.
alpha4 vs alpha5: see [MIGRATION_LOG.md, Step 8](MIGRATION_LOG.md#step-8-comparison-alpha4-vs-alpha5).

| # | Severity | Area | Summary |
|---|---|---|---|
| F1 | Low | Dry run | `FindTestClassesToMigrate` lists no page objects and says `migrates` for classes that end up Selenium-only |
| F2 | **High** | Recipe | `:8080` `APP_URL` navigation in a base-class helper is kept, unmarked, after the switch to `@UnifiedSpringBootTest`, so the tests can silently hit the old server |
| F3 | Medium | Recipe | No Migration Marker on any call into a custom element / page object (only class-level `@RequiresTestingTool`) |
| F4 | Low | Recipe | Emits `TestBenchEscapeHatch.getRunLocallyBrowser()`, which doesn't exist (the only compile error) |
| F5 | Low | Recipe | Login marker on a statement that doesn't sign in, and in a class that tests the login itself |
| F6 | Medium | Reports | The password secret is masked as a plain string everywhere: user name and `@DisplayName` become `••••` |
| F7 | Low | API | Plain HTML in a template's shadow DOM is unreachable even in a browser; `findDescendants` doesn't pierce |
| F8 | ✔ | API | `@WithUserDetails` + `findTemplate(..).findComponent(..)` work on all three tools (positive) |
| F9 | **High** | Core Set / Browserless | Flow `Crud`: no Wrapper; on Browserless its buttons have no text and clicking them does nothing; its edit column has no header, so no `row.cell(..)` |
| F10 | **High** | Playwright adapter | An Escape Hatch click doesn't wait for the server and the next Unified action doesn't either: the old value overwrites typed text, the wrong data is saved; a heisenbug |
| F11 | Low | API | `hasPath` exact only; no current-path read |
| F12 | Low | Reports | Step text for a grid row is code; Findable View prints `<the class with @Tag(..)>` before first use |
| F13 | **High** | Browserless adapter | `page.find(UnifiedLink.class)` throws on any page with a Flow `Text` (alpha5 tag matching); hidden behind a timeout, links missing from the Findable View |
| F14 | Low | Reports | `Found 4: button “Cancel”, …` doesn't say which are hidden |
| F15 | Medium | Findable View | Inside a Template, `@Id` components without Java-set text are `not unique: N alike` instead of `withId(..)` |
| F16 | Low | Config | No window/viewport size setting |
| F17 | Medium | Recipe / docs | `@UnifiedSpringBootTest` silently changes what `it-native` / `it-saucelabs` test; no remote-grid setting |
| F18 | Low | Recipe / skill | `vaadin-testbench-junit6` kept though not needed after the markers are resolved |

## Details

<a id="f1"></a>

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

<a id="f2"></a>

## F2: Hard-coded `:8080` base URL survives the switch to `@UnifiedSpringBootTest` (recipe)

- **Step:** 2
- `AbstractIT` had `public String APP_URL = "http://localhost:8080/"` (non-final, reassigned to the site-local IP when not
  running locally) and `openLoginView(driver, url)` doing `driver.get(url)`.
- The recipe makes the class `@UnifiedSpringBootTest` (app on a **random port**) but keeps `driver.get(url)` in
  `openLoginView` through the Escape Hatch **without a Migration Marker**. Only the two `getDriver().get(APP_URL + "x")`
  in tests got the *“not a local application URL”* marker.
- **Impact:** silent wrong-server testing. Because the `it` profile still runs `spring-boot:start` on :8080, the migrated
  tests would have passed while testing the *old* server, not the app the test started (and fail with
  connection refused once the profile is cleaned up as the marker suggests).
- **Suggestion:** when the class becomes `@UnifiedSpringBootTest`, flag every `WebDriver.get(..)` / field holding
  `localhost:8080`, not just constant-concatenation call sites; ideally rewrite `driver.get(url)` with a String
  parameter to `page.navigate(path)`.

<a id="f3"></a>

## F3: No Migration Marker on calls into custom elements / page objects (recipe)

- **Step:** 2
- Docs (*What it leaves for you*) list the marker `TestBench page object: LoginViewElement (custom element extending
  LoginOverlayElement): write a Wrapper or convert the page object` and say *“Each statement calling one gets a
  Migration Marker”*.
- **Actual:** not a single statement calling `productsPage.getCrud()…`, `storefrontPage.getOrderCard(..)`,
  `loginView.login(..)` etc. got that marker; only the class-level `@RequiresTestingTool(SELENIUM)` shows it. 17 tests'
  worth of TestBench code is invisible to `grep "TODO unified"`, so the `resolve-migration-markers` skill (which works
  marker-by-marker) would miss most of the work.
- Related to [F1](#f1): the same blind spot in the dry run.

<a id="f4"></a>

## F4: Recipe emits `testBench.getRunLocallyBrowser()`, which doesn't compile (recipe)

- **Step:** 2
- `if (getRunLocallyBrowser() == null)` in the old `@BeforeEach` became
  `testBench.getRunLocallyBrowser()` with an *Escape Hatch* marker; `TestBenchEscapeHatch` has no such method →
  compilation error. The only compile error of the run.
- **Suggestion:** either expose it on the Escape Hatch or treat it like `@RunLocally` (*“browser setup removed”* marker).

<a id="f5"></a>

## F5: Login marker on a statement that doesn't sign in (recipe, minor)

- **Step:** 2
- `LoginIT.loginWorks`: `Assertions.assertEquals("Email", loginView.getUsernameLabel())` got
  *“signs in through the login form: … consider @WithMockUser”* – any statement calling a method on the login element is
  marked, not only the one that submits. Also, it's a `LoginIT`: every test there checks the login itself, so
  `@WithMockUser` advice is noise for that class.

<a id="f6"></a>

## F6: Password masking redacts the same word everywhere, incl. the user name and `@DisplayName` (bug)

- **Step:** 4 (`LoginIT`)
- `page.find(UnifiedLogin.class).login("barista@vaadin.com", "barista")`: the password is treated as a secret of the run,
  and every occurrence of the *string* `barista` in the reports is replaced. `user-steps.txt` reads:
  ```
  ✔ LoginIT › A signed-in •••• logs out from the menu and is back at the login
     1  Sign in as ••••@vaadin.com
        1.2  sign in as “••••@vaadin.com” in login
  ```
  The `@DisplayName` ("A signed-in barista …"), the `page.step` name and the user name are all mangled, though only
  the password was secret. The same happens with `admin` / `admin@vaadin.com`.
- Demo apps (and Spring Security samples) very often use password == user name prefix, so this hits most first
  experiences of `login(..)`.
- **Suggestion:** mask only the value typed into the password field (and the field's value in failures), not a global
  find/replace; or at least don't mask inside the display name and inside a longer word / e-mail.

<a id="f7"></a>

## F7: No way to reach plain HTML written in a template's shadow DOM, even in a browser (api)

- **Step:** 4 (`DashboardViewIT.checkRowsCount`)
- The test counts the `<vaadin-board-row>`s that `dashboard-view.js` writes in its Lit template (no `@Id`, no
  server-side component). `page.findTemplate(DashboardView.class).element().findDescendants("vaadin-board-row")`
  returns **0** on Playwright and Selenium – `findDescendants` doesn't enter the template's shadow root – and the
  Findable View lists only `Template <dashboard-view>`.
- That's consistent with ADR 0012 (only `@Id` components inside a Template), but it leaves the Escape Hatch as the only
  option for a browser-only check; TestBench's `$` pierced shadow roots by default. Kept as a TestBench Escape Hatch
  (Selenium only) rather than Playwright `unwrap(Page.class)` to keep the original code.
- **Suggestion:** document that `UnifiedElement.findDescendants` doesn't pierce shadow roots; consider a browser-only
  `findDescendants` that does (Playwright's CSS engine pierces open shadow roots by default anyway).

<a id="f8"></a>

## F8 (positive): `@WithUserDetails` + `@Id` components in a Template work on Browserless

- **Step:** 4 (`DashboardViewIT.checkCounters`)
- `@WithUserDetails("admin@vaadin.com")` (the app's own `UserDetailsService`) signed the user in on all three tools with
  no extra setup, and `page.findTemplate(DashboardView.class).findComponent(DashboardCounterLabel.class)` with
  `hasCount(4)` passes on Browserless, Playwright and Selenium. The test used to need a real browser + login form.

<a id="f9"></a>

## F9: Flow's `Crud` has no Wrapper and can't be driven on Browserless (api, gap)

- **Step:** 4 (`ProductsViewIT`, `UsersViewIT`)
- Bakery's Products and Users views are `Crud`s (grid + editor). The Core Set has no `UnifiedCrud`, the TestBench
  recipe leaves `CrudElement` untouched, and neither the docs nor the *Known limitations* page mention `Crud`.
- On Browserless the Crud's Save / Cancel / Delete buttons are Flow `Button`s **without text** (the web component
  sets it from the i18n in the browser): the Findable View shows `button (no name)  not unique: 4 alike`. Found by
  `withAttribute("slot", "save-button")`, clicking them **does nothing**: the save/cancel/delete are DOM events the
  `vaadin-crud` web component fires to the server. The confirm dialog (`Discard changes`) never opens either.
- The edit button (pencil) is rendered by `Crud.addEditColumn` (a `LitRenderer`, no Flow component) in a column
  **without a header**, and `UnifiedGridRow.cell(..)` only takes a header → not reachable with the Unified API even
  in a browser. Wrote a helper that clicks it through Dramafinder (`GridElement.findCell(row, col)`) or TestBench
  (`GridElement.getCell(row, col)`), picked with `page.tryUnwrap(Page.class)`.
- Browserless Test itself has no `CrudTester` either.
- **Result:** all Products and Users tests are `@RequiresBrowser` (Playwright + Selenium).
- **Suggestions:** a `UnifiedCrud` (editor open/closed, `edit(row)`, `save()`, `cancel()`, `delete()`, the two confirm
  dialogs) with Browserless support that fires the Crud's events; `UnifiedGridRow.cell(int columnIndex)`; list
  `Crud` under the known gaps.

<a id="f10"></a>

## F10: An Escape Hatch action on Playwright doesn't wait for the server, and the next Unified action doesn't either (bug-ish / docs)

- **Step:** 4 (`ProductsViewIT.editProductTwice`, Playwright only, deterministic)
- Sequence: Playwright click on the Crud's pencil (Escape Hatch) → `assertThat(nameField).isVisible()` passes at
  once (the editor opens client-side) → `nameField.setValue("New …")` → `save`. The server's answer to the edit
  (load entity, navigate to `products/{id}`, `binder.readBean`) lands **after** `setValue` and puts the old name back,
  so Save stores the old name. The test fails on Playwright, passes on Selenium (TestBench's click waits for Vaadin),
  and passes on Playwright with `-Dvaadin.testing.user-steps.snapshots=true` (slower steps) – a heisenbug.
- The skill/docs say Escape Hatch code *“gets none of the Unified API's waiting”*, but they imply the next Unified
  action waits for the server again (Auto-Wait). Here `setValue` did not wait for the round trip the Escape Hatch
  started.
- Worked around with `assertThat(name).hasValue(<current name>)` before typing (a meaningful check anyway).
- **Suggestions:** make the Playwright adapter's actions wait for Vaadin's idle state before acting (not only after),
  or offer `page.waitForServer()` for Escape Hatch code; mention this pitfall next to the Escape Hatch docs.

<a id="f11"></a>

## F11: `assertThat(page).hasPath(..)` is exact only; no way to read the current path (api, minor)

- **Step:** 4 (`ProductsViewIT.editProduct`)
- The old test checked that opening a row put the item's id in the URL (`currentUrl.length() > before`). The id is
  unknown to the test, `hasPath` compares the whole path, and `UnifiedPage` has no `getPath()`. The check was dropped;
  `isAt(ProductsView.class)` can't tell `products` from `products/42`.
- **Suggestion:** `hasPathStartingWith(..)` / `hasPathMatching(regex)` or a `currentPath()` read.

<a id="f12"></a>

## F12: Small report / Findable View wording issues (info)

- A User Step for a `page.step("Edit " + row, ..)` reads `Edit row().withCellText("Product Name", "…")`: a grid row's
  `toString()` is code, not words like the built-in steps (`row where Product Name is “…”`).
- Before the template was entered, the Findable View printed
  `page.findTemplate(<the class with @Tag("search-bar")>.class)` instead of `SearchBar.class` (it printed the real
  class name in the same run after the first `findTemplate(SearchBar.class)` call).

<a id="f13"></a>

## F13: On Browserless, `page.find(UnifiedLink.class)` throws when the page holds a Flow `Text` component (bug, high)

- **Step:** 4 (`UsersViewIT.accessDenied`)
- `page.find(UnifiedLink.class).all()` → `UnsupportedOperationException` at
  `AbstractTextElementStateProvider.getTag` ← `Element.getTag` ← `BrowserlessSession.hasQueriedTag(BrowserlessSession.java:307)`.
  `hasQueriedTag` (new in alpha5, for `@UnifiedComponent(value = Component.class, tag = "a")`) calls
  `c.getElement().getTag()` on every component, and a `Text` component's element is a text node, which has no tag.
- Bakery's menu (`RouterLink` with an icon and `a.add(title)` → `Text`) and its 404 view (`new Text("Oops …")`) both hit it.
  `HasComponents.add(String)` creates a `Text`, so most apps will.
- **Symptoms:** inside an assertion the exception is swallowed: `assertThat(link).exists()` fails after 10 s with only
  `Expected link “Go to the front page.” to exist` – no `Found`, no `Nearby`, no cause. The Findable View silently
  leaves **every** link out on Browserless (on Playwright it lists them), so `Did you mean` can't help either.
- **Fix:** skip text nodes (`c.getElement().isTextNode()`) in `hasQueriedTag`; and let a lookup's unexpected exception
  surface as the cause of the `UnifiedAssertionError` instead of a timeout.
- **Workaround used:** `assertThat(page).hasTitle("Page was not found")` and
  `page.findComponent(RouterLink.class).withText("Go to the front page.")` (a class with its own `@Tag` skips the tag check).
- **Repro (Browserless):** a view with `add(new Text("x"), new RouterLink("Home", HomeView.class))`, then
  `page.find(UnifiedLink.class).withText("Home").click()`.

<a id="f14"></a>

## F14: The default Crud/confirm-dialog lookups need `.visible()` everywhere (info)

- **Step:** 4
- In a browser, `page.find(UnifiedButton.class).withText("Cancel")` matched 4 buttons (the Crud editor's and the
  hidden buttons of the Crud's two confirm dialogs and of the main layout's `ConfirmDialog`), as did
  `UnifiedConfirmDialog` without a header filter. The failure message (`Found 4: button “Cancel”, …`) doesn't say
  which are hidden, while the Findable View does (`(hidden)`). Adding `(hidden)` to the `Found` list would point
  straight at `.visible()`.

<a id="f15"></a>

## F15: The Findable View suggests nothing for a Template's `@Id` components whose text is in the template HTML (api)

- **Step:** 4 (`StorefrontViewIT`)
- On Browserless, inside `order-details`, the view prints `button (no name)  not unique: 5 alike` five times, and in
  `order-editor` `combo box (no name)  not unique: 3 alike`. They are `@Id` fields (`edit`, `save`, `cancel`, …), and
  `withId(..)` is exactly what the docs recommend inside a Template, but the view doesn't suggest it (alpha5 added
  `data-*` disambiguation, not id). On Playwright the same buttons show their text.
- **Suggestion:** inside a Template, fall back to `withId("…")` for a component with an id, at least for `@Id` fields.
- Related: the page-level Findable View stops at `Template <storefront-view>`; you have to call `describe()` on each
  nested template to discover the dialog, then the editor, then the item editor. Fine, but a hint like
  `Template <storefront-view> (has a dialog, a grid, 2 templates)` would save a few rounds.

<a id="f16"></a>

## F16: No setting for the browser window size (api, minor)

- **Step:** 4 (`AbstractIT`)
- The TestBench base resized the window to 1024×800 before each test. There's no `vaadin.testing.*` setting for a
  viewport/window size (Playwright and Selenium); the only way is an Escape Hatch per tool, which would make every
  test tool-specific. Dropped it; the tests pass at the default size. Responsive apps will want this.

<a id="f17"></a>

## F17: `springBootTest: true` silently changes what the other Failsafe profiles test (recipe / docs)

- **Step:** 6
- The recipe adds the evidence dir to `it-saucelabs` and `it-native` too, but the switch to `@UnifiedSpringBootTest`
  changes what they mean:
  - `it-native` ran the `*IT`s against a native executable started outside the build. Now every test class starts
    the JVM app itself; the native image isn't tested any more, and nothing says so.
  - `it-saucelabs` passes `com.vaadin.testbench.Parameters.gridBrowsers` / sauce options, which the Unified Selenium
    adapter doesn't read (there is no remote-grid setting in the configuration page), so these runs would use a local
    Chrome.
- The marker only talks about removing `spring-boot:start/stop`. **Suggestion:** a marker on each profile that
  starts/targets an external server or a remote grid; document remote Selenium grids (or their absence).

<a id="f18"></a>

## F18: The recipe keeps `vaadin-testbench-junit6`, which is not needed once page objects are gone (info)

- **Step:** 6
- Correct at the time (page objects still used TestBench), but after resolving the markers, the remaining
  `TestBenchEscapeHatch` code compiled without it. Nothing tells the user to re-check; a hint in the skill's final step
  ("remove `vaadin-testbench-*` once no page object is left") would help.

