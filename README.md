# type-tris

Typing + Tetris-style game — **CS2212 Group 26**, Winter 2026.

## User interface

- **Tailwind (recommended for the design):** open [`web/index.html`](web/index.html) in a browser; see [`web/README.md`](web/README.md).
- **Java:** Maven project (`pom.xml`). The `typetris.App` entry point can be used for backend-only runs or demos; the visual menu is maintained in **`web/`**.

## Build (Java)

```bash
mvn compile
mvn test
```

Requires **JDK 23+** and Maven.

## Docs

- [`docs/SWING_VS_HTML.md`](docs/SWING_VS_HTML.md) — why the browser UI matches the design better than Swing alone.
