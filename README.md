# jass
![Java CI with Maven](https://github.com/jberclaz/jass/workflows/Build/badge.svg) ![Release](https://img.shields.io/github/v/release/jberclaz/jass)

Distributed [Jass](https://en.wikipedia.org/wiki/Jass) game for the members of the FLAT(r)

## How to compile?
Make sure `JAVA_HOME` is defined. Then:

`mvn package`

> [!NOTE]
> If you experience any issue with the unit tests, you can skip them with `mvn package -DskipTests` .

## How to run?
- Compile the code as described above. You'll obtain a `.jar` file in `./target/`.
- Launch the client with `java -jar target/jass-2.1.3-SNAPSHOT.jar`
- Launch the server with `java -cp target/jass-2.1.3-SNAPSHOT.jar com.leflat.jass.server.JassServer`

If you make the jar file executable, you can double-click on the file to launch the client.

## How to run on Windows
The game uses Java 11. Unfortunately, Oracle abandoned the JRE
starting from version 11. You will need to install the
[full JDK](https://www.oracle.com/java/technologies/javase-jdk14-downloads.html#license-lightbox).

### Classic UI
![Jass client screenshot](doc/screenjass.jpg)

### Modern UI
![Modern UI screenshot](doc/modern_ui.png)

## Development

- Compile and test: `mvn package`
- Compile without tests: `mvn -Dmaven.test.skip=true package`
- Update dependencies: `mvn versions:use-latest-releases`
- Create new release: `mvn release:prepare` and `mvn release:perform`
