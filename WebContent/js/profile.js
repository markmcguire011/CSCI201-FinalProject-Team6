/*
 * profile.js: the Profile page (section 6, Viewing a profile).
 *
 *   profile.html?id=42   anyone can view user 42's profile
 *   profile.html         the logged-in user's own profile; a guest is sent to the login page
 *
 * GET api/profile?id=N answers {userId, fullName, accountType, joinMonth, bio?, photoUrl?, owner, email?, listings}.
 * Keys whose value is null are left out, so a missing bio or photoUrl means there is none.
 * All user text is added with textContent, never innerHTML, so it shows exactly as typed.
 */
(() => {
  const STATUS_LABELS = { PENDING: "Pending", SOLD: "Sold", INACTIVE: "Inactive", EXPIRED: "Expired" };
  const EDITABLE = ["AVAILABLE", "INACTIVE"];
  const LISTING_FORM = "listing-form.html";
  const EDIT_PROFILE = "edit-profile.html";

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  /** "Maya Chen" becomes "MC", the same rule as User.getInitials on the server. */
  function initials(name) {
    return (name || "").trim().split(/\s+/).filter(Boolean).slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase()).join("");
  }

  /** Fills box with the photo, or with a circle of initials when there is no photo. */
  function showAvatar(box, photoUrl, name) {
    box.textContent = "";
    if (photoUrl) {
      const img = el("img");
      img.src = photoUrl;
      img.alt = name ? name + "'s photo" : "Profile photo";
      box.appendChild(img);
      box.classList.remove("profile-initials");
    } else {
      box.textContent = initials(name);
      box.classList.add("profile-initials");
      box.setAttribute("aria-hidden", "true");
    }
  }

  function price(amount) {
    return "$" + Number(amount).toLocaleString("en-US");
  }

  function listingCard(listing, owner) {
    const item = el("li", "card profile-card");

    if (listing.coverUrl) {
      const img = el("img");
      img.src = listing.coverUrl;
      img.alt = "";
      img.loading = "lazy";
      item.appendChild(img);
    } else {
      item.appendChild(el("div", "card-placeholder", "No photo"));
    }

    const body = el("div", "card-body");
    body.appendChild(el("p", "card-title", listing.title));
    const priceRow = el("p", "card-price-row");
    priceRow.appendChild(el("span", "card-price", price(listing.price)));
    if (STATUS_LABELS[listing.status]) {
      priceRow.appendChild(el("span", "status-badge status-" + listing.status.toLowerCase(), STATUS_LABELS[listing.status]));
    }
    body.appendChild(priceRow);
    body.appendChild(el("p", "card-meta", listing.category || ""));

    if (owner && EDITABLE.includes(listing.status)) {
      const edit = el("a", "card-edit", "Edit");
      edit.href = LISTING_FORM + "?id=" + encodeURIComponent(listing.id);
      edit.dataset.requires = "EDIT_OWN";
      edit.setAttribute("aria-label", "Edit " + listing.title);
      body.appendChild(edit);
    }
    item.appendChild(body);

    // TODO section 8 (Listing pop-up): clicking the card opens the listing pop-up.
    return item;
  }

  function render(profile, me) {
    document.title = profile.fullName + " · USC Marketplace";
    showAvatar(document.getElementById("profile-photo"), profile.photoUrl, profile.fullName);
    document.getElementById("profile-name").textContent = profile.fullName;
    document.getElementById("profile-meta").textContent =
      [profile.accountType, profile.joinMonth ? "Joined " + profile.joinMonth : null].filter(Boolean).join(" · ");

    const bio = document.getElementById("profile-bio");
    bio.textContent = profile.bio || "";
    bio.hidden = !profile.bio;

    const actions = document.getElementById("profile-actions");
    actions.textContent = "";
    if (profile.owner) {
      const edit = el("a", "btn btn-secondary", "Edit Profile");
      edit.href = EDIT_PROFILE;
      edit.dataset.requires = "EDIT_OWN";
      actions.appendChild(edit);
    } else {
      // Guests see "Log in to continue" instead (access.js).
      const report = el("button", "btn btn-danger", "Report user");
      report.type = "button";
      report.dataset.requires = "REPORT";
      report.addEventListener("click", () => ReportDialog.open("USER", profile.userId, profile.fullName));
      actions.appendChild(report);
    }

    const list = document.getElementById("listings");
    list.textContent = "";
    const listings = profile.listings || [];
    listings.forEach((listing) => list.appendChild(listingCard(listing, profile.owner)));
    document.getElementById("listings-empty").hidden = listings.length > 0;

    document.getElementById("profile-loading").hidden = true;
    document.getElementById("profile").hidden = false;
    // The buttons were added after access.js checked the page, so check them now.
    Access.guardAll(document.getElementById("profile"));
  }

  function showMissing() {
    document.getElementById("profile-loading").hidden = true;
    document.getElementById("profile").hidden = true;
    document.getElementById("back-to-browse").href = Nav.PAGES.browse;
    document.getElementById("profile-missing").hidden = false;
  }

  async function load() {
    const me = await Nav.ready;
    const params = new URLSearchParams(location.search);
    let id = params.get("id");
    if (id === null) {
      if (!me) {
        location.replace(Nav.PAGES.login + "?next=" + encodeURIComponent(Nav.PAGES.profile));
        return;
      }
      id = String(me.id);
    }

    try {
      const profile = await Api.get("api/profile?id=" + encodeURIComponent(id), { silent: true });
      render(profile, me);
    } catch (err) {
      if (err.status === 404) {
        showMissing();
      } else {
        document.getElementById("profile-loading").textContent = err.message;
        Toast.error(err.message);
      }
    }
  }

  document.addEventListener("DOMContentLoaded", load);
})();
