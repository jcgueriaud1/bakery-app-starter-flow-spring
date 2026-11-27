# TestBench vs Unified Testing Tool 0.1.0-alpha5 on Bakery: did it help?

This compares the Bakery UI tests before the migration (TestBench with page objects, commit `e927b3c`) and after it
(Unified Testing Tool 0.1.0-alpha5, branch `unified-testing-tool-alpha5`). It looks at how easy the tests were to
write and how fast they run. The numbers come from [MIGRATION_LOG.md](MIGRATION_LOG.md), and the F-numbers refer to
[findings.md](findings.md). Statements marked *(judgment)* are my assessment, not measurements.

## Summary

**The Unified Testing Tool helped:**
- **Less code.** The test code shrank by about a quarter, and every page object is gone.
- **Tests read like the user's actions.** They find fields by their label and say what the user does.
- **The tests start the app themselves.** No server has to be started by the build.
- **More reliable checks.** The checks keep retrying until they pass or time out, and no run was flaky.
- **Better reports.** Every test produces a readable list of steps.
- **A fast run with no browser.** Four tests run without a browser; that run of the whole build takes 17 s.

**What held it back on this app:**
- **Browserless covers little.** Only 4 of the 17 tests can run without a browser. Flow `Crud`, the login form, and
  content drawn by renderers inside grid cells still need one.
- **No faster in a browser.** On Selenium and Playwright the suite takes about as long as the TestBench suite did.
- **The recipe left most of the work.** It did not convert the page objects, so I rewrote every test class.
- **Some bugs cost real time.** A Playwright race saved wrong data (F10), and a Browserless lookup crash hid behind a
  timeout (F13).

## Scorecard

| | TestBench (before) | Unified alpha5 (after) | Better? |
|---|---|---|---|
| Test code | 944 lines: 6 classes and 16 page objects | 703 lines: 9 classes and no page objects | **Unified** |
| How tests find components | Methods on page objects, `$(…)`, element ids | Label, text, Wrapper type, and `findTemplate(…)` with `withId` | **Unified** *(judgment)* |
| Starting the app | Maven `spring-boot:start` on port 8080, with a hard-coded `APP_URL` | `@UnifiedSpringBootTest` on a random port | **Unified** |
| Signing in | Through the login form in every test | `@WithUserDetails`, except for the tests that check the login itself | **Unified** |
| Checks | JUnit `assert*`, which runs once and does not wait | 50 retrying `assertThat`, 1 JUnit `assert` | **Unified** |
| Tool to run on | Selenium/Chrome only | Browserless, Playwright or Selenium, set with a property | **Unified** |
| Tests that run without a browser | 0 | 4 of 17 | **Unified**, but few tests |
| Tests that run without TestBench | 0 | 15 (Playwright) | **Unified** |
| Whole `mvn verify -Pit` | 43–51 s | Browserless 17–18 s, Playwright 37–38 s, Selenium 40–41 s | **Unified** without a browser; about the same in one |
| Time spent in the test classes | ~20 s, classes run in parallel | ~30 s on Selenium, summed over the classes | **TestBench** |
| Flaky runs | 0 in 3 runs | 0 in 2 rounds × 3 tools | Same |
| Failure messages and reports | Stack trace and screenshot | Step list, `Found`/`Nearby`/`Did you mean`, `summary.md`, `report.html` | **Unified** |
| Looking at the page while writing a test | Browser devtools, then a page object | `page.describe()` (the Findable View) | **Unified**, with gaps (F13, F15) |
| Reaching shadow-DOM HTML in a template | `$` looks inside shadow roots | Not possible; needs an Escape Hatch (F7) | **TestBench** |
| Flow `Crud` | `CrudElement` | No Wrapper, and does not work on Browserless (F9) | **TestBench** |
| Clicking content drawn in a grid cell | `getCell(…)` | A different Escape Hatch per tool (`GridCellTasks`) | **TestBench** |
| Window size | Resized to 1024×800 | No setting for it (F16) | **TestBench** |
| Checking part of the URL | `getCurrentUrl()` | `hasPath` only compares the whole path; check removed (F11) | **TestBench** |

## Where it helped

### 1. One test, three tools

Each test is written once and runs on whichever tool `-Dvaadin.testing.tool` names. A tool a test cannot use is
named in an annotation:

- `@RequiresBrowser` on 11 tests: 4 in `LoginIT`, 3 in `ProductsViewIT`, and 4 in `UsersViewIT`.
- `@RequiresTestingTool(SELENIUM)` on 1 test.

The tests stay the same across tools.

