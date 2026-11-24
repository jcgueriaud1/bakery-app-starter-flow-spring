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
