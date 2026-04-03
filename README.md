# type-tris

Typing + Tetris-style game — **CS2212 Group 26**, Winter 2026.

## User interface

The app runs as a **Java Swing desktop UI** (`typetris.ui.UserInterface`), launched from `typetris.App`. There is **no** HTML/CSS frontend in this tree.

## Build & run (Java)

From the repository root (where `pom.xml` is):

```bash
mvn compile exec:java
```

Or:

```bash
mvn
```

(`compile` runs first so classes exist before `exec:java`.)

Requires **JDK 21+** (see `maven.compiler.release` in `pom.xml`) and **Maven**.

Account data and stats are stored in **`data/type-tris.db`** (SQLite).

## Optional HTTP API

`typetris.backend.WebServer` can still be used as a small embedded HTTP API (e.g. for testing), but **`App` does not start it** — the default entry point is Swing only.
