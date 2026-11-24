# Migration log: TestBench → Unified Testing Tool 0.1.0-alpha5

Project: Bakery App Starter (Flow + Spring), branch `unified-testing-tool-alpha5`.
Tool: `org.vaadin.addons:unified-test-*:0.1.0-alpha5` (Maven Central / local repo), docs from the `unified-test-0.1.0-alpha5` tag.
Environment: macOS, JDK 25 (target 21), Maven, Chrome, TestBench license present. Vaadin 25.3.6, Spring Boot 4.1.3.

Each step below is one commit. Issues found along the way are in [findings.md](findings.md).
The `unified-testing-tool-alpha4` branch was deliberately **not** looked at until the final comparison.

## Step 0: Baseline (TestBench, before migration)

Command: `mvn -B verify -Pit` (Spring Boot started by the `it` profile on :8080, Failsafe, local Chrome).

| | Result |
|---|---|
| Unit tests (Surefire) | 15 run, 15 pass |
| UI tests (Failsafe, `*IT`) | 17 run, 16 pass, 1 skipped (`StorefrontViewIT.editOrder`, `@Disabled`) |
| Wall-clock, whole `mvn verify -Pit` | 73 s |
| Classes | `DashboardViewIT` (2), `LoginIT` (4), `ProductsViewIT` (3), `StorefrontViewIT` (3), `UsersViewIT` (5); TestBench runs classes in parallel (~8–20 s each) |

Test code: 6 test classes (incl. `AbstractIT`), 16 page objects / custom elements (`elements/ui`, `elements/components`), 944 lines.

## Step 1: Dry run (`FindTestClassesToMigrate`)

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.46.1:dryRun \
  -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-alpha5 \
  -Drewrite.activeRecipes=org.vaadin.addons.unified.rewrite.FindTestClassesToMigrate \
  -Drewrite.exportDatatables=true
```

10 s, no errors. Result (`FindTestClassesToMigrate$TestClasses.csv`):

| Class | Status | Page objects | Annotation |
|---|---|---|---|
| `AbstractIT` | migrates | – | `@UnifiedSpringBootTest` (option `springBootTest` decides) |
| `DashboardViewIT`, `LoginIT`, `ProductsViewIT`, `StorefrontViewIT`, `UsersViewIT` | migrates | – | – |

Every class is reported as `migrates`, and the `pageObjects` column is empty, although every test goes through
custom elements (`LoginViewElement extends LoginOverlayElement`, `OrderCardElement` `@Element("order-card")`, …),
which the docs say are listed and make a class `migrates partly` → [F1](findings.md#f1).

Notable for the next step: the app has 7 `LitTemplate`s (`storefront-view`, `dashboard-view`, `search-bar`,
`order-editor`, `order-details`, `order-item-editor`, `dashboard-counter-label`), and alpha5 is the first release
whose recipe enters Templates (`findTemplate`).

## Step 2: Run the recipe (`MigrateTestBenchToUnified`)

`rewrite.yml` with `addPlaywright: true`, `includePageObjects: true`, `addAgentSkill: true`; `rewrite-maven-plugin:6.46.1:run`. 14 s.
The output is committed **unchanged** (commit "Step 2: raw output…") so it can be diffed.

What it did:

- **pom.xml:** imports `unified-test-bom:0.1.0-alpha5`; adds `unified-test-spring`, `-browserless`, `-playwright`,
  `-selenium`, `browserless-test-spring`, `spring-boot-starter-security-test`; `vaadin-testbench-junit5` →
  `vaadin-testbench-junit6` (kept, since page objects stay TestBench); adds `vaadin.testing.evidence-dir` to all three
  Failsafe configurations (`it`, `it-saucelabs`, `it-native`). The `spring-boot:start/stop` executions are left alone (as documented).
- **Agent files:** `.claude/skills/resolve-migration-markers/SKILL.md`, `.claude/skills/write-unified-ui-tests/SKILL.md`, `AGENTS.md`.
- **Tests:** `AbstractIT` loses `extends BrowserTestBase`, gains `@UnifiedSpringBootTest`; `@BrowserTest` → `@Test` with a
  `UnifiedPage page` parameter; `@BeforeEach setup()` → explicit `setup(page)` at the start of every test; helpers take the page.
- **Page objects (16 classes): untouched**, despite `includePageObjects` – they are all custom elements (`@Element`,
  `extends LoginOverlayElement`, …), which the docs say stay TestBench.

Result in numbers:

| | |
|---|---|
| Tests | 17, **all 17 `@RequiresTestingTool(SELENIUM)`** – none runs on Browserless or Playwright |
| Migration Markers | 30: 18 *TestBench Escape Hatch*, 9 *signs in through the login form*, 2 *navigation to `APP_URL + …`*, 1 *tests now start the app themselves* |
| Unified API lookups (`page.find`) produced | 0 – every lookup in Bakery lives in a page object |
| Compiles? | No – 1 error (`TestBenchEscapeHatch.getRunLocallyBrowser()` doesn't exist) → [F4](findings.md#f4) |

Other observations → [F2](findings.md#f2) (hard-coded `:8080` base URL silently kept),
[F3](findings.md#f3) (no Migration Marker on any page object / custom element call),
[F5](findings.md#f5) (login marker on a line that doesn't sign in).

## Step 3: Compile and run (Selenium only)

Smallest change to compile and to target the right server:

- removed `APP_URL` (`http://localhost:8080/` or the site-local IP) and the `getRunLocallyBrowser()` branch; the
  Unified API owns the browser and the base URL;
