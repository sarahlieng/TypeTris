/* Shared page behavior (kept out of HTML). */
(function () {
  "use strict";

  function difficultyProgressStorageKey() {
    var u = sessionStorage.getItem("typetrisPlayerUsername");
    if (!u) {
      return null;
    }
    u = String(u).trim();
    if (!u) {
      return null;
    }
    return "typetrisTierMaxClear:" + encodeURIComponent(u);
  }

  function tierMaxClearedMap() {
    var key = difficultyProgressStorageKey();
    if (!key) {
      return {};
    }
    try {
      var raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : {};
    } catch (e) {
      return {};
    }
  }

  function typetrisIsSubLevelUnlocked(tier, subLevel) {
    var t = String(tier || "easy").toLowerCase();
    var n = Math.max(1, Math.min(3, Number(subLevel) || 1));
    if (n <= 1) {
      return true;
    }
    if (!difficultyProgressStorageKey()) {
      return false;
    }
    var maxC = Number(tierMaxClearedMap()[t]) || 0;
    return maxC >= n - 1;
  }

  function typetrisRecordSubLevelClear(tier, subLevel) {
    var key = difficultyProgressStorageKey();
    if (!key) {
      return;
    }
    var t = String(tier || "easy").toLowerCase();
    var lv = Math.max(1, Math.min(3, Number(subLevel) || 1));
    var map = tierMaxClearedMap();
    map[t] = Math.max(Number(map[t]) || 0, lv);
    try {
      localStorage.setItem(key, JSON.stringify(map));
    } catch (e) {
      /* ignore quota / private mode */
    }
  }

  window.typetrisDifficultyProgress = {
    recordClear: typetrisRecordSubLevelClear,
    isUnlocked: typetrisIsSubLevelUnlocked
  };

  function showMessage(element, text, isError) {
    if (!element) {
      return;
    }
    element.textContent = text;
    element.classList.remove("hidden", "text-primary", "text-secondary", "text-red-400");
    element.classList.add(isError ? "text-red-400" : "text-primary");
  }

  async function postJson(url, data) {
    var response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data)
    });
    var payload = {};
    try {
      payload = await response.json();
    } catch (err) {
      payload = {};
    }
    return { ok: response.ok, status: response.status, payload: payload };
  }

  async function getJson(url) {
    var response = await fetch(url);
    var payload = {};
    try {
      payload = await response.json();
    } catch (err) {
      payload = {};
    }
    return { ok: response.ok, status: response.status, payload: payload };
  }

  function wireExitButtons() {
    var buttons = document.querySelectorAll("[data-exit='app']");
    buttons.forEach(function (button) {
      button.addEventListener("click", function () {
        if (confirm("Exit TYPE-TRIS?")) {
          window.close();
        }
      });
    });
  }

  function wireRegisterForm() {
    var submitButton = document.getElementById("register-submit");
    if (!submitButton) {
      return;
    }
    var msg = document.getElementById("register-message");
    submitButton.addEventListener("click", async function (event) {
      event.preventDefault();
      var email = document.getElementById("register-email").value.trim();
      var username = document.getElementById("register-username").value.trim();
      var password = document.getElementById("register-password").value;
      var confirmPassword = document.getElementById("register-confirm-password").value;

      if (password !== confirmPassword) {
        showMessage(msg, "Passwords do not match.", true);
        return;
      }

      var result = await postJson("/api/register", {
        email: email,
        username: username,
        password: password,
        confirmPassword: confirmPassword
      });

      if (!result.ok) {
        showMessage(msg, result.payload.error || "Registration failed.", true);
        return;
      }
      showMessage(msg, "Account created. Redirecting to login...", false);
      setTimeout(function () {
        window.location.href = "login.html";
      }, 1000);
    });
  }

  function wireLoginForm() {
    var form = document.getElementById("login-form");
    if (!form) {
      return;
    }
    var msg = document.getElementById("login-message");
    var modal = document.getElementById("login-failed-modal");
    var closeButton = document.getElementById("login-failed-close");
    var overlay = document.getElementById("login-failed-overlay");
    var modalText = document.getElementById("login-failed-text");

    function closeModal() {
      if (!modal) {
        return;
      }
      modal.classList.add("hidden");
      modal.setAttribute("aria-hidden", "true");
    }

    function openModal(text) {
      if (!modal) {
        showMessage(msg, text || "Invalid username or password.", true);
        return;
      }
      if (modalText && text) {
        modalText.textContent = text;
      }
      modal.classList.remove("hidden");
      modal.setAttribute("aria-hidden", "false");
    }

    if (closeButton) {
      closeButton.addEventListener("click", function () {
        closeModal();
      });
    }
    if (overlay) {
      overlay.addEventListener("click", function () {
        closeModal();
      });
    }
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape") {
        closeModal();
      }
    });

    form.addEventListener("submit", async function (event) {
      event.preventDefault();
      var username = document.getElementById("login-username").value.trim();
      var password = document.getElementById("login-password").value;

      var result = await postJson("/api/login", {
        username: username,
        password: password
      });

      if (!result.ok) {
        openModal(result.payload.error || "The username or password you entered is incorrect. Please try again.");
        return;
      }
      sessionStorage.setItem("typetrisPlayerUsername", username);
      window.location.href = "player-menu.html";
    });
  }

  function wirePlayerMenu() {
    var usernameEl = document.getElementById("player-menu-username");
    if (!usernameEl) {
      return;
    }
    var name = sessionStorage.getItem("typetrisPlayerUsername");
    if (!name) {
      window.location.href = "login.html";
      return;
    }
    usernameEl.textContent = name;

    var saveBtn = document.getElementById("player-menu-save");
    if (saveBtn) {
      saveBtn.addEventListener("click", function () {
        window.alert("Save game is not implemented yet.");
      });
    }

    var logoutBtn = document.getElementById("player-menu-logout");
    if (logoutBtn) {
      logoutBtn.addEventListener("click", function () {
        sessionStorage.removeItem("typetrisPlayerUsername");
        window.location.href = "index.html";
      });
    }
  }

  function wireDifficultyScreen() {
    var root = document.getElementById("difficulty-screen");
    if (!root) {
      return;
    }
    if (!sessionStorage.getItem("typetrisPlayerUsername")) {
      window.location.href = "login.html";
      return;
    }

    var buttons = root.querySelectorAll(".difficulty-level-btn");
    buttons.forEach(function (btn) {
      var tier = btn.getAttribute("data-tier") || "";
      var levelNum = Number(btn.getAttribute("data-level")) || 1;
      if (!typetrisIsSubLevelUnlocked(tier, levelNum)) {
        btn.disabled = true;
        btn.classList.add("opacity-45", "cursor-not-allowed", "pointer-events-none");
        btn.setAttribute("aria-disabled", "true");
        btn.title = "Finish " + tier.toUpperCase() + " level " + (levelNum - 1) + " first.";
      }
      btn.addEventListener("click", function () {
        if (btn.disabled) {
          return;
        }
        if (!typetrisIsSubLevelUnlocked(tier, levelNum)) {
          return;
        }
        sessionStorage.setItem(
          "typetrisDifficulty",
          JSON.stringify({ tier: tier, level: levelNum })
        );
        window.location.href = "gameplay.html";
      });
    });
  }

  function wireParentalLogin() {
    var form = document.getElementById("parental-login-form");
    if (!form) {
      return;
    }
    var pinInput = document.getElementById("parental-pin");
    var msg = document.getElementById("parental-message");

    form.addEventListener("submit", async function (event) {
      event.preventDefault();
      var pin = pinInput ? pinInput.value.trim() : "";
      if (!pin) {
        showMessage(msg, "Admin PIN is required.", true);
        return;
      }

      var result = await postJson("/api/parent/sessions", { pin: pin });
      if (!result.ok) {
        showMessage(msg, result.payload.error || "Failed to unlock parental controls.", true);
        return;
      }

      sessionStorage.setItem("typetrisAdminPin", pin);
      window.location.href = "parent-dashboard.html";
    });
  }

  function wireParentDashboard() {
    var dashboard = document.getElementById("parent-dashboard");
    if (!dashboard) {
      return;
    }
    var pin = sessionStorage.getItem("typetrisAdminPin");
    if (!pin) {
      window.location.href = "parental.html";
      return;
    }

    var msg = document.getElementById("parent-dashboard-message");
    var tbody = document.getElementById("parent-dashboard-sessions-body");
    var list = document.getElementById("parent-dashboard-sessions-list");
    var logout = document.getElementById("parent-logout");
    var createButton = document.getElementById("parent-create-account");

    function renderAccountRows(accounts) {
      if (!list) {
        return;
      }
      list.innerHTML = "";
      if (!Array.isArray(accounts) || accounts.length === 0) {
        var emptyCard = document.createElement("div");
        emptyCard.className = "p-5 bg-surface-container-low text-on-surface-variant";
        emptyCard.textContent = "No student accounts yet.";
        list.appendChild(emptyCard);
        return;
      }
      accounts.forEach(function (account, index) {
        var row = document.createElement("div");
        row.className = "flex flex-col md:flex-row md:items-center justify-between p-5 bg-surface-container-low hover:bg-surface-container transition-colors" +
          (index < accounts.length - 1 ? " border-b border-outline-variant/10" : "");
        row.innerHTML =
          "<span class='text-lg font-headline font-medium text-on-surface mb-4 md:mb-0'>" + account.username + "</span>" +
          "<div class='flex flex-wrap gap-3'>" +
          "<button type='button' data-action='view-stats' data-username='" + account.username + "' class='px-4 py-2 text-[10px] font-label font-bold tracking-widest uppercase border border-outline hover:border-primary hover:text-primary transition-all'>VIEW STATS</button>" +
          "<button type='button' data-action='reset-password' data-username='" + account.username + "' class='px-4 py-2 text-[10px] font-label font-bold tracking-widest uppercase border border-outline hover:border-primary hover:text-primary transition-all'>RESET PASSWORD</button>" +
          "<button type='button' data-action='delete-account' data-username='" + account.username + "' class='px-4 py-2 text-[10px] font-label font-bold tracking-widest uppercase border border-secondary/40 text-secondary hover:bg-secondary/10 transition-all'>DELETE</button>" +
          "</div>";
        list.appendChild(row);
      });
    }

    function loadAccounts() {
      return postJson("/api/parent/accounts/list", { pin: pin }).then(function (result) {
        if (!result.ok) {
          throw new Error(result.payload.error || "Failed to load accounts.");
        }
        var accounts = Array.isArray(result.payload.accounts) ? result.payload.accounts : [];
        renderAccountRows(accounts);
        return accounts;
      });
    }

    if (logout) {
      logout.addEventListener("click", function () {
        sessionStorage.removeItem("typetrisAdminPin");
        window.location.href = "parental.html";
      });
    }

    if (list) {
      list.addEventListener("click", function (event) {
        var button = event.target.closest("button[data-action]");
        if (!button) {
          return;
        }
        var action = button.getAttribute("data-action");
        var username = button.getAttribute("data-username");
        if (!username) {
          return;
        }
        if (action === "view-stats") {
          postJson("/api/parent/accounts/list", { pin: pin }).then(function (result) {
            if (!result.ok) {
              showMessage(msg, result.payload.error || "Failed to load stats.", true);
              return;
            }
            var accounts = Array.isArray(result.payload.accounts) ? result.payload.accounts : [];
            var match = accounts.find(function (a) { return a.username === username; });
            if (!match) {
              showMessage(msg, "Account not found.", true);
              return;
            }
            alert(
              "Stats for " + username + "\n" +
              "Sessions: " + match.sessionCount + "\n" +
              "Last Session: " + (match.lastSessionAt || "Never") + "\n" +
              "Created: " + match.createdAt
            );
          });
          return;
        }
        if (action === "reset-password") {
          var newPassword = prompt("Enter new password for " + username + " (min 8 chars):");
          if (!newPassword) {
            return;
          }
          postJson("/api/parent/accounts/reset-password", {
            pin: pin,
            username: username,
            newPassword: newPassword
          }).then(function (result) {
            if (!result.ok) {
              showMessage(msg, result.payload.error || "Failed to reset password.", true);
              return;
            }
            showMessage(msg, "Password reset for " + username + ".", false);
          });
          return;
        }
        if (action === "delete-account") {
          if (!confirm("Delete account '" + username + "'?")) {
            return;
          }
          postJson("/api/parent/accounts/delete", {
            pin: pin,
            username: username
          }).then(function (result) {
            if (!result.ok) {
              showMessage(msg, result.payload.error || "Failed to delete account.", true);
              return;
            }
            showMessage(msg, "Deleted account " + username + ".", false);
            loadAccounts().catch(function (err) {
              showMessage(msg, err.message || "Failed to refresh accounts.", true);
            });
          });
        }
      });
    }

    if (createButton) {
      createButton.addEventListener("click", function () {
        var username = prompt("New student username:");
        if (!username) {
          return;
        }
        var email = prompt("Student email:");
        if (!email) {
          return;
        }
        var password = prompt("Temporary password (min 8 chars):");
        if (!password) {
          return;
        }
        postJson("/api/parent/accounts/create", {
          pin: pin,
          username: username.trim(),
          email: email.trim(),
          password: password
        }).then(function (result) {
          if (!result.ok) {
            showMessage(msg, result.payload.error || "Failed to create account.", true);
            return;
          }
          showMessage(msg, "Created account " + username + ".", false);
          loadAccounts().catch(function (err) {
            showMessage(msg, err.message || "Failed to refresh accounts.", true);
          });
        });
      });
    }

    postJson("/api/parent/sessions", { pin: pin }).then(function (result) {
      if (!result.ok) {
        showMessage(msg, result.payload.error || "Session expired. Please enter PIN again.", true);
        sessionStorage.removeItem("typetrisAdminPin");
        setTimeout(function () {
          window.location.href = "parental.html";
        }, 900);
        return;
      }
      showMessage(msg, "Unlocked. Account management ready.", false);
      if (!tbody && !list) {
        return;
      }
      if (tbody) {
        tbody.innerHTML = "";
      }
      if (list) {
        list.innerHTML = "";
      }
      loadAccounts().catch(function (err) {
        showMessage(msg, err.message || "Failed to load accounts.", true);
      });
    });
  }

  function wireHighscoresPage() {
    var root = document.getElementById("highscores-screen");
    if (!root) {
      return;
    }
    var tbody = document.getElementById("highscores-body");
    var msg = document.getElementById("highscores-message");
    getJson("/api/leaderboard?limit=15").then(function (result) {
      if (!result.ok || !result.payload || !result.payload.ok) {
        if (msg) {
          msg.textContent =
            "Could not load leaderboard. Run the game with mvn exec:java and open http://localhost:8080.";
          msg.classList.remove("hidden");
        }
        return;
      }
      var rows = result.payload.leaderboard;
      if (!tbody || !Array.isArray(rows)) {
        return;
      }
      tbody.innerHTML = "";
      if (rows.length === 0) {
        var empty = document.createElement("tr");
        var td = document.createElement("td");
        td.colSpan = 5;
        td.className = "py-8 px-4 text-center text-on-surface-variant font-body";
        td.textContent = "No players yet. Register an account and finish a run.";
        empty.appendChild(td);
        tbody.appendChild(empty);
        return;
      }
      rows.forEach(function (row) {
        var tr = document.createElement("tr");
        tr.className = "border-b border-outline-variant/15 hover:bg-surface-bright/30";
        var rank = document.createElement("td");
        rank.className = "py-3 px-2 font-headline text-primary";
        rank.textContent = String(row.rank);
        var name = document.createElement("td");
        name.className = "py-3 px-2 font-body font-medium";
        name.textContent = String(row.username || "");
        var hi = document.createElement("td");
        hi.className = "py-3 px-2 text-right tabular-nums";
        hi.textContent = String(row.highScore != null ? row.highScore : "");
        var wpm = document.createElement("td");
        wpm.className = "py-3 px-2 text-right tabular-nums text-secondary";
        wpm.textContent = String(row.bestWPM != null ? row.bestWPM : "");
        var acc = document.createElement("td");
        acc.className = "py-3 px-2 text-right tabular-nums text-on-surface-variant";
        var a = row.avgAccuracy;
        acc.textContent =
          a == null || isNaN(Number(a)) ? "—" : Math.round(Number(a)) + "%";
        tr.appendChild(rank);
        tr.appendChild(name);
        tr.appendChild(hi);
        tr.appendChild(wpm);
        tr.appendChild(acc);
        tbody.appendChild(tr);
      });
    });
  }

  function init() {
    wireExitButtons();
    wireRegisterForm();
    wireLoginForm();
    wirePlayerMenu();
    wireDifficultyScreen();
    wireHighscoresPage();
    wireParentalLogin();
    wireParentDashboard();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
