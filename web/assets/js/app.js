/* Shared page behavior (kept out of HTML). */
(function () {
  "use strict";

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
      showMessage(msg, "Login successful.", false);
    });
  }

  function init() {
    wireExitButtons();
    wireRegisterForm();
    wireLoginForm();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
