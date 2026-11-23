# Migration log: TestBench → Unified UI Tests 0.1.0-alpha4

Migration of the Bakery app's TestBench integration tests (`src/test/java/.../testbench`) to the
[Unified API for Vaadin UI tests](https://github.com/parttio/unified-testing-tool) `0.1.0-alpha4`, following
[Migrating from TestBench](https://parttio.github.io/unified-testing-tool/migrating-from-testbench/).

- Branch: `unified-testing-tool-alpha4`, from `v25` at `59c7c46` (Vaadin 25.3.5, Spring Boot 4.1.2).
- Environment: macOS (Darwin 25.6, arm64), OpenJDK 25.0.3, Maven, Chrome, cached Playwright Chromium.
- Issues with the tool: see [findings.md](findings.md). Before/after comparison: [at the end](#comparison).
- Migration PR (draft): https://github.com/jcgueriaud1/bakery-app-starter-flow-spring/pull/1
- Upstream feedback: [parttio/unified-testing-tool#524](https://github.com/parttio/unified-testing-tool/issues/524) and its sub-issues.

Each step is one commit (or several, as noted).

## Starting point

| | |
|---|---|
| Test classes (UI) | 5 ITs: `LoginIT`, `DashboardViewIT`, `ProductsViewIT`, `StorefrontViewIT`, `UsersViewIT` |
| Base class | `AbstractIT<E extends TestBenchElement> extends BrowserTestBase` |
| Tests | 17 `@BrowserTest` (1 `@Disabled`: `StorefrontViewIT.editOrder`) |
| Page objects / custom elements | 18 classes under `testbench/elements` |
| Lines of UI test code | 944 (`testbench/**/*.java`) |
| How they run | `mvn verify -Pit`: Spring Boot started by `spring-boot-maven-plugin:start` on port 8080, then failsafe runs the ITs against `http://localhost:8080/` in local Chrome |

## Step 0: Baseline (TestBench)

`mvn -B verify -Pit`, 3 consecutive runs, local Chrome (TestBench `runLocally=chrome`, the tests run in parallel).

| Run | Result | Wall time (whole build) | IT phase (server start → done) |
|---|---|---|---|
| 1 | 16 passed, 1 skipped | 85 s | ~49 s (first run: frontend bundle check, cold JVM) |
| 2 | 16 passed, 1 skipped | 38 s | ~28 s |
| 3 | **11 passed, 5 errors**, 1 skipped | 89 s | ~78 s |

Run 3's 5 errors are all `SessionNotCreatedException: DevToolsActivePort file doesn't exist` /
`Devtools port number file contents <> were in an unexpected format`: Chrome failed to start while several
sessions launched in parallel, and each failing test waited ~60 s first. No test logic failed, but the suite
**is flaky at the infrastructure level** (1 run out of 3). This is the reference for the comparison.

Per-test time (run 1): ~30 s for the first test of each class (they all wait on the first page load of the
just-started server, in parallel), 1.5–4 s for the next ones (`UsersViewIT` is `@Isolated`, so it runs alone after
the others).

## Step 1: Dry run (`FindTestClassesToMigrate`)

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.46.1:dryRun \
  -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-alpha4 \
  -Drewrite.activeRecipes=org.vaadin.addons.unified.rewrite.FindTestClassesToMigrate \
  -Drewrite.exportDatatables=true
```

10 s. No file changed. The `TestClasses.csv` data table lists the 5 ITs and `AbstractIT`, all `migrates`, with an
empty `reason`.

What it doesn't say: the 18 TestBench page objects/custom elements in `testbench/elements` (`LoginViewElement`,
`StorefrontViewElement`, `OrderCardElement`, `BakeryCrudViewElement`, ...) hold most of the logic (the tests call
`openLoginView().login(..)`, `getMenu().navigateToUsers()`, `getCrud().isEditorOpen()`, ...). Every test depends
on them, and they aren't in the report, so "6 × migrates" overstates what the recipe will do here (finding F1).

## Step 2: Run the Migration Recipe (raw output, committed untouched)

`rewrite.yml` (deleted after the run, it isn't needed any more):

```yaml
type: specs.openrewrite.org/v1beta/recipe
name: com.vaadin.starter.bakery.MigrateTestBench
recipeList:
  - org.vaadin.addons.unified.rewrite.MigrateTestBenchToUnified:
      addPlaywright: true
      includePageObjects: true
      addAgentSkill: true
```

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run \
  -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-alpha4 \
  -Drewrite.activeRecipes=com.vaadin.starter.bakery.MigrateTestBench
```

About 10 s. The options **were applied**: the Playwright adapter and `resolve-migration-markers` skill were added,
although the guide says alpha4 ignores them (#353) (finding F2).

### What changed

| | |
|---|---|
| `pom.xml` | imports `unified-test-bom` 0.1.0-alpha4; adds `unified-test-spring`, `-browserless`, `-playwright`, `-selenium`, `browserless-test-spring`; removes `vaadin-testbench-junit5` |
| Test classes | the 5 ITs and `AbstractIT`: `extends BrowserTestBase` → `@UnifiedUITest`; `@BrowserTest` → `@Test` + `UnifiedPage page` parameter; `@BeforeEach setup()` → `setup(page)` call at the start of each test; `$(..)` on the test class → `TestBenchEscapeHatch` |
| Page objects (18 files) | **unchanged**, although `includePageObjects: true` |
| New files | `AGENTS.md`, `.claude/skills/write-unified-ui-tests/SKILL.md`, `.claude/skills/resolve-migration-markers/SKILL.md` |
| Diff | 6 test files, +176 / −99 lines |

Counts in the recipe output: **8 Migration Markers**, 6 `TestBenchEscapeHatch.of(page)`, 5
`@RequiresTestingTool(SELENIUM)` (4 tests + `editOrder`, which is also `@Disabled`).

### Result: it doesn't compile

`mvn test-compile` stops at javac's 100-error limit. Two causes:

1. **The build:** removing `vaadin-testbench-junit5` also removes the element jars the untouched page objects
   use: `html`, `app-layout`, `board`, `confirm-dialog`, `crud`, `form-layout`, `login` (`*.testbench` packages
   not found). The Selenium adapter only brings button, checkbox, combo-box, date(-time)-picker, details, dialog,
   grid, menu-bar, messages, notification, radio-button, tabs, text-field, time-picker and upload (finding F3).
2. **The test code** (finding F4):
   - `UsersViewIT`: 3 tests already had a local `UsersViewElement page = openView(..)`, so `openView(page)` now
     clashes with the new `UnifiedPage page` parameter (`variable page is already defined`).
   - `AbstractIT`: `extends BrowserTestBase` is gone, but its `@Override getDriver()` (calling `super.getDriver()`)
     stays, and so do the plain `getDriver()` calls in `AbstractIT`, `LoginIT` and `ProductsViewIT`. They got no
     Escape Hatch and no marker.
   - `AbstractIT.openView()` stays `abstract E openView()`, while every override became `openView(UnifiedPage page)`.
   - `LoginIT.openLoginAfterLoggedIn` calls the test method `loginToNotDefaultUrl()`, which now takes a `page`;
     the call wasn't updated.
   - `ProductsViewIT`/`UsersViewIT`: `getCell(text).getRow()` on a TestBench `GridElement` became `.getIndex()`, a
     method `GridTHTDElement` doesn't have. The grid rule renamed the method although the grid stayed a TestBench
     element (in `createProduct`, inside the Escape Hatch, `getRow()` was left).

### What the markers don't show

Only the statements that called TestBench **on the test class itself** got a marker or `@RequiresTestingTool`. The
tests that go through page objects, which is all of them, look migrated but aren't. For example, `LoginIT.loginWorks`
has no marker and no `@RequiresTestingTool`, and its whole body is TestBench (`openLoginView(page)` →
`LoginViewElement.login(..)`). On Browserless or Playwright these would fail instead of being skipped (finding F5).
The app is a Spring Boot app, but the tests had no `@SpringBootTest`: they called an external server at
`APP_URL`, so they became `@UnifiedUITest`, with no Spring context and no marker about `APP_URL` / `driver.get(url)`
navigation (finding F6).

## Step 3: Make it build and run (on Selenium)

Goal: the smallest change that makes the recipe's output compile and pass, with the tests' meaning unchanged. The
result is the honest "recipe + minimal fixes" state: **every test still drives the TestBench page objects, so the
suite is Selenium-only.**

| Change | Why |
|---|---|
| `pom.xml`: add `com.vaadin:vaadin-testbench-junit6` (test) | F3: brings back the element jars (`html`, `crud`, `board`, `login`, `app-layout`, `form-layout`, `confirm-dialog`) the page objects use; JUnit 6 flavour, like the adapter's TestBench core |
| `pom.xml`, profile `it`: remove `spring-boot:start/stop` | the app now starts inside the test JVM (`@UnifiedSpringBootTest`, random port) |
| `AbstractIT`: `@UnifiedUITest` → `@UnifiedSpringBootTest` | F6: Bakery is a Spring Boot app |
| `AbstractIT`: `@RequiresTestingTool(SELENIUM)` on the class (inherited since alpha4) | F5: every test uses TestBench page objects |
| `AbstractIT`: drop `APP_URL`, the `IPAddress` grid logic, the `getDriver()` override; `openLoginView(page, path)` uses `page.navigate(path)` | F4b, F6: the base URL is the adapter's job |
| `AbstractIT.setup(page)`: `getDriver()` → `testBench.getDriver()` (marker added), `throws Exception` dropped | F4b, and F4f: the old `@BeforeEach ... throws Exception` became a call in test methods that don't declare it |
| `abstract E openView()` → `openView(UnifiedPage page)` | F4c |
| `UsersViewIT`: local `page` → `usersView` | F4a |
| `LoginIT`: `loginToNotDefaultUrl(page)`; `getDriver().get(APP_URL + "login")` → `page.navigate("login")` | F4d |
| `getIndex()` → `getRow()` back on TestBench `GridTHTDElement` | F4e |
| `getDriver()` in `LoginIT`/`ProductsViewIT` → `TestBenchEscapeHatch.of(page).getDriver()`, with markers | F4b |

Markers now: 10 (7 of the recipe's 8: its `getRunLocallyBrowser()` marker went with the grid-IP code; + 3 `getDriver()` ones added here, in the recipe's own format).

### Runs

| Run | Command | Result | Wall time |
|---|---|---|---|
| Selenium | `mvn verify -Pit -Dvaadin.testing.tool=selenium` | 16 passed, 1 skipped (`@Disabled`) | **29 s** |
| Browserless | `mvn verify -Pit` | 17 skipped (`@RequiresTestingTool(SELENIUM)`) | 6 s |

The Selenium run is faster than the TestBench baseline (38–89 s): one headless Chrome reused for the run, a fresh tab
per test (`vaadin.testing.selenium.reuse-browser=true`), instead of a Chrome per test, and no separate app start.
The run writes `summary.md`, `summary.json` and `report.html`, with a `user-steps.txt` per test.

Observations on the reports (finding F8):
- The evidence goes to `target/surefire-reports/unified-evidence` even though failsafe runs the tests.
- 13 of 16 scenarios are listed under "Scenarios without a check". Expected, since their checks are TestBench
  `Assertions.assert*` calls the recorder can't see, but noisy for a freshly migrated suite.
- Navigating to the root is reported as `open “”`.

## Step 4: `/resolve-migration-markers` (started by the user, run by the agent)

The skill is user-invoked only (`disable-model-invocation: true`), so the user typed `/resolve-migration-markers`
and the agent followed it. It took about **14 minutes** (14:37 → 14:51), including reading the API reference and
the views, and two throwaway probe tests that printed `page.describe()` and tried lookups on each view
(deleted afterwards).

### What the probes showed

`page.describe()` and direct lookups, signed in as admin with `@WithMockUser`:

| View | Browserless | Playwright |
|---|---|---|
| Storefront (`LitTemplate`, grid/dialog/search bar `@Id`-mapped, order cards drawn by a `LitRenderer`) | tabs only | tabs + 3 hidden buttons of `MainView`'s confirm dialog |
| Dashboard (`LitTemplate`: client-side `vaadin-board`, `@Id` counters, charts, grid) | tabs only; `findComponent(DashboardCounterLabel.class)` → 0 | same |
| Products / Users (`Crud` built in Java, `SearchBar` template) | grid ✔, editor text fields ✔, combo box ✔; Crud buttons `button (no name)` ×4; **no "New product"/"New user"** | grid ✔, fields ✔, Crud buttons named (`Save`, `Cancel`, `Delete`, `Discard`, all hidden); **no "New product"/"New user"** |

So with alpha4, **nothing a LitTemplate maps with `@Id` can be found, on any Testing Tool** (finding F9). Bakery is
built on such templates. `EmailField`, `PasswordField`, `LoginOverlay`, `Crud` and `ConfirmDialog` have no
Wrapper, and the Crud's buttons have a name only in a browser (finding F13).

### What was converted

- **Signing in:** every test that isn't about login now uses Spring's `@WithMockUser` (needs
  `spring-boot-starter-security-test`, finding F16) instead of typing into the login overlay. This removes the
  `$(LoginViewElement.class)` entry marker for 4 classes, and each test is one login shorter.
- **Navigation:** `page.navigate(View.class)` / `page.navigate("storefront")` instead of clicking through the
  menu page object. The menu is clicked through its `RouterLink`s where the test is about the menu.
- **Checks:** `assertThat(page).isAt(..)`, `hasPath(..)`, `assertThat(tabs).hasTabs(..)`/`hasSelectedTab(..)`,
  `UnifiedGrid` rows (`row().withCellText("Email", ..)`, `assertThat(row(n)).hasCellText(..)`), `UnifiedTextField`
  and `UnifiedComboBox` by label, `UnifiedNotification`.
- **`@DisplayName`** on every test (the skill's style).
- **`AbstractIT`** is now a plain `@UnifiedSpringBootTest` base class (one Spring context), without the generic
  page-object type, `openView()` or the window resizing.

Fully Unified, so they run on more than Selenium: `LoginIT.openLoginAfterLoggedIn` and `UsersViewIT.accessDenied`
(all 3 tools) and `LoginIT.logout` (`@RequiresBrowser`: the app logs out with `Page.setLocation(..)`, which
Browserless doesn't follow).

### Problems hit on the way

| Problem | Tool | Workaround | Finding |
|---|---|---|---|
| `page.navigate(StorefrontView.class)` opened `storefront/:orderID` (route `storefront/:orderID?`): server error `Couldn't parse ':orderID' as long` | Selenium | `page.navigate("storefront")` | F10 |
| `assertThat(page).isAt(StorefrontView.class)` fails at `storefront` and at the `""` alias | Selenium (passes on Browserless and Playwright) | `assertThat(tabs).hasSelectedTab("Storefront")` | F10 |
| `selectTab("Logout")`: the tab's listener navigates away, then the tab's selection is read: raw Playwright `TimeoutError` | Playwright | `findComponent(Tab.class).withAccessibleName("Logout").element().click()` | F11 |
| `selectTab("Users")` selects the menu tab but doesn't follow the `RouterLink` inside it | all | `findComponent(RouterLink.class).withAccessibleName("Users").element().click()` | F12 |
| `findComponent(RouterLink.class)` found 3 links on Browserless, 4 in browsers (the logout `Anchor` too) | browsers | `.withAccessibleName("Go to the front page.")` | F15 |
| `withTimeout(..)` returns `UnifiedWrapper` (can't call `getRowCount()` after it); `findComponent(..)` has no `click()` | all | global `vaadin.testing.timeout`; `element().click()` | F15 |

### Skill step 4: the report

| # | Marker | Outcome | What changed |
|---|---|---|---|
| 1 | `AbstractIT`: `getDriver()` (window size) | resolved | setup removed: the adapters choose the window; every test passes without it |
| 2 | `AbstractIT`: `$(LoginViewElement.class)` | resolved for 4 classes / **kept** in `LoginIT.loginWorks` and `loginToNotDefaultUrl` | `@WithMockUser`; kept: `LoginOverlay` has no Wrapper, its form exists only in the browser |
| 3 | `LoginIT`: `getDriver()` (URL ends with `login`) | resolved | `assertThat(page).isAt(LoginView.class)` |
| 4 | `LoginIT`: `$(StorefrontViewElement.class)` | resolved | `assertThat(tabs).hasSelectedTab("Storefront")` (F10) |
| 5 | `LoginIT`: `$(LoginViewElement.class)` (absent) | resolved | same check: the user is on the storefront |
| 6 | `ProductsViewIT`: `getDriver()` (URL) | resolved / **kept** | `hasPath("products")`; kept for "the URL has the product id": `hasPath` needs the whole path |
| 7 | `ProductsViewIT`: `waitUntil(.. getCell(name))` | resolved | `page.find(UnifiedGrid.class).row().withCellText("Product Name", name)` |
| 8 | `UsersViewIT`: `$("vaadin-app-layout > div > a")` | resolved | `findComponent(RouterLink.class).withAccessibleName("Go to the front page.")` |
| 9 | `StorefrontViewIT.editOrder`: `waitUntil(URL matches …/edit)` | **kept** | `hasPath` needs the whole path; the test is `@Disabled` |
| 10 | `StorefrontViewIT.editOrder`: `$(NotificationElement.class).last()` | resolved | `assertThat(page.find(UnifiedNotification.class).withTextContaining("was updated")).isOpen()` |

No `TODO unified:` left. **11 `// Escape Hatch kept:` comments**, each with its reason: the whole of
`DashboardViewIT` and `StorefrontViewIT` (LitTemplate content, F9); the Crud flows in `ProductsViewIT` and 4 of 5
`UsersViewIT` tests ("New …" in the SearchBar template, F9; Crud, `EmailField`, `PasswordField`, confirm dialogs
without Wrapper, F13); the 2 real-login tests (`LoginOverlay`, F13).

`editOrder` was given one try without `@Disabled` on Selenium: it fails in the old TestBench page object
(`OrderItemEditorElement` looks for `div[part='increase-button']`, which Vaadin 25's integer field doesn't have).
That breakage predates the migration, so the test stays `@Disabled`.

### Skill step 3: prove it

- Per class, on Browserless and Playwright as the skill says, and on Selenium too: all pass or are skipped
  (Selenium-only classes are skipped on the other two).
- The skill's full-suite proof, `mvn test`, ran **15 unit tests and 0 UI tests** and was green. The recipe kept
  the `*IT` names, which surefire excludes (finding F14). The real full-suite runs:

| Tool | Command | Result | Wall time |
|---|---|---|---|
| Browserless | `mvn verify -Pit` | **2 passed**, 15 skipped | 12 s |
| Playwright | `mvn verify -Pit -Dvaadin.testing.tool=playwright` | **3 passed**, 14 skipped | 15 s |
| Selenium | `mvn verify -Pit -Dvaadin.testing.tool=selenium` | **16 passed**, 1 skipped | 28 s |

Compared with the Browserless run (`vaadin.testing.user-steps.compare-dir`): "2 scenarios ran on both Testing
Tools: same result and same User Steps on each."

## Step 5: Custom Wrappers, style rules, stability

Beyond the markers, the skill's reference for a component without a Wrapper is "write one next to the tests". Time-boxed
attempt, on the two `UsersViewIT` tests that don't need the "New user" button (blocked by F9):

| Wrapper (`testbench/wrappers`) | For | Methods |
|---|---|---|
| `UnifiedEmailField` | Flow `EmailField` | `withLabel`, `setValue`, `getValue` |
| `UnifiedPasswordField` | Flow `PasswordField` | same |
| `UnifiedCrud` | Flow `Crud` | `editRow(int)` (clicks the row's `vaadin-crud-edit`), `isEditorOpen()` |

65 lines, about 15 minutes including two failed tries:
- The Crud's delete confirmation has a "Delete" button like the editor's, both visible:
  `page.find(UnifiedButton.class).withText("Delete").visible()` → `Found 2`. Scoped with
  `page.findComponent(ConfirmDialog.class)`.
- `findComponent(ConfirmDialog.class).visible()` found the open dialog on Selenium but **none on Playwright**: the
  `vaadin-confirm-dialog` host has no size there, since the content is in its overlay (finding F17). Picked it with
  `.withTextContaining("Are you sure you want to delete")` instead.

`tryToUpdateLockedEntity` and `tryToDeleteLockedEntity` are now fully Unified and `@RequiresBrowser` (the Crud's
edit buttons and button texts exist only in the browser): they now also run on Playwright. The other TestBench
tests all need the search bar's "New …" button or the Crud's hidden toolbar button (F9), so they stay on the
Escape Hatch.

### Style rules

Added `UnifiedStyleTest` (ArchUnit, `unified-test-archunit`), as `write-unified-ui-tests` says. First run: 7
violations, all **"No raw waits"** on `ElementQuery.waitForFirst(..)` / `TestBenchEscapeHatch.waitUntil(..)`. That's
code the recipe produced and the marker skill kept, in tests that declare `@RequiresTestingTool(SELENIUM)` (finding
F18). Fixed with `first()`, since `page.navigate(..)` already waited for the server, and with
`page.waitUntil(..)`. The style test then passes.

### Stability: full suite, 3 rounds per tool

| Tool | Round 1 | Round 2 | Round 3 | Passed / skipped |
|---|---|---|---|---|
| Browserless | ✔ 12.7 s | ✔ 12.1 s | ✔ 14.2 s | 2 / 15 |
| Playwright | ✔ 16.7 s | ✔ 16.6 s | ✔ 17.7 s | 5 / 12 |
| Selenium | ✔ 26.3 s | ✔ 26.7 s | ✔ 27.0 s | 16 / 1 (`@Disabled`) |

9 of 9 runs green, no flaky test. (Wall time of `mvn verify -Pit`, whole build included.) The Playwright run
compared with the Browserless run: "2 scenarios ran on both Testing Tools: same result and same User Steps on each."

## Step 6: Cleanup

- Deleted the menu page objects no test uses any more (navigation now goes through `page.navigate(..)` and
  `RouterLink`s): `HasApp`, `MainViewElement`, `AppNavigationElement` (−3 files, −56 lines).
  `ItemDetailDialogElement` was already unused before the migration and is left as it was.
- `pom.xml`, profile `it`: the comment says how to run on each Testing Tool; TestBench's `runLocally=chrome`
  parameter is gone (the Selenium adapter runs headless Chrome by default).
- `README.md`: the "Running Integration Tests" section explains the three Testing Tools and that most tests are
  still Selenium-only.
- Checked the README's recommended `mvn verify -Pit,production -Dvaadin.testing.tool=selenium`: 16 passed,
  1 skipped, 32 s.

Left as they were, outside this migration's scope:
- `it-saucelabs` still passes TestBench's grid parameters. The Unified Selenium adapter would need its own
  configuration (`SELENIUM_REMOTE_URL`, `vaadin.testing.selenium.*`), which wasn't tried.
- `it-native` was meant to run the ITs against a native executable started outside the build. A
  `@UnifiedSpringBootTest` always starts the application in the test JVM, so that profile no longer tests the native
  image. Pointing the tests at an external server (`vaadin.testing.base-url`) would mean a non-Spring
  `@UnifiedUITest` base class, which wasn't tried.

## Comparison

TestBench suite at `59c7c46` vs. Unified alpha4 suite at the end of this branch. All runs on the same machine,
`mvn verify -Pit`, dev mode.

### Where each test runs

| Test | TestBench | Unified: Browserless | Unified: Playwright | Unified: Selenium |
|---|---|---|---|---|
| `LoginIT.loginWorks` | ✔ | – | – | ✔ (Escape Hatch: `LoginOverlay`) |
| `LoginIT.logout` | ✔ | – (`setLocation` not followed) | ✔ | ✔ |
| `LoginIT.loginToNotDefaultUrl` | ✔ | – | – | ✔ (Escape Hatch: `LoginOverlay`) |
| `LoginIT.openLoginAfterLoggedIn` | ✔ | ✔ | ✔ | ✔ |
| `DashboardViewIT` ×2 | ✔ | – | – | ✔ (Escape Hatch: LitTemplate) |
| `ProductsViewIT` ×3 | ✔ | – | – | ✔ (Escape Hatch: SearchBar template, Crud) |
| `StorefrontViewIT.testDialogs`, `testTextFieldValidation` | ✔ | – | – | ✔ (Escape Hatch: LitTemplate) |
| `StorefrontViewIT.editOrder` | `@Disabled` | `@Disabled` | `@Disabled` | `@Disabled` |
| `UsersViewIT.updatePassword`, `testCancelConfirmationMessage` | ✔ | – | – | ✔ (Escape Hatch: SearchBar template, Crud, Email/PasswordField) |
| `UsersViewIT.tryToUpdateLockedEntity`, `tryToDeleteLockedEntity` | ✔ | – | ✔ | ✔ (custom Wrappers) |
| `UsersViewIT.accessDenied` | ✔ | ✔ | ✔ | ✔ |
| **Tests run** | **16** | **2** | **5** | **16** |

**5 of 16 tests run without TestBench** (Playwright), and 2 run without a browser at all. 11 stay on the TestBench
Escape Hatch, each with a one-line reason. 9 of those 11 are blocked only by LitTemplate `@Id` lookups (F9) and missing
Core Set Wrappers (F13), not by anything in the tests.

### Speed and stability

| | TestBench (before) | Unified on Selenium | Unified on Playwright | Unified on Browserless |
|---|---|---|---|---|
| Wall time, whole `mvn verify -Pit` | 85 s / 38 s / 89 s | 26.3 / 26.7 / 27.0 s | 16.7 / 16.6 / 17.7 s | 12.7 / 12.1 / 14.2 s |
| Runs green | **2 of 3** (5 × `SessionNotCreated`: Chrome failed to start in parallel) | 3 of 3 | 3 of 3 | 3 of 3 |
| Browser | a local, visible Chrome per test, tests in parallel | one headless Chrome per run, a fresh tab per test | one headless Chromium per run | none |
| Server | `spring-boot:start` before failsafe, port 8080 | in the test JVM, random port, one Spring context | same | same |
| Sign-in | through the login form in 15 of 16 tests | `@WithMockUser` (test-only endpoint) in 14, the form in 2 (the login tests) | same | security context set directly |

The Selenium run is **about 30 % faster than the best TestBench run and 3× faster than the worst**, and it wasn't flaky.
That comes from reusing one headless browser, signing in without the form, and having no separate application start.
The tests themselves still do the same work through the same TestBench page objects. The Browserless and
Playwright runs cover fewer tests, so their times aren't comparable.

### Code

| | TestBench (before) | Unified (after) |
|---|---|---|
| Test classes (`AbstractIT` + 5 ITs) | 527 lines | 633 lines (+20 %: `@DisplayName`s, Escape Hatch reasons, imports) |
| Page objects (`testbench/elements`) | 417 lines, 18 files | 345 lines, 15 files (3 deleted, the rest still needed) |
| Custom Wrappers | – | 65 lines, 3 files (`EmailField`, `PasswordField`, `Crud`) |
| Style rules test | – | 15 lines (passes) |
| JUnit `Assertions.*` in tests | 52 | 41 |
| Retrying `assertThat(..)` (Unified) | 0 | 20 |
| `TestBenchEscapeHatch.of(page)` | – | 8 |
| `// Escape Hatch kept: <reason>` | – | 9 |
| `@RequiresTestingTool(SELENIUM)` / `@RequiresBrowser` | – | 7 / 3 |
| Test dependencies | `vaadin-testbench-junit5` | `unified-test-bom` + `-spring`, `-browserless`, `-playwright`, `-selenium`, `-archunit`; `browserless-test-spring`; `spring-boot-starter-security-test`; still `vaadin-testbench-junit6` for the page objects |

One fully converted test, before and after:

```java
// TestBench
@BrowserTest
public void accessDenied() {
    StorefrontViewElement storefront = openLoginView().login("barista@vaadin.com", "barista");
    Assertions.assertEquals(3, storefront.getMenu().$(TabElement.class).all().size());

    getDriver().get(APP_URL + "users");
    TestBenchElement link404 = $("vaadin-app-layout > div > a").waitForFirst();

    Assertions.assertEquals("Go to the front page.", link404.getText());
}

// Unified: runs on Browserless, Playwright and Selenium
@Test
@DisplayName("A barista sees no admin views in the menu and gets a not-found page at the users view")
@WithMockUser(username = "barista@vaadin.com", roles = "barista")
public void accessDenied(UnifiedPage page) {
    page.navigate("storefront");
    assertThat(page.find(UnifiedTabs.class)).hasTabs("Storefront", "Dashboard", "Logout");

    page.navigate("users");

    assertThat(page.findComponent(RouterLink.class).withAccessibleName("Go to the front page.")).isVisible();
}
```

and its report (`user-steps.txt`, Selenium):

```text
✔ UsersViewIT › A barista sees no admin views in the menu and gets a not-found page at the users view   selenium · 0.4 s
   1  open “storefront”
   2  see tabs with tabs “Storefront”, “Dashboard”, “Logout”
   3  open “users”
   4  see router link “Go to the front page.”
```

The Unified test checks the tab *names*, not just their count, and finds the link by what the user reads instead
of a CSS path.

### Reports and failures

| | TestBench | Unified |
|---|---|---|
| Per test | surefire stack trace | `user-steps.txt`/`.json`; on failure: screenshot (browsers) or component tree, and `<test>.view.txt` |
| Per run | failsafe summary | `summary.md`, `summary.json`, `report.html`, "scenarios without a check", cross-tool comparison |
| Failure message | `NoSuchElementException: No element with tag <div> found with the attributes [part='increase-button']` | `Failed at User Step 2 of 2: select tab “Logout” in tabs / Expected exactly one tabs / Found none / Waited 10 s / Code LoginIT.java:42`, with the server's error under the step it happened in (`⚠ server ERROR … Couldn't parse 'orderID' …`) |

The server-log-under-the-step line found the cause of F10 in one read. The TestBench suite had no equivalent.

### The migration itself

| Phase | Time (wall clock) | Who |
|---|---|---|
| Baseline, 3 runs | 4 min | agent |
| Dry run | 1 min | agent |
| Recipe run | < 1 min | agent |
| Making the recipe's output compile and run (F3–F6) | 3 min | agent |
| `/resolve-migration-markers` (probes, conversion, 3-tool runs) | 14 min | user started it, agent followed it |
| Custom Wrappers, style rules, stability runs | 7 min | agent |
| Cleanup, production-profile check | 2 min | agent |
| **Total** | **≈ 37 min** (14:24 → 15:01) plus writing these notes | one manual step (typing the skill command) |

What the tooling did, in proportion:
- **The recipe** did the mechanical part: build, annotations, `UnifiedPage` parameters, `@BeforeEach` inlining.
  But its output **didn't compile** (F3, F4), and its 8 markers covered almost none of the real work, because
  that sat in the page objects (F5).
- **The marker skill** gave a clear, usable procedure and good reference tables. Its proof step was hollow here
  (F14), and its outcome then failed the style rules (F18).
- **Most of the effort** went into finding out what the Unified API can't see in this app: LitTemplates (F9),
  missing Wrappers (F13), the menu pattern (F12), and Selenium's route handling (F10). `page.describe()` was the
  best tool for that: one print per view answered "can I find it?" on each Testing Tool.

### Verdict

For the Bakery, alpha4 gives **a faster and stabler Selenium suite with much better reports right away**, even
where tests still run through TestBench page objects. It gives **real multi-tool coverage for less than a third of
the tests** (5 of 16 on Playwright, 2 of 16 on Browserless). The ceiling isn't the tests: it's alpha4's blindness to
`@Id`-mapped LitTemplate components (F9) and the missing `Crud`/`ConfirmDialog`/`EmailField`/`PasswordField`/
`LoginOverlay` Wrappers (F13). With those two fixed, most of the remaining 11 Selenium-only tests could be
converted the way `accessDenied` and the locked-entity tests were. The recipe needs F3/F4/F5 fixed before it can be
called a migration rather than a starting point on a suite built on `TestBenchElement` page objects.

18 findings in [findings.md](findings.md): 5 High (F3, F4, F5, F9, F10), 5 Medium (F6, F11–F14), 8 Low.
