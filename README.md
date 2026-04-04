# type-tris

Typing + Tetris-style game — **CS2212 Group 26**, Winter 2026.

## User interface

The app runs as a **Java Swing desktop UI** (`typetris.ui.UserInterface`), launched from `typetris.App`.

## Build & run (Java)

From the repository root (where `pom.xml` is):

```bash
mvn compile
mvn test
```

Requires **JDK 23+** and Maven.

## Optional HTTP API

`typetris.backend.WebServer` can still be used as a small embedded HTTP API (e.g. for testing), but **`App` does not start it** and the default entry point is Swing only.
