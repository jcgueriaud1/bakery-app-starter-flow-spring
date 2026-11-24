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
