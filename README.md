# ⚠️ This starter is not recommended ⚠️

**This starter is not recommended for new Vaadin Flow applications. Instead, visit [start.vaadin.com](https://start.vaadin.com/) to configure and download a Vaadin project that reflects the current best practices.**


# Running the Project in Development Mode

`mvn spring-boot:run` or just `mvn`

Wait for the application to start

Open http://localhost:8080/ to view the application.

Default credentials are admin@vaadin.com/admin for admin access and
barista@vaadin.com/barista for normal user access.

Note that when running in development mode, the application will not work in IE11.

# Running Integration Tests and Linter

The UI tests (`src/test/java/.../testbench/*IT.java`) are [Unified UI Tests](https://github.com/parttio/unified-testing-tool):
each test starts the application itself (`@UnifiedSpringBootTest`) and runs on the Testing Tool you choose. They are in
a separate profile:

```sh
mvn verify -Pit                                        # Browserless (no browser): the tests that need none
mvn verify -Pit -Dvaadin.testing.tool=playwright       # headless Chromium, downloaded on the first run
mvn verify -Pit -Dvaadin.testing.tool=selenium         # headless Chrome through TestBench
```

Most tests still use TestBench page objects (`testbench/elements`) and run on Selenium only
(`@RequiresTestingTool(SELENIUM)`), which needs a valid TestBench license. Each run writes a report to
`target/surefire-reports/unified-evidence/` (`summary.md`, `report.html`, and the User Steps of every test).

How the tests were migrated from TestBench: [MIGRATION_LOG.md](MIGRATION_LOG.md), [findings.md](findings.md) and the
[migration PR](https://github.com/jcgueriaud1/bakery-app-starter-flow-spring/pull/1).

# Automatic Restart and Live Reload

1. Vaadin Dev Tools interacts `spring-boot-devtools` and is able to automatically reload the browser when code is changed.

2. Optionally you might want to avoid the data generator to be run on each single reload, therefore, make H2 database store entities in file-system instead of in memory by adding the following lines to the `src/main/resources/application.properties`
```properties
spring.datasource.url=jdbc:h2:file:~/bakery-test-data
spring.jpa.hibernate.ddl-auto=update
```


# Running the Project in Production Mode

`mvn spring-boot:run -Pproduction`

The default mode when the application is built or started is 'development'. The 'production' mode is turned on by enabling the `production` profile when building or starting the app.

# Running in Eclipse or IntelliJ
As both IDEs support running Spring Boot applications you just have to import the project and select `com.vaadin.starter.bakery.Application` as main class if not done automatically. Using an IDE will also allow you to speed up development even more. Just check https://vaadin.com/blog/developing-without-server-restarts.

# Running Scalability Tests

*NOTE* SCALABILITY TESTS ARE BROKEN

The Bakery App Starter includes scalability tests. Once you have deployed a production build of Bakery you can run them to check how the app behaves under load. The scalability tests can be run completely on your local machine, but you might as well want to run locally only the test agents while the Bakery app under test is deployed to an environment that is close to your production.

In order to run the scalability tests locally:

1. Make sure you are using Java 8 (Gatling Maven plugin does not yet work with Java 9+)

1. Build and start Bakery in the production mode (e.g. ```mvn clean spring-boot:run -DskipTests -Pproduction```)

1. Open terminal in the project root

1. Start a test from the command line:

    ```sh
    mvn -Pscalability gatling:test
    ```

1. Test results are stored into target folder (e.g. to ```target/gatling/BaristaFlow-1487784042461/index.html```)

1. By default the scalability test starts 100 user sessions at a 100 ms interval for one repeat, all of which connect to a locally running Bakery app. These defaults can be overridden with the `gatling.sessionCount`, `gatling.sessionStartInterval` `gatling.sessionRepeats`, and `gatling.baseUrl` system properties. See an example execution for 300 users started within 50 s:

    ```sh
    mvn -Pscalability gatling:test -Dgatling.sessionCount=300 -Dgatling.sessionStartInterval=50
    ```

Note: If you run Bakery with an in-memory database (like H2, which is the default), it will logically use more memory than when using an external database (like PostgreSQL). It is recommend to run scalability tests for Bakery only after you have configured it to use an external database.

# License
This is free and unencumbered software released into the public domain.

For full terms, see LICENSE

*NOTE* This project uses licensed components listed in the next section, thus licenses for those components are required during development.

## Pro components
Pro components used in the starter are :
 - [Vaadin Crud](https://vaadin.com/components/vaadin-crud)
 - [Vaadin Charts](https://vaadin.com/components/vaadin-charts)
 - [Vaadin Confirm Dialog](https://vaadin.com/components/vaadin-confirm-dialog) 

 Also the tests are created using [Testbench](https://vaadin.com/testbench) library.
