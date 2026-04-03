(function () {
  "use strict";

  function showMessage(el, text, isError) {
    if (!el) {
      return;
    }
    el.textContent = text;
    el.classList.remove("hidden");
    el.classList.toggle("text-red-400", !!isError);
  }

  function accuracyToPercent(avgAccuracy) {
    // Design doc: avgAccuracy is a ratio in [0.0, 1.0]. If backend ever stores 0-100, handle it.
    var value = Number(avgAccuracy);
    if (!isFinite(value)) {
      return "--";
    }
    var pct = value > 1 ? value : value * 100;
    return pct.toFixed(1) + "%";
  }

  function renderLeaderboardTable(container, leaderboard) {
    if (!container) {
      return;
    }
    container.innerHTML = "";

    if (!leaderboard || leaderboard.length === 0) {
      container.innerHTML = '<div class="text-on-surface-variant p-3">No players yet.</div>';
      return;
    }

    var table = document.createElement("table");
    table.className =
      "min-w-full text-left border-separate border-spacing-0 border border-outline-variant/30 bg-surface-container-lowest/40";

    var thead = document.createElement("thead");
    thead.innerHTML =
      "<tr class='bg-surface-container-high'>" +
      "<th class='p-3 text-xs uppercase tracking-widest text-on-surface-variant'>Rank</th>" +
      "<th class='p-3 text-xs uppercase tracking-widest text-on-surface-variant'>Username</th>" +
      "<th class='p-3 text-xs uppercase tracking-widest text-on-surface-variant'>High Score</th>" +
      "<th class='p-3 text-xs uppercase tracking-widest text-on-surface-variant'>Best WPM</th>" +
      "<th class='p-3 text-xs uppercase tracking-widest text-on-surface-variant'>Avg Accuracy</th>" +
      "</tr>";
    table.appendChild(thead);

    var tbody = document.createElement("tbody");
    leaderboard.forEach(function (row) {
      var tr = document.createElement("tr");
      tr.className = "border-t border-outline-variant/30";
      tr.innerHTML =
        "<td class='p-3 font-headline font-bold text-primary'>" + row.rank + "</td>" +
        "<td class='p-3 text-on-surface-variant'>" + row.username + "</td>" +
        "<td class='p-3 text-on-surface-variant'>" + row.highScore + "</td>" +
        "<td class='p-3 text-on-surface-variant'>" + row.bestWPM + "</td>" +
        "<td class='p-3 text-on-surface-variant'>" + accuracyToPercent(row.avgAccuracy) + "</td>";
      tbody.appendChild(tr);
    });

    table.appendChild(tbody);
    container.appendChild(table);
  }

  function init() {
    var messageEl = document.getElementById("dashboard-message");
    var logoutButton = document.getElementById("logout-button");
    var username;

    try {
      username = window.localStorage.getItem("typeTrisUsername");
    } catch (e) {
      username = null;
    }

    if (logoutButton) {
      logoutButton.addEventListener("click", function () {
        try {
          window.localStorage.removeItem("typeTrisUsername");
        } catch (e) {
          // ignore
        }
        window.location.href = "login.html";
      });
    }

    // CardLayout manager inside the UserInterface class.
    function UserInterface() {
      var self = this;
      this.cards = {
        "personal-stats": document.getElementById("card-personal-stats"),
        "leaderboard": document.getElementById("card-leaderboard")
      };

      this.tabs = document.querySelectorAll("[data-card]");

      // Swap which "card" is visible.
      this.cardLayout = new (function CardLayoutManager(cardsMap) {
        var cards = cardsMap;
        this.show = function (cardId) {
          Object.keys(cards).forEach(function (id) {
            var el = cards[id];
            if (!el) return;
            if (id === cardId) {
              el.classList.remove("hidden");
            } else {
              el.classList.add("hidden");
            }
          });

          self.tabs.forEach(function (btn) {
            if (!btn) return;
            var targetId = btn.getAttribute("data-card");
            var isActive = targetId === cardId;
            btn.classList.toggle("border-primary/0", !isActive);
            btn.classList.toggle("bg-primary-container/30", isActive);
            btn.classList.toggle("bg-primary-container/10", !isActive);
          });
        };
      })(this.cards);

      this.showCard = function (cardId) {
        if (!this.cards[cardId]) {
          return;
        }
        this.cardLayout.show(cardId);
      };

      this.wireTabs = function () {
        self.tabs.forEach(function (btn) {
          btn.addEventListener("click", function () {
            self.showCard(btn.getAttribute("data-card"));
          });
        });
      };

      this.setDefaultActiveTab = function () {
        var params = new URLSearchParams(window.location.search);
        var view = params.get("view");
        if (view && self.cards[view]) {
          self.showCard(view);
          return;
        }
        self.showCard("personal-stats");
      };

      this.loadPersonalStats = async function () {
        if (!username) {
          showMessage(messageEl, "Login required to view personal statistics.", true);
          return;
        }
        var res = await fetch(
          "/api/personal-stats?username=" + encodeURIComponent(username)
        );
        var data = {};
        try {
          data = await res.json();
        } catch (e) {
          data = {};
        }
        if (!res.ok || !data.ok) {
          showMessage(messageEl, (data && data.error) ? data.error : "Failed to load personal stats.", true);
          return;
        }

        var player = data.player || {};
        var bestWpmEl = document.getElementById("bestWpm-label");
        var avgAccEl = document.getElementById("avgAccuracy-label");
        if (bestWpmEl) bestWpmEl.textContent = (player.bestWPM ?? 0).toString();
        if (avgAccEl) avgAccEl.textContent = accuracyToPercent(player.avgAccuracy);
      };

      this.loadLeaderboard = async function () {
        var loadingEl = document.getElementById("leaderboard-loading");
        var wrapEl = document.getElementById("leaderboard-table-wrap");
        if (loadingEl) loadingEl.classList.remove("hidden");
        if (wrapEl) wrapEl.innerHTML = "";

        var res = await fetch("/api/leaderboard?limit=10");
        var data = {};
        try {
          data = await res.json();
        } catch (e) {
          data = {};
        }

        if (loadingEl) loadingEl.classList.add("hidden");
        if (!res.ok || !data.ok) {
          showMessage(messageEl, "Failed to load leaderboard.", true);
          return;
        }

        renderLeaderboardTable(wrapEl, data.leaderboard);
      };

      this.init = async function () {
        self.wireTabs();
        self.setDefaultActiveTab();

        // Load data in parallel; card switching is independent.
        await Promise.all([self.loadPersonalStats(), self.loadLeaderboard()]);
      };
    }

    var ui = new UserInterface();
    ui.init();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();

