# Type-tris web UI (Tailwind)

The **primary menu and screen flow** for Type-tris live here as **HTML + Tailwind (CDN)** so the interface matches your design in the browser.

## View locally

**Option A — open the file**

- Double-click `index.html`, or drag it into Chrome / Firefox / Edge.  
- **Note:** Tailwind and fonts load from the CDN; you need **internet** the first time.

**Option B — local server (recommended)**

From the repo root:

```bash
cd web
python3 -m http.server 8080
```

Then visit [http://localhost:8080](http://localhost:8080).

## Project layout

| File | Purpose |
|---|---|
| `index.html` | Main menu |
| `login.html` | Login screen |
| `highscores.html` | High scores page |
| `instructions.html` | Instructions page |
| `parental.html` | Parental/teacher controls page |

Navigation now uses normal links between pages (multi-page setup), which is easier to scale for many screens.

## Course note

If your CS2212 contract requires a **Java Swing** GUI, confirm with your instructor before relying on this web UI only. You can keep Swing for marking and use this folder as the “real” design, or serve this UI from a small embedded server—team decision.
