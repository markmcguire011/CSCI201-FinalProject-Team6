/*
 * access.js: shows guests and unverified users what they need to do before using a button (section 4).
 * Needs toast.js, api.js and nav.js. Include it after nav.js:
 *   <script src="js/access.js"></script>
 *
 * Mark any button that needs a login with the action it performs:
 *   <button data-requires="MESSAGE">Message Seller</button>
 *   <button data-requires="SAVE">Save</button>
 * Actions: MESSAGE, SAVE, SELL, REVIEW, REPORT, EDIT_OWN, ADMIN.
 *
 * When the page loads, each marked button is checked against what GET api/me says the user can do:
 *   - Guests: the button says "Log in to continue" and opens the login page, which returns to this page.
 *   - Unverified users (Message, Save, Sell, Review): it says "Verify your email to continue" and opens the Verify page.
 *   - Everyone else: the button is left alone and works normally. Report works for unverified users too.
 *
 * For buttons added later (e.g. inside a listing pop-up), call Access.guardAll(popupElement), or guard one
 * button with Access.guard(button, "MESSAGE"). To just ask: if (await Access.can("SAVE")) { ... }
 *
 * This only changes what the page shows. The server checks every request again (AuthFilter).
 */
const Access = (() => {
  const LOGIN_TEXT = "Log in to continue";
  const VERIFY_TEXT = "Verify your email to continue";
  const VERIFY_PAGE = "verify.html";

  /** The login page, with ?next= set to the page the user is on, so they come back here after logging in. */
  function loginUrl() {
    const page = location.pathname.split("/").pop() || Nav.PAGES.browse;
    return Nav.PAGES.login + "?next=" + encodeURIComponent(page + location.search + location.hash);
  }

  /** True if the logged-in user may do the action. Guests can do nothing but browse. */
  async function can(action) {
    const me = await Nav.ready;
    return Boolean(me && me.can && me.can[action]);
  }

  /** Turns the button into a link to the login or Verify page. */
  function replace(button, text, url) {
    button.textContent = text;
    button.disabled = false;
    button.removeAttribute("aria-pressed");
    button.dataset.blocked = "true";
    // Capture phase, so this runs before the button's normal click handler and stops it.
    button.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopImmediatePropagation();
      location.href = url;
    }, true);
  }

  /** Checks one button. Returns true if the user may use it, false if it was replaced. */
  async function guard(button, action) {
    const me = await Nav.ready;
    if (me && me.can && me.can[action]) return true;
    if (button.dataset.blocked === "true") return false;   // already replaced

    if (!me) {
      replace(button, LOGIN_TEXT, loginUrl());
    } else if (!me.verified && action !== "REPORT" && action !== "ADMIN") {
      replace(button, VERIFY_TEXT, VERIFY_PAGE);
    } else {
      button.disabled = true;   // e.g. a suspended account: the server would refuse it anyway
    }
    return false;
  }

  /** Checks every element marked data-requires inside root (the whole page by default). */
  function guardAll(root = document) {
    const marked = Array.from(root.querySelectorAll("[data-requires]"));
    return Promise.all(marked.map((button) => guard(button, button.dataset.requires)));
  }

  document.addEventListener("DOMContentLoaded", () => guardAll());

  return { can, guard, guardAll, loginUrl };
})();
