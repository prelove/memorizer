# Repository Guidelines

## Project Structure & Module Organization
Primary application code lives in `src/main/java/com/memorizer`. Keep UI entry points and system wiring inside the `app` package (`MainApp`, `AppContext`, `TrayManager`), persistence helpers under `db`, spaced-repetition logic in `srs`, and import utilities within `importer`. Shared helpers belong in `util`. Database migrations are versioned SQL scripts in `src/main/resources/db/migration` and are loaded automatically at startup. Runtime configuration files (`application.properties`, `logback.xml`) sit in `src/main/resources`. The `data/` directory stores the embedded H2 database files; the shaded build output appears under `target/`.

## Build, Test, and Development Commands
Run `mvn clean package` to compile sources and create the shaded desktop jar. Execute `mvn verify` before submitting changes; it compiles, packages, and runs the full test suite. During rapid iteration you can use `mvn clean install -DskipTests` when tests are unaffected. Launch the application locally with `java -jar target/memorizer-0.1.0-SNAPSHOT-shaded.jar` once the package goal has executed.

## Coding Style & Naming Conventions
This project targets Java 8 with four-space indentation and Unix line endings. Adhere to the existing package hierarchy rooted in `com.memorizer`, naming classes in UpperCamelCase and methods/fields in lowerCamelCase. Favor null-safe patterns (`Optional`, guard clauses) and centralize configuration in `Config` or `application.properties`. Use SLF4J for logging (`private static final Logger log = LoggerFactory.getLogger(...)`) and avoid `System.out` or ad-hoc logging frameworks.

## Testing Guidelines
Place unit and integration tests in `src/test/java`, mirroring the production package structure so Maven Surefire detects them. Adopt JUnit 5 (`org.junit.jupiter`) and Mockito for behavioral seams; declare dependencies with `<scope>test</scope>` in `pom.xml`. Name test classes with the `*Test` suffix and use descriptive method names such as `shouldScheduleNextReview`. Always run `mvn test` (or `mvn verify` for a full check) and cover new code paths involving repositories and SRS scheduling.

## Commit & Pull Request Guidelines
Commits should remain focused and follow the existing conventional style (`feat:`, `bugfix:`, `chore:`) observed in the history. Reference related issues in the body and explain user-facing impact when applicable. Every pull request needs a concise summary, a list of validation commands (for example `mvn verify`, manual launch notes), and screenshots or logs for UI/runtime changes. Highlight schema or migration adjustments so reviewers can refresh their local H2 database if required.

## Data & Configuration Tips
Local development uses the embedded H2 database stored in `data/memo.*`; remove those files to start with a clean slate. Ensure new Flyway scripts increment the version prefix (`V###__description.sql`) and remain idempotent. Update `logback.xml` only when logging behavior must change, and keep default levels at `INFO` to avoid noisy tray notifications.
