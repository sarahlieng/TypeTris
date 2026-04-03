/* Gameplay front-end (Stage 0)
 * - Initializes HUD values for the selected difficulty/level
 * - Wires pause overlay + typing input focus
 * - Does NOT implement word falling yet
 */
(function () {
  "use strict";

  function getSessionDifficulty() {
    var raw = sessionStorage.getItem("typetrisDifficulty");
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch (e) {
      return null;
    }
  }

  function byId(id) {
    return document.getElementById(id);
  }

  function setText(id, value) {
    var el = byId(id);
    if (el) el.textContent = String(value);
  }

  function setPaused(paused) {
    var overlay = byId("pause-overlay");
    var input = byId("typing-input");
    var pauseButton = byId("pause-button");

    if (paused) {
      if (overlay) overlay.classList.remove("hidden");
      if (input) {
        input.disabled = true;
        input.blur();
      }
      if (pauseButton) pauseButton.setAttribute("aria-pressed", "true");
    } else {
      if (overlay) overlay.classList.add("hidden");
      if (input) {
        input.disabled = false;
        input.focus();
      }
      if (pauseButton) pauseButton.setAttribute("aria-pressed", "false");
    }
  }

  function wire() {
    var difficulty = getSessionDifficulty();
    var username = sessionStorage.getItem("typetrisPlayerUsername");
    if (!username || !difficulty) {
      window.location.href = "player-menu.html";
      return;
    }

    // HUD init (no gameplay loop in Stage 0)
    var level = difficulty.level || 1;
    setText("hud-current-level", level);
    setText("hud-score", 0);
    setText("hud-best-score", 0);
    setText("hud-wpm", 0);
    setText("hud-lives", 3);
    setText("hud-lines-cleared", 0);
    setText("hud-streak", 0);
    setText("hud-combo-multiplier", "x1");
    setText("hud-accuracy", "100%");

    // Focus typing input
    var input = byId("typing-input");
    if (input) {
      input.value = "";
      input.disabled = false;
      input.focus();
    }

    // Pause overlay wiring
    var pauseButton = byId("pause-button");
    var resumeButton = byId("pause-resume");
    var overlay = byId("pause-overlay");

    var paused = false;
    setPaused(false);

    function togglePause() {
      paused = !paused;
      setPaused(paused);
    }

    if (pauseButton) {
      pauseButton.addEventListener("click", function () {
        togglePause();
      });
      pauseButton.setAttribute("aria-pressed", "false");
    }
    if (resumeButton) {
      resumeButton.addEventListener("click", function () {
        paused = false;
        setPaused(false);
      });
    }

    // Basic pause UX: ESC toggles pause
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape") {
        // If overlay isn't visible, treat as resume->pause toggle
        togglePause();
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", wire);
  } else {
    wire();
  }
})();

