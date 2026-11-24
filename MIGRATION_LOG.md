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