- **Browserless:**
  - Runs 4 tests: dashboard counters, storefront dialogs, field validation, and access denied.
  - They take about 0.5 s together once Spring is up. Of the 17–18 s build, about 8 s is starting the Spring context.
  - That makes it a useful check before every commit, though it covers only part of the suite.
- **Playwright:**
  - Runs 15 tests with no TestBench and no TestBench license.
  - Before the migration, every test needed TestBench.

### 2. Less code, and the code reads better

- All 16 page objects are gone. The test code went from 944 to 703 lines, which is −24 %.
- Lookups use what the user sees. Compare one test:

  ```java
  // TestBench: three page objects are needed to read this
  OrderEditorElement orderEditor = storefrontPage.getOrderEditor();
  TextFieldElement customerNameField = orderEditor.getCustomerNameField();
  testFieldOverflow(customerNameField);

  // Unified: says which field it uses
  UnifiedTextField customerName = orderEditor(page).find(UnifiedTextField.class).withLabel("Customer");
  assertRejectsOverflow(customerName);
  ```

- **Templates:** `findTemplate(View.class)` with `withId("…")` reaches the `@Id` fields of all 7 Lit templates, and
  templates nested inside other templates. That works on all three tools. The alpha4 attempt could not do this, and
  it was the biggest gain for Bakery.
- **Login and confirm dialog:** the Core Set already has Wrappers for these and for the email and password fields, so
  I wrote no custom Wrappers. The alpha4 attempt needed 3.

### 3. Less setup

- `@UnifiedSpringBootTest` starts the app inside the test on a random port. The build no longer starts and stops
  the app, and the base URL is no longer hard-coded.
- `AbstractIT` went from a TestBench base class to two annotations and a static block.
- `@WithUserDetails("admin@vaadin.com")` signs in through the app's own `UserDetailsService`. Only the tests that check
  the login itself still go through the login form.

### 4. Checks that wait

`assertThat(…)` retries until the check passes or times out. Unified actions also wait for the server before they
return. I removed every `Assertions.assertNotNull(element)` and hand-written wait. All runs were green: 2 rounds on
each of the 3 tools, plus the runs of single classes.

### 5. Reports and help while writing

- **Reports:**
  - Each test writes `user-steps.txt`, a numbered list of what it did in plain words.
  - `page.step("…")` groups steps under a name.
  - Each run also writes `summary.md` and `report.html`.
  - A reviewer can check that a test does what its `@DisplayName` says without reading the code *(judgment)*.
- **Failures:** a failing lookup lists what it `Found`, what is `Nearby`, and `Did you mean`. That is quicker to act
  on than a `NoSuchElementException`.
- **Findable View:** `page.describe()` prints what can be found on the page and how. It is how I wrote each test from
  the view's code without going back to the page objects.
- **Style rules:** `UnifiedStyleRules.ALL`, checked with ArchUnit, catches tool-specific code that has no annotation
  or branch for its tool. It found 3 violations in my first version.

### 6. Migration tooling

- **Recipe:**
  - Changed `pom.xml`, the annotations and the test method signatures.
  - Added the two agent skills.
  - Produced 30 Migration Markers and only 1 compile error.
- **Total effort:** about 47 minutes of agent time from the dry run to the last green run, with no manual step.

## Where it was incomplete or less efficient

### Writing tests

1. **The recipe did the setup changes, and I did the rest by hand (F1, F3).**
   - Bakery keeps all its test logic in 16 TestBench page objects. The recipe leaves those alone, does not mark the
     statements that call them, and the dry run does not list them.
   - So the recipe's output ran only on Selenium, and "resolving the markers" meant rewriting all five test classes.
   - For a suite built on page objects, plan for a rewrite.
2. **Flow `Crud` (F9).**
   - There is no `UnifiedCrud`.
   - On Browserless, the Crud's buttons have no text and clicking them does nothing.
   - That puts 7 of the 17 tests in a browser, and every Crud test needs `.visible()` on its button and dialog lookups
     (F14).
3. **Content drawn by renderers inside grid cells.**
   - The Crud's edit pencil and the storefront's order cards are drawn by a `LitRenderer` and are not Flow components.
   - The Unified API can't reach them, so `GridCellTasks` clicks them with two Escape Hatches: Dramafinder on
     Playwright, TestBench on Selenium.
   - On Browserless, the test navigates to the order's route instead.
   - That is three pieces of code for one click. TestBench needed one: `getCell(…)`.
4. **Shadow-DOM HTML in templates (F7).**
   - `checkRowsCount` counts `<vaadin-board-row>` elements written in the template's HTML.
   - `findDescendants` does not look inside shadow roots, so this test runs on Selenium only, through a TestBench
     Escape Hatch.
   - TestBench's `$` looked inside shadow roots by default.
