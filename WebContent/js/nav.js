/*
 * nav.js: draws the same navigation bar on every page (section 16).
 * Needs toast.js and api.js. Each page includes:
 *   <header id="site-nav"></header>
 *   <script src="js/toast.js"></script>
 *   <script src="js/api.js"></script>
 *   <script src="js/nav.js"></script>
 *
 * Bar: Browse, Sell, Messages (with the unread count), Saved, Profile (the user's initials),
 * Admin (administrators only), and Log In or Log Out.
 *
 * It asks GET api/me who is logged in. That servlet answers {id, fullName, initials, role, unreadMessages}
 * or 401 for a guest. Pages can wait for the same answer: const me = await Nav.ready;   (null for a guest)
 */
const Nav = (() => {
  // Page addresses. Change them here if a page has a different file name.
  const PAGES = {
    browse: "index.html",
    sell: "listing-form.html",
    messages: "messages.html",
    saved: "saved.html",
    profile: "profile.html",
    admin: "admin.html",
    login: "login.html",
  };
  const ME_URL = "api/me";
  const LOGOUT_URL = "api/logout";

  let unreadBadge = null;

  function link(page, label, extraClass) {
    const a = document.createElement("a");
    a.href = PAGES[page];
    a.textContent = label;
    a.className = "nav-link" + (extraClass ? " " + extraClass : "");
    const current = location.pathname.split("/").pop() || PAGES.browse;
    if (current === PAGES[page]) {
      a.classList.add("nav-active");
      a.setAttribute("aria-current", "page");
    }
    return a;
  }

  function render(me) {
    let header = document.getElementById("site-nav");
    if (!header) {
      header = document.createElement("header");
      header.id = "site-nav";
      document.body.prepend(header);
    }
    header.textContent = "";

    const nav = document.createElement("nav");
    nav.className = "nav";
    nav.setAttribute("aria-label", "Main");

    const brand = document.createElement("a");
    brand.href = PAGES.browse;
    brand.className = "nav-brand";
    brand.textContent = "USC Marketplace";
    nav.appendChild(brand);

    const links = document.createElement("div");
    links.className = "nav-links";
    links.appendChild(link("browse", "Browse"));
    links.appendChild(link("sell", "Sell"));

    const messages = link("messages", "Messages");
    unreadBadge = document.createElement("span");
    unreadBadge.className = "nav-badge";
    unreadBadge.hidden = true;
    messages.appendChild(unreadBadge);
    links.appendChild(messages);

    links.appendChild(link("saved", "Saved"));

    if (me) {
      if (me.role === "ADMIN") links.appendChild(link("admin", "Admin"));

      const profile = link("profile", me.initials || "Me", "nav-avatar");
      profile.title = me.fullName ? me.fullName + "'s profile" : "Profile";
      profile.setAttribute("aria-label", "Profile");
      links.appendChild(profile);

      const logout = document.createElement("button");
      logout.type = "button";
      logout.className = "nav-link nav-button";
      logout.textContent = "Log Out";
      logout.addEventListener("click", logOut);
      links.appendChild(logout);

      setUnread(me.unreadMessages || 0);
    } else {
      links.appendChild(link("login", "Log In", "nav-login"));
    }

    nav.appendChild(links);
    header.appendChild(nav);
  }

  /** Updates the number next to Messages; messaging code calls this when a new message arrives. */
  function setUnread(count) {
    if (!unreadBadge) return;
    unreadBadge.hidden = !count;
    unreadBadge.textContent = count > 99 ? "99+" : String(count);
    unreadBadge.setAttribute("aria-label", count + " unread messages");
  }

  async function logOut() {
    try {
      await Api.post(LOGOUT_URL);
      Toast.flash("Logged out.");
      location.href = PAGES.browse;
    } catch (e) {
      // api.js already showed the error
    }
  }

  async function loadUser() {
    try {
      return await Api.get(ME_URL, { silent: true });
    } catch (e) {
      if (e.status !== 401) Toast.error(e.message);
      return null;   // guest
    }
  }

  // Draw the guest bar right away so the page never jumps, then update it once the user is known.
  const ready = new Promise((resolve) => {
    document.addEventListener("DOMContentLoaded", async () => {
      render(null);
      const me = await loadUser();
      if (me) render(me);
      resolve(me);
    });
  });

  return { ready, setUnread, PAGES };
})();
