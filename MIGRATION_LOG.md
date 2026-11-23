# Migration log: TestBench → Unified UI Tests 0.1.0-alpha4

Migration of the Bakery app's TestBench integration tests (`src/test/java/.../testbench`) to the
[Unified API for Vaadin UI tests](https://github.com/parttio/unified-testing-tool) `0.1.0-alpha4`, following
[Migrating from TestBench](https://parttio.github.io/unified-testing-tool/migrating-from-testbench/).

- Branch: `unified-testing-tool-alpha4`, from `v25` at `59c7c46` (Vaadin 25.3.5, Spring Boot 4.1.2).
- Environment: macOS (Darwin 25.6, arm64), OpenJDK 25.0.3, Maven, Chrome, cached Playwright Chromium.
- Issues with the tool: see [findings.md](findings.md). Before/after comparison: [at the end](#comparison).

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