5. **Small API gaps that cost checks.**
   - The old test checked that the URL gained the item's id when the editor opened. With no prefix match and no way to
     read the current path, I removed that check (F11).
   - The 1024×800 window size was dropped because there is no setting for it (F16).
6. **Bakery's login form needs a browser.**
   - It posts to Spring Security's `/login`, which Browserless can't do, so `LoginIT` (4 tests) needs a browser.
   - That is expected, but it means Browserless can't test the login.
7. **More rounds of exploring the page.**
   - Inside templates, the Findable View shows `@Id` buttons as `button (no name)  not unique: 5 alike` instead of
     suggesting `withId(…)` (F15).
   - It shows nested templates only one level at a time.
   - On Browserless it leaves out every link (F13).

### Running tests

1. **No faster in a browser.**
   - With Selenium the whole build takes 40–41 s, against 43–51 s for TestBench. The saving comes from no longer
     starting the app separately.
   - Time spent in the test classes went from ~20 s, with TestBench running classes in parallel, to ~30 s summed over
     the classes.
   - Playwright, at 37–38 s, is not faster than Selenium here.
   - The checks also do more: there are 2.5× more retrying checks than in alpha4, and each editor test waits for the
     loaded value before typing.
2. **Browserless covers only 4 of the 17 tests.**
   - It is very fast, but only for a quarter of the suite.
   - Full coverage still needs a Selenium run: one test needs TestBench, and that run needs a TestBench license.
   - Running more than one tool in CI multiplies the cost.
3. **Bugs that are hard to diagnose:**
   - **F10 (Playwright).** After an Escape Hatch click, the next `setValue` does not wait for the server. The old value
     overwrote the typed text and the test saved the wrong data. It fails only on Playwright, and it disappears when
     snapshots are on. I found and worked around it, but it costs time to diagnose.
   - **F13 (Browserless).** `page.find(UnifiedLink.class)` throws on any page that has a Flow `Text`. Inside an
     assertion the exception is swallowed: the check fails after the 10 s timeout and the message doesn't show the
     exception.
   - **F6 (reports).** The password is hidden in every report wherever its text appears. With `barista`/`barista`, the
     user name and the `@DisplayName` came out as `••••`.
4. **Build profiles that changed meaning (F17).**
   - `it-native` now tests the JVM app instead of the native image.
   - `it-saucelabs` passes settings for a remote grid that the Unified Selenium adapter doesn't read.
   - Neither profile is marked or documented as changed.

## What would close the gap

| Gap | Effect on Bakery | Upstream |
|---|---|---|
| Browserless link crash (F13) | Link lookups fail on almost every view | [#603](https://github.com/parttio/unified-testing-tool/issues/603) |
| Playwright race after an Escape Hatch action (F10) | The wrong data was saved | [#604](https://github.com/parttio/unified-testing-tool/issues/604) |
| Password masking (F6) | Reports were mangled | [#605](https://github.com/parttio/unified-testing-tool/issues/605) |
| Recipe ignores page objects (F1, F3, F4, F5, F18) | Every test class was rewritten by hand | [#606](https://github.com/parttio/unified-testing-tool/issues/606) |
| `row.cell(int)` and renderer-drawn cell content on Browserless (F9) | Would remove `GridCellTasks` and could bring the Crud tests closer to Browserless | [#608](https://github.com/parttio/unified-testing-tool/issues/608) |
| Template shadow DOM and Findable View hints (F7, F15) | 1 test is Selenium-only, and exploring took extra rounds | [#609](https://github.com/parttio/unified-testing-tool/issues/609) |
| Window size (F16) | The window size setting was dropped | #489, #496 |

If #608 also covered Crud, up to 7 more tests could run on Browserless. That would make Browserless cover most of the
suite rather than a quarter of it.

## Conclusion

For Bakery, the Unified Testing Tool is better for **writing and reading** the tests: less code, no page objects,
tests that say what the user does, retrying checks, and useful reports. It is about the same for **running** them in a
browser, and better only on the small part that runs without one. Its weak spots are Flow `Crud` and content drawn by
renderers in grid cells, which are a large part of this app. Bakery also used page objects for everything, so the
migration became a rewrite. That rewrite took under an hour with the agent skills.

*(judgment)* A workable setup for this project:
- **Every build:** run on Browserless for fast feedback.
- **CI:**
  - Run on Playwright, which has no TestBench license cost and covers 15 tests.
  - Run on Selenium only for `checkRowsCount`, until F7 is solved or that check is dropped.
