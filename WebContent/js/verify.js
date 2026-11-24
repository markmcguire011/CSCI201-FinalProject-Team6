/*
 * verify.js: the Verify page (section 3, Verifying the email).
 *
 * Six boxes for the code, a Verify button, and a "Send a new code" link that greys out and counts
 * down from 60 seconds after each code is sent. The server enforces the same one-per-minute limit.
 */
(() => {
  const form = document.getElementById("verify-form");
  const boxes = Array.from(document.querySelectorAll(".code-box"));
  const verifyButton = document.getElementById("verify-button");
  const resendButton = document.getElementById("resend-button");
  const errorNote = document.getElementById("verify-error");
  const intro = document.getElementById("verify-intro");
  let timer = null;

  function showError(message) {
    errorNote.textContent = message;
    errorNote.hidden = false;
  }

  function clearError() {
    errorNote.hidden = true;
    errorNote.textContent = "";
  }

  function typedCode() {
    return boxes.map((box) => box.value).join("");
  }

  /**
   * Spreads digits over the boxes starting at one box. The boxes have no maxlength on purpose: a whole
   * code can arrive in one box at once (pasting, or a phone's code autofill) and is spread out here.
   */
  function fill(startIndex, text) {
    const digits = text.replace(/\D/g, "").split("");
    let index = startIndex;
    digits.forEach((digit) => {
      if (index < boxes.length) boxes[index++].value = digit;
    });
    boxes[Math.min(index, boxes.length - 1)].focus();
  }

  boxes.forEach((box, index) => {
    box.addEventListener("input", () => {
      const text = box.value;
      box.value = "";
      fill(index, text);
      clearError();
    });
    box.addEventListener("paste", (event) => {
      event.preventDefault();
      fill(index, event.clipboardData.getData("text"));
      clearError();
    });
    box.addEventListener("keydown", (event) => {
      if (event.key === "Backspace" && box.value === "" && index > 0) {
        boxes[index - 1].value = "";
        boxes[index - 1].focus();
        event.preventDefault();
      } else if (event.key === "ArrowLeft" && index > 0) {
        boxes[index - 1].focus();
      } else if (event.key === "ArrowRight" && index < boxes.length - 1) {
        boxes[index + 1].focus();
      }
    });
    box.addEventListener("focus", () => box.select());
  });

  /** Greys out "Send a new code" and counts down; at 0 the link works again. */
  function startCountdown(seconds) {
    clearInterval(timer);
    let left = seconds;
    const draw = () => {
      if (left <= 0) {
        clearInterval(timer);
        resendButton.disabled = false;
        resendButton.textContent = "Send a new code";
      } else {
        resendButton.disabled = true;
        resendButton.textContent = "Send a new code (" + left + ")";
      }
    };
    draw();
    if (left > 0) {
      timer = setInterval(() => {
        left -= 1;
        draw();
      }, 1000);
    }
  }

  function finish() {
    Toast.flash("Email verified. Messaging, saving and selling are unlocked.");
    location.href = Nav.PAGES.browse;
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const code = typedCode();
    if (!/^\d{6}$/.test(code)) {
      showError("Enter all 6 digits of the code.");
      return;
    }
    clearError();
    verifyButton.disabled = true;
    verifyButton.textContent = "Verifying...";
    try {
      await Api.post("api/verify", { code }, { silent: true });
      finish();
    } catch (err) {
      showError(err.message);
      verifyButton.disabled = false;
      verifyButton.textContent = "Verify";
      if (err.status === 400) {
        boxes.forEach((box) => (box.value = ""));
        boxes[0].focus();
      }
    }
  });

  resendButton.addEventListener("click", async () => {
    clearError();
    resendButton.disabled = true;
    try {
      const status = await Api.post("api/verify/resend", undefined, { silent: true });
      Toast.success("We sent a new code. The old one no longer works.");
      boxes.forEach((box) => (box.value = ""));
      boxes[0].focus();
      startCountdown(status.secondsUntilResend);
    } catch (err) {
      showError(err.message);
      const wait = /Wait (\d+) seconds/.exec(err.message);
      if (wait) startCountdown(Number(wait[1]));
      else resendButton.disabled = false;
    }
  });

  document.addEventListener("DOMContentLoaded", async () => {
    try {
      const status = await Api.get("api/verify", { silent: true });
      if (status.verified) {
        location.href = Nav.PAGES.browse;
        return;
      }
      intro.textContent = "Enter the 6-digit code we emailed to " + status.email + ". It expires after 15 minutes.";
      startCountdown(status.secondsUntilResend);
      boxes[0].focus();
    } catch (err) {
      if (err.status === 401) {
        Toast.flash("Log in to verify your email.", "error");
        location.href = Nav.PAGES.login + "?next=verify.html";
      } else {
        showError(err.message);
      }
    }
  });
})();