- `openLoginView(page, driver, url)` → `openLoginView(page, path)` using `page.navigate(path)`;
- the two `getDriver().get(APP_URL + "...")` → `page.navigate("login")` / `page.navigate("users")`.

`mvn verify -Pit -Dvaadin.testing.tool=selenium`: **17 run, 16 pass, 1 skipped (`@Disabled`)**, 32 s wall-clock.
`summary.md`: *“14 passing scenarios end in Escape Hatch code, whose checks the report can't see”*.
On Browserless / Playwright every test is skipped (`@RequiresTestingTool(SELENIUM)`).

Note: the `it` profile still starts a second app on :8080 that nobody uses now (cleaned up in step 6).

## Step 4: Resolve the Migration Markers, rewrite the tests on the Unified API

The `resolve-migration-markers` skill can't be started by an agent (`disable-model-invocation: true`), so I read it
and `write-unified-ui-tests` and followed their steps by hand. Because the recipe left all the real code inside 16
TestBench page objects (custom elements, untouched), "resolving markers" here meant **rewriting each test class**
from the views' code, then deleting the page objects. One commit per class (4a–4e).

Approach per class: read the view, explore with `page.describe()` / `grid.describe()` on Browserless and Playwright,
write the test with `@DisplayName`, User Tasks (`page.step`) and retrying `assertThat`, run on all three tools.

