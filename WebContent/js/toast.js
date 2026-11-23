/*
 * toast.js: success and error notices in the lower right corner (section 16).
 * Each notice stays for about 4 seconds; clicking it closes it sooner.
 *
 *   Toast.success("Listing saved.");
 *   Toast.error("Use your USC email address (ending in usc.edu).");
 *   Toast.flash("Logged in. Messaging, saving and selling are unlocked.");   // shown on the next page
 *
 * Include on every page:  <script src="js/toast.js"></script>
 */
const Toast = (() => {
  const DURATION_MS = 4000;
  const FLASH_KEY = "uscm.flash";
  let stack = null;

  function container() {
    if (!stack) {
      stack = document.createElement("div");
      stack.className = "toast-stack";
      stack.setAttribute("role", "status");
      stack.setAttribute("aria-live", "polite");
      document.body.appendChild(stack);
    }
    return stack;
  }

  function show(message, type = "success") {
    const toast = document.createElement("div");
    toast.className = "toast toast-" + type;
    toast.textContent = message;   // textContent, never innerHTML, so user text cannot inject HTML
    toast.addEventListener("click", () => close(toast));
    container().appendChild(toast);
    requestAnimationFrame(() => toast.classList.add("toast-visible"));
    setTimeout(() => close(toast), DURATION_MS);
  }

  function close(toast) {
    if (!toast.isConnected) return;
    toast.classList.remove("toast-visible");
    setTimeout(() => toast.remove(), 250);
  }

  /** Saves a notice to show after the browser moves to another page (e.g. after logging in). */
  function flash(message, type = "success") {
    try {
      sessionStorage.setItem(FLASH_KEY, JSON.stringify({ message, type }));
    } catch (e) {
      // storage blocked: the notice is skipped, nothing else breaks
    }
  }

  function showFlash() {
    try {
      const saved = sessionStorage.getItem(FLASH_KEY);
      if (saved) {
        sessionStorage.removeItem(FLASH_KEY);
        const { message, type } = JSON.parse(saved);
        show(message, type);
      }
    } catch (e) {
      // ignore
    }
  }

  document.addEventListener("DOMContentLoaded", showFlash);

  return {
    show,
    flash,
    success: (message) => show(message, "success"),
    error: (message) => show(message, "error"),
  };
})();
