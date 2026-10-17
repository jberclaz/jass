# jass — Agent Instructions

## Build & Test
- **Build + test:** `mvn package`
- **Build only (skip tests):** `mvn package -DskipTests`
- **Build only (skip test compilation too):** `mvn -Dmaven.test.skip=true package`
- **Single test class:** `mvn test -Dtest=FunctionalTest`
- **Update deps:** `mvn versions:use-latest-releases`
- **Release:** `mvn release:prepare` then `mvn release:perform`

## Architecture
- **Java 21** Maven project (targets `release 21`). CI uses **JDK 11** (actions/setup-java@v1); tests run on both.
- **3 packages:** `com.leflat.jass.common` (shared model), `client` (Swing GUI), `server` (game logic + AI).
- **Fat JAR** via maven-shade-plugin. Main class in manifest: `com.leflat.jass.client.JassClient`.
- **Server entry point** (not in manifest): `java -cp target/jass-*.jar com.leflat.jass.server.JassServer`. Server listens on **port 23107** (hardcoded).
- **Branch is `master`** (not `main`).

## Testing
- Mix of **JUnit 4** (`junit:junit`) and **JUnit 5** (`junit-jupiter`). Both coexist.
- JaCoCo coverage reports generated during `test` phase.
- Integration tests in `com.leflat.jass.server`; functional test at `src/test/java/FunctionalTest.java`.
- CLI test tools live in **`com.leflat.jass.test`** (main source, not test source): `AiTester`, `TokenExtractor`, `AutomaticClientTesting`.

## AI / ML (ONNX)
- Java runs inference via `com.microsoft.onnxruntime:onnxruntime`.
- Default model loaded from classpath: `cp:/model/jassformer.onnx` (packaged inside JAR).
- **Python training pipeline** is in `python/model/`. It is independent — not built by Maven.
- **Token vocabulary must match exactly** between `python/model/rl/tokens.py` and Java's `Tokens.java`. Any mismatch causes silent AI misbehavior.
- ONNX export uses **opset 18**; ensure Java onnxruntime version supports it.

## UI
- Swing-based with IntelliJ GUI Designer (`forms_rt` dependency).
- Two UI styles: `ModernUi` and `OriginalUi`.
- `log4j.properties` is excluded from the JAR.