| Class | Result | Runs on | Notes |
|---|---|---|---|
| `LoginIT` (4) | rewritten with `UnifiedLogin.login(..)`, `isAt(..)`, `UnifiedTabs.selectTab("Logout")` | Playwright, Selenium (`@RequiresBrowser`) | The login form posts to Spring Security's `/login`, which Browserless can't do; these tests check the login itself, so no `@WithMockUser`. Passed first try. → [F6](findings.md#f6) |
| `DashboardViewIT` (2) | `@WithUserDetails("admin@vaadin.com")`; counters via `findTemplate(DashboardView.class).findComponent(DashboardCounterLabel.class)` | counters: **all 3**; board rows: Selenium | Board rows exist only in the template's shadow DOM → TestBench Escape Hatch kept. → [F7](findings.md#f7), [F8](findings.md#f8) |
| `ProductsViewIT` (3) | Crud fields by label, grid rows by `withCellText`, `UnifiedConfirmDialog` | Playwright, Selenium (`@RequiresBrowser`) | Flow's `Crud` can't be driven on Browserless; its pencil needs a per-tool Escape Hatch (`GridCellTasks`). A Playwright-only race found and fixed. → [F9](findings.md#f9), [F10](findings.md#f10), [F11](findings.md#f11) |
| `UsersViewIT` (5) | as Products; `UnifiedEmailField`, `UnifiedPasswordField`, `UnifiedComboBox` | 4 Crud tests: browsers; `accessDenied`: **all 3** | Browserless crash on `UnifiedLink` lookups. → [F13](findings.md#f13), [F14](findings.md#f14) |
| `StorefrontViewIT` (3, 1 `@Disabled`) | nested templates: `findTemplate(StorefrontView).find(UnifiedDialog).findTemplate(OrderEditor)…`, `@Id` buttons by `withId` | **all 3** | Order cards are LitRenderer content: browsers click them through `GridCellTasks`; Browserless navigates to the route the card's `cardClick` opens (Escape Hatch kept, documented). → [F15](findings.md#f15) |

Other changes:

- `AbstractIT` is now only `@UnifiedSpringBootTest` plus the `NOTIFICATION_DURATION` tweak, one Spring context for all
  classes. The window resize to 1024×800 (Escape Hatch) is dropped: no setting exists for it and the tests pass at
  the default size → [F16](findings.md#f16).
- `CrudTasks` (Crud lookups / User Tasks) and `GridCellTasks` (the one Escape Hatch for renderer-drawn cell
  content, for Playwright via Dramafinder and Selenium via TestBench) are shared by the test classes.
- Deleted the 16 page objects in `testbench/elements` (nothing uses them).
- Checks changed on purpose:
  - `editProduct` no longer checks that the URL gets longer when the editor opens ([F11](findings.md#f11)).
  - The overflow checks expected *either* `maximum length is 255 characters` *or* `size must be between 0 and 255`.
    They now expect the first, which all three tools show.
  - `LoginIT.loginWorks` checks the "Email" field exists, not its label read from the TestBench element.
- `StorefrontViewIT.editOrder` stays `@Disabled` as before (disabled without a reason in #1431). Rewritten, it
  **passes on all three tools** when the condition is deactivated, so it could be re-enabled.
- Migration Markers left: **0**.

## Step 5: Wrappers for custom components (none needed)

The plan was to write Wrappers for the custom elements. After step 4 none is needed:

- Everything the tests reach that **is** a Flow component is covered by the Core Set, by `findTemplate(..)` for the
  7 Lit templates (`@Id` fields with `withId(..)`), or by `findComponent(DashboardCounterLabel.class)`.
- What's left (Crud's edit pencil, the storefront's `order-card`, the dashboard's `vaadin-board-row`) is markup drawn
  by a `LitRenderer` or written in a template's HTML: no Flow component, so a `@UnifiedComponent` Wrapper has
  nothing to bind to, and on Browserless it doesn't exist at all. These stay behind the two documented Escape
  Hatches (`GridCellTasks`, `DashboardViewIT.checkRowsCount`).

No commit for this step.

## Step 6: Build cleanup

- `it` profile: removed the `spring-boot:start`/`stop` executions (the tests start the app themselves now, as the
  recipe's marker said) and the `com.vaadin.testbench.Parameters.runLocally` property; documented how to choose the
  Testing Tool in a comment.
- Removed `vaadin-testbench-junit6`: the remaining TestBench Escape Hatches (`GridElement`, `$("…")`) compile with
  what `unified-test-selenium` brings.
- Removed `rewrite.yml` (one-off migration config; its content is in step 2).
- Kept the `*IT` names and Failsafe: `mvn test` stays a fast unit-test run, `mvn verify -Pit` runs the UI tests.
- **Left alone, needs a decision:** `it-saucelabs` (still starts the app with `spring-boot:start` and passes TestBench
  grid parameters, which the Unified Selenium adapter doesn't read) and `it-native` (meant to test a native executable
  started outside; `@UnifiedSpringBootTest` now starts the JVM app instead, so the profile no longer tests the native
  image). Both are probably broken by the move to `@UnifiedSpringBootTest` → [F17](findings.md#f17).

Sanity run `mvn verify -Pit` (Browserless): 17 UI tests, 4 run and pass, 13 skipped (browser-only or `@Disabled`).

## Step 7: Style rules and final runs

**Style rules:** added `unified-test-archunit` and `UnifiedStyleTest` (`UnifiedStyleRules.ALL` over
`com.vaadin.starter.bakery`). First run: 3 violations of *Testing Tool code needs its Capability*, all from
`StorefrontViewIT.openOrder` calling `unwrap(Grid.class)` in its Browserless branch. Switched that branch to
`tryUnwrap(Grid.class)`, which the rule accepts as a per-tool branch → **0 violations**. (The rules don't follow calls
into `CrudTasks` / `GridCellTasks`, as documented; those are only reached from `@RequiresBrowser` tests or through
per-tool branches.)

**Final runs** (`mvn -B verify -Pit [-Dvaadin.testing.tool=…]`, whole build incl. the Vaadin frontend build, same
machine; each run twice, both green). The baseline was re-timed now on the pre-migration commit for a fair comparison
(step 0's 73 s was a cold run).

| | Baseline TestBench | Unified · Browserless | Unified · Playwright | Unified · Selenium |
|---|---|---|---|---|
| UI tests passed / skipped | 16 / 1 | **4** / 13 | **15** / 2 | **16** / 1 |
| Wall-clock `mvn verify -Pit` (run 1 / run 2) | 51 s / 43 s | 17 s / 18 s | 38 s / 38 s | 41 s / 41 s |
| Of which in Failsafe (sum of classes) | ~20 s (classes in parallel) + app start by `spring-boot:start` | ~9 s (8 s is the Spring context) | ~29 s | ~30 s |
| Flaky in 2 runs | 0 | 0 | 0 | 0 |

The 4 Browserless tests: `DashboardViewIT.checkCounters`, `StorefrontViewIT.testDialogs`,
`StorefrontViewIT.testTextFieldValidation`, `UsersViewIT.accessDenied`. On Browserless they take about 0.5 s together
once the Spring context is up.

Skipped, and why:

| Test | Browserless | Playwright | Selenium | Reason |
|---|---|---|---|---|
| `LoginIT` ×4 | skip | ✔ | ✔ | `@RequiresBrowser`: the login form posts to Spring Security's `/login` |
| `ProductsViewIT` ×3, `UsersViewIT` ×4 | skip | ✔ | ✔ | `@RequiresBrowser`: Flow `Crud` ([F9](findings.md#f9)) |
| `DashboardViewIT.checkRowsCount` | skip | skip | ✔ | `@RequiresTestingTool(SELENIUM)`: shadow-DOM-only board rows ([F7](findings.md#f7)) |
| `StorefrontViewIT.editOrder` | skip | skip | skip | `@Disabled` since before the migration (passes on all 3 if enabled) |

Escape Hatches left: 2 places. `GridCellTasks.clickInCell` (Crud pencil and order card, browsers) plus its Browserless
twin in `StorefrontViewIT.openOrder`, and `DashboardViewIT.checkRowsCount`. Each has a comment saying why.

Code size: test code went from 944 lines (6 classes + 16 page objects) to 703 lines (5 test classes, `AbstractIT`, `CrudTasks`, `GridCellTasks`, `UnifiedStyleTest`; no page
objects): `git diff --stat e927b3c -- src/test` gives 26 files, +564 −807.

## Step 8: Comparison, alpha4 vs alpha5

Only now did I read the `unified-testing-tool-alpha4` branch (its `MIGRATION_LOG.md`, `findings.md` and final code).
I also ran its final suite once per tool on this machine, right after the alpha5 runs, for comparable times.

### Result

| | alpha4 branch (final) | **alpha5 branch (final)** |
|---|---|---|
| UI tests passing on **Browserless** | 2 | **4** |
| UI tests passing on **Playwright** | 5 | **15** |
| UI tests passing on **Selenium** | 16 | 16 |
| Tests that need TestBench (Selenium-only) | 11 | **1** (`checkRowsCount`, shadow-DOM layout check) |
| Tests that run on all 3 tools | 2 (`accessDenied`, `openLoginAfterLoggedIn`) | **4** (`checkCounters`, `testDialogs`, `testTextFieldValidation`, `accessDenied`) |
| `@Disabled editOrder` if enabled | not tried | passes on all 3 |
| TestBench page objects left | 15 files, 345 lines | **0** |
| Custom Wrappers written | 3 (`UnifiedCrud`, `UnifiedEmailField`, `UnifiedPasswordField`) | 0 (Email/Password/Login/ConfirmDialog are in the Core Set now) |
| `TestBenchEscapeHatch.of(..)` in tests | 8 | 2 (both in documented helpers) |
| JUnit `assert*` / retrying `assertThat(..)` | 37 / 20 | 1 / 50 |
| `@RequiresTestingTool(SELENIUM)` / `@RequiresBrowser` | 7 / 3 | 1 / 7 (class-level on Login, Products; per test on Users) |
| Sign-in | `@WithMockUser(username, roles)` | `@WithUserDetails` (the app's own `UserDetailsService`) |
| Style rules | pass | pass |

### Speed (same machine, same day, `mvn -B verify -Pit`, whole build)

| | TestBench baseline | alpha4 suite | alpha5 suite |
|---|---|---|---|
| Browserless | – | 33 s¹ (2 tests) | 17–18 s (4 tests) |
| Playwright | – | 20 s (5 tests) | 37–38 s (15 tests) |
| Selenium | 43–51 s (16 tests) | 30 s (16 tests) | 40–41 s (16 tests) |
| Sum of test-class times on Selenium | ~20 s per class in parallel | 22.7 s | 30.0 s |

¹ First build after switching branches: the alpha4 branch commits its own `package.json`/`vite.config.ts`, so the
frontend was rebuilt. Its 12–14 s in its own log is the steady value.

On Selenium the alpha5 suite is about 30% slower than alpha4's, though both run the same 16 tests. The time goes
into what alpha5's tests do on top: about 2.5× as many retrying checks, waiting for the editor to show the loaded
value before typing (the fix for F10), and opening order cards in the Storefront tests through the real grid instead
of page objects. Both Unified suites are faster than the TestBench baseline. The alpha5 suite was green in every run
(2 rounds × 3 tools + the per-class runs). alpha4 also reported its TestBench baseline as flaky (Chrome failing to
start in parallel); I didn't see that in my 3 baseline runs.

### The migration itself

| | alpha4 | alpha5 |
|---|---|---|
| Recipe output compiles? | No: 5 kinds of error (F3, F4 there), incl. removed element jars | **1 error** (`getRunLocallyBrowser()`) |
| Migration Markers from the recipe | 8 | 30 (login, navigation, Escape Hatches, the server switch) |
| Spring Boot app tested against `spring-boot:start` | silently `@UnifiedUITest`, no marker | `@UnifiedSpringBootTest` + a marker (ADR 0013); `APP_URL` driver navigation in the base class still unmarked (F2) |
| Page objects | not converted, not marked, tests not annotated | not converted, not marked, **but** every test gets `@RequiresTestingTool(SELENIUM)` |
| Evidence dir for Failsafe | under `surefire-reports` | own `failsafe-reports/unified-evidence` |
| `@WithMockUser` dependency | had to be added by hand | added by the recipe |
| Time, wall clock | ≈ 37 min + a manual skill start | ≈ 47 min (12:58 → 13:45), no manual step |
| Where the time went | making it compile, then finding out what the API can't see | exploring templates and Crud with `describe()`, then rewriting every class (the page objects held all the logic) |

### alpha4 findings, re-checked on alpha5

| alpha4 finding | Status in alpha5 |
|---|---|
| F1 dry run silent about page objects | **Still there** → alpha5 F1 |
| F3 build drops element jars still used | **Fixed** (keeps `vaadin-testbench-junit6`; now even more than needed, F18) |
| F4 output doesn't compile | **Mostly fixed**: 1 new error (F4) |
| F5 page objects neither converted nor marked | **Partly fixed**: tests now get `@RequiresTestingTool(SELENIUM)`, statements still get no marker (F3) |
| F6 external-server Spring Boot app silently `@UnifiedUITest` | **Fixed** (ADR 0013, marker). `APP_URL` in a helper still slips through (F2) |
| F7 pom rewrite interleaves test deps with runtime ones | **Still there**: `browserless-test-spring` was inserted at the top, among the runtime dependencies |
| F8 evidence under `surefire-reports`; root navigation reads `open “”` | **Fixed** (failsafe dir; reads `open “/”`). "Scenarios without a check" after migration still noisy until the markers are resolved |
| F9 nothing inside a LitTemplate can be found | **Fixed**: `findTemplate(..)`, nested, on all three tools. The biggest gain for Bakery |
| F10 Selenium route templates / `isAt` on `@RouteAlias` | **Not hit**: `isAt(StorefrontView.class)` on `/` passes on Selenium |
| F11/F12 `selectTab(..)` on a menu tab that navigates | **Fixed**: `selectTab("Dashboard"/"Users"/"Storefront"/"Logout")` works on all three tools |
| F13 Core Set gaps (Login, ConfirmDialog, Email, Password, Crud) | **Mostly fixed**: all but `Crud` (F9) |
| F14 skill proof runs 0 UI tests | **Fixed** in the skill text (Failsafe aware, "0 tests = failed proof") |
| F15 `withTimeout` loses the type | Fixed per release notes (not used here) |
| F16 security test dependency undocumented | **Fixed** (recipe adds it, guide documents it) |
| F17 `.visible()` on a confirm dialog differs PW vs Selenium | **Not hit**: `page.find(UnifiedConfirmDialog.class).visible().confirm()` passes on both |
| F18 migration output fails the style rules | Not comparable: I rewrote all code. My own first version had 3 violations, fixed with `tryUnwrap` |

New in alpha5 (not in alpha4's list): **F13** (Browserless `UnifiedLink` crash, a regression from alpha5's new tag
matching), **F10** (Playwright Escape Hatch race), **F6** (secret masking), **F7**/**F15** (template visibility
limits), **F17** (other Failsafe profiles).

### Verdict

alpha5 fixed exactly what limited the alpha4 result: LitTemplate `@Id` components (`findTemplate`) and the missing
Core Set Wrappers (Login, ConfirmDialog, Email/Password). On the same app, that took the suite from **5 to 15 tests
without TestBench** and **from 2 to 4 tests without a browser**, and let me delete every page object. The recipe is
close to compiling out of the box now (1 error instead of a list) and handles the "server started by the build" layout
properly.

What still caps Bakery:

1. **Flow `Crud`** (7 tests browser-only, plus a per-tool helper for the edit pencil): no Wrapper, no Browserless support.
2. **Renderer-drawn content in grid cells** (Crud pencil, order cards): unreachable from the Unified API; a
   `row.cell(int)` and a way to fire a `LitRenderer` function on Browserless would remove the last helper.
3. **The recipe still ignores `TestBenchElement` page objects**, so on a page-object suite the recipe does the
   plumbing and a person or agent rewrites every test.

Bugs to fix before beta: **F13** (Browserless link lookups crash on any `Text`, a regression), **F10** (Playwright
Escape Hatch race → silently wrong data) and **F6** (secret masking mangles reports).
