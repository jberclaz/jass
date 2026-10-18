# jass — Agent Instructions

## Build & Test
- **Build + test:** `mvn package`
- **Build only (skip tests):** `mvn package -DskipTests`
- **Build only (skip test compilation too):** `mvn -Dmaven.test.skip=true package`
- **Single test class:** `mvn test -Dtest=FunctionalTest`
- **Update deps:** `mvn versions:use-latest-releases`
- **Release:** `mvn release:prepare` then `mvn release:perform`

## Architecture
- **Java 21** Maven project (targets `release 21`), needs a full JDK (not just a JRE). CI uses Temurin JDK 21.
- **3 packages:** `com.leflat.jass.common` (shared model), `client` (Swing GUI), `server` (game logic + AI).
- **Fat JAR** via maven-shade-plugin. Main class in manifest: `com.leflat.jass.client.JassClient`.
- **Server entry point** (not in manifest): `java -cp target/jass-*.jar com.leflat.jass.server.JassServer`. Server listens on **port 23107** (hardcoded).
- **Branch is `master`** (not `main`).

## Testing
- **JUnit 5** (`junit-jupiter`) with Mockito.
- JaCoCo coverage reports generated during `test` phase.
- Integration tests in `com.leflat.jass.server`; functional test at `src/test/java/FunctionalTest.java`.
- CLI test tools live in **`com.leflat.jass.test`** (main source, not test source): `AiTester`, `TokenExtractor`, `AutomaticClientTesting`.
- Supervised training data: `java -cp target/jass-*.jar com.leflat.jass.test.TokenExtractor -o <dir> -g <games> -s <strength>` (one `<run>_seat<i>.sdat` file per seat, with Monte Carlo's score of every legal card; `--no-scores` for the older `.dat`; never overwrites).

## AI / ML (ONNX)
- Java runs inference via `com.microsoft.onnxruntime:onnxruntime`.
- Default model loaded from classpath: `cp:/model/jassformer.onnx`. It is **not** in the repo: without it (or an explicit model path), `TransformersStrategy` falls back to Monte Carlo.
- **Python training pipeline** is in `python/model/`. It is independent — not built by Maven. Versions are pinned in `python/model/requirements.txt`; run tests from `python/model/` with `python -m pytest`. Run the scripts from `python/model/` too; those in `rl/` as modules (e.g. `python -m rl.ppo --supervised-model <state_dict>`, `python -m rl.export_checkpoint_to_onnx --rl --checkpoint <pth>`). Win rate vs Monte Carlo or other models: `python -m rl.evaluate <checkpoints> [--opponents <models>] | --watch runs/<run>` (needs the jar). See `TRAIN.md`.
- **Token vocabulary must match exactly** between `python/model/rl/tokens.py` and Java's `Tokens.java`. Any mismatch causes silent AI misbehavior.
- ONNX export uses **opset 18**; ensure Java onnxruntime version supports it.

## UI
- Swing-based with IntelliJ GUI Designer (`forms_rt` dependency).
- Two UI styles: `ModernUi` and `OriginalUi`.
- `log4j.properties` is excluded from the JAR.
