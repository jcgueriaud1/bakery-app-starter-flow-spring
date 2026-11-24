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

## F4: Recipe emits `testBench.getRunLocallyBrowser()`, which doesn't compile (recipe)

- **Step:** 2
- `if (getRunLocallyBrowser() == null)` in the old `@BeforeEach` became
  `testBench.getRunLocallyBrowser()` with an *Escape Hatch* marker; `TestBenchEscapeHatch` has no such method →
  compilation error. The only compile error of the run.
- **Suggestion:** either expose it on the Escape Hatch or treat it like `@RunLocally` (*“browser setup removed”* marker).

## F5: Login marker on a statement that doesn't sign in (recipe, minor)

- **Step:** 2
- `LoginIT.loginWorks`: `Assertions.assertEquals("Email", loginView.getUsernameLabel())` got
  *“signs in through the login form: … consider @WithMockUser”* – any statement calling a method on the login element is
  marked, not only the one that submits. Also, it's a `LoginIT`: every test there checks the login itself, so
  `@WithMockUser` advice is noise for that class.

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

