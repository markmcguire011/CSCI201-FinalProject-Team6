/*
 * listing-form.js: the Sell page and the Edit Listing page (section 7). One page for both:
 *
 *   listing-form.html          create: POST api/listings with the fields and the "photos" files in display order
 *   listing-form.html?id=42    edit:   GET api/listings/42 fills the form; Save sends POST api/listings/42 with
 *                              version, keepPhotoIds, newPhotos and photoOrder ("keep:<id>" / "new:<index>")
 *
 * The form is checked here first with the same rules and messages as Validator on the server, so a mistake
 * is shown without sending anything. The server checks everything again.
 * All user text is added with textContent, never innerHTML, so it shows exactly as typed.
 */
(() => {
  const MAX_PHOTOS = 5;
  const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
  const LIMITS = { title: 80, pickupSpot: 60, description: 1000 };
  const FIELDS = ["photos", "title", "category", "condition", "price", "courseCode", "pickupSpot", "description"];

  // The same messages as Validator.java.
  const MESSAGES = {
    title: "Enter a title (up to 80 characters)",
    category: "Choose a category",
    price: "Price must be a whole number from 0 to 10,000.",
    condition: "Choose a condition",
    courseCode: "Enter a course code like CSCI 201, or leave it blank",
    pickupSpot: "Enter a pickup spot (up to 60 characters)",
    description: "Enter a description (up to 1,000 characters)",
    tooManyPhotos: "You can add up to 5 photos.",
    photoSize: "Photo must be 5 MB or smaller.",
    photoType: "Photo must be a JPG or PNG file.",
  };
  const NOT_OWNER = "You can only edit your own listings and profile";
  const NOT_FOUND = "Listing not found.";
  const PHOTOS_OUT_OF_DATE = "Photo list is out of date. Refresh and try again.";

  const params = new URLSearchParams(location.search);
  const listingId = params.get("id");
  const editing = listingId !== null;

  const form = document.getElementById("listing-form");
  const strip = document.getElementById("photo-strip");
  const addTile = document.getElementById("photo-add");
  const fileInput = document.getElementById("photo-input");
  const submitButton = document.getElementById("submit-button");
  const submitLabel = editing ? "Save Changes" : "Post listing";
  const busyLabel = editing ? "Saving…" : "Posting…";

  // Set before access.js checks the page on DOMContentLoaded: editing needs EDIT_OWN, creating needs SELL.
  submitButton.dataset.requires = editing ? "EDIT_OWN" : "SELL";
  submitButton.textContent = submitLabel;

  /**
   * The photos in display order. A saved photo is {key, kind: "keep", id, url}; a new one is
   * {key, kind: "new", file, url} with an object URL for its preview.
   */
  let photos = [];
  let nextKey = 1;
  let version = 0;
  let locked = false;
  let savedState = "";   // the form as loaded or last saved, to tell whether Cancel would discard changes

  // ----- small helpers -----

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function input(name) {
    return document.getElementById(name);
  }

  /** "PENDING" becomes "Pending". */
  function statusLabel(status) {
    return status ? status.charAt(0) + status.slice(1).toLowerCase() : "";
  }

  /** The same rule as Validator.normalizeCourseCode: "csci201" becomes "CSCI 201". */
  function normalizeCourseCode(code) {
    if (!code || !code.trim()) return null;
    const compact = code.trim().toUpperCase().replace(/\s+/g, "");
    for (let i = 1; i < compact.length; i++) {
      if (/\d/.test(compact[i])) {
        return /[A-Z]/.test(compact[i - 1]) ? compact.slice(0, i) + " " + compact.slice(i) : compact;
      }
    }
    return compact;
  }

  /** The same checks as Validator.validateListing. */
  function check() {
    const errors = {};
    const text = (name) => input(name).value.trim();
    if (text("title").length < 1 || text("title").length > LIMITS.title) errors.title = MESSAGES.title;
    if (!input("category").value) errors.category = MESSAGES.category;
    const price = text("price");
    if (!/^\d{1,5}$/.test(price) || Number(price) > 10000) errors.price = MESSAGES.price;
    if (!input("condition").value) errors.condition = MESSAGES.condition;
    const course = normalizeCourseCode(input("courseCode").value);
    if (course !== null && !/^[A-Z]{2,4} \d{3}[A-Z]?$/.test(course)) errors.courseCode = MESSAGES.courseCode;
    if (text("pickupSpot").length < 1 || text("pickupSpot").length > LIMITS.pickupSpot) errors.pickupSpot = MESSAGES.pickupSpot;
    if (text("description").length < 1 || text("description").length > LIMITS.description) errors.description = MESSAGES.description;
    if (photos.length > MAX_PHOTOS) errors.photos = MESSAGES.tooManyPhotos;
    return errors;
  }

  /** "12/80", red once past the limit. The button stays enabled so the message can explain. */
  function updateCounter(name) {
    const counter = document.getElementById(name + "-counter");
    const length = input(name).value.trim().length;
    counter.textContent = length + "/" + LIMITS[name];
    counter.classList.toggle("counter-over", length > LIMITS[name]);
  }

  function updateCounters() {
    Object.keys(LIMITS).forEach(updateCounter);
  }

  /** Everything the user can change, as text, so Cancel can tell whether anything changed. */
  function currentState() {
    const values = ["title", "category", "price", "condition", "courseCode", "pickupSpot", "description"].map((n) => input(n).value);
    return JSON.stringify([values, photos.map((p) => p.key)]);
  }

  // ----- errors -----

  function clearErrors() {
    form.querySelectorAll(".field-error, .form-error").forEach((node) => node.remove());
    form.querySelectorAll("[aria-invalid]").forEach((node) => node.removeAttribute("aria-invalid"));
  }

  /** Puts each message under its field, like the profile page. A message with no field goes above the buttons. */
  function showErrors(errors, summary) {
    clearErrors();
    let first = null;
    FIELDS.forEach((name) => {
      if (!errors[name]) return;
      const target = name === "photos" ? document.getElementById("photo-strip") : input(name);
      const note = el("p", "field-error", errors[name]);
      note.id = name + "-error";
      target.setAttribute("aria-invalid", "true");
      target.setAttribute("aria-describedby", note.id);
      target.closest(".field").appendChild(note);
      if (!first && name !== "photos") first = target;
    });
    if (summary) {
      const note = el("p", "form-error", summary);
      note.setAttribute("role", "alert");
      submitButton.closest(".form-buttons").insertAdjacentElement("beforebegin", note);
    }
    if (first) first.focus();
  }

  function showPhotoError(message) {
    const old = document.getElementById("photos-error");
    if (old) old.remove();
    if (!message) return;
    const note = el("p", "field-error", message);
    note.id = "photos-error";
    document.getElementById("photos-field").appendChild(note);
  }

  // ----- photo strip -----

  /** Reads the first bytes of the file, like the server, so a renamed text file is caught here too. */
  async function isJpgOrPng(file) {
    const bytes = new Uint8Array(await file.slice(0, 4).arrayBuffer());
    const jpg = bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
    const png = bytes.length >= 4 && bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47;
    return jpg || png;
  }

  /** Null if the file can be added, or the message to show, e.g. '"notes.jpg": Photo must be a JPG or PNG file.' */
  async function photoError(file) {
    if (file.size > MAX_PHOTO_BYTES) return `"${file.name}": ${MESSAGES.photoSize}`;
    if (!(await isJpgOrPng(file))) return `"${file.name}": ${MESSAGES.photoType}`;
    return null;
  }

  function move(index, by) {
    const target = index + by;
    if (target < 0 || target >= photos.length) return;
    [photos[index], photos[target]] = [photos[target], photos[index]];
    renderStrip();
    // Keep the keyboard on the same photo after it moved.
    const button = strip.querySelectorAll(".photo-thumb")[target].querySelector(by < 0 ? ".move-left" : ".move-right");
    (button.disabled ? strip.querySelectorAll(".photo-thumb")[target].querySelector(".remove-photo") : button).focus();
  }

  function remove(index) {
    const [gone] = photos.splice(index, 1);
    if (gone.kind === "new") URL.revokeObjectURL(gone.url);
    showPhotoError("");
    renderStrip();
  }

  function thumbButton(className, text, label, onClick, disabled) {
    const button = el("button", "photo-button " + className, text);
    button.type = "button";
    button.setAttribute("aria-label", label);
    button.disabled = disabled;
    button.addEventListener("click", onClick);
    return button;
  }

  function renderStrip() {
    strip.querySelectorAll(".photo-thumb").forEach((node) => node.remove());
    photos.forEach((photo, i) => {
      const item = el("li", "photo-thumb");
      const img = el("img");
      img.src = photo.url;
      img.alt = "Photo " + (i + 1);
      item.appendChild(img);
      if (i === 0) item.appendChild(el("span", "cover-badge", "Cover"));

      const controls = el("div", "photo-controls");
      const n = i + 1;
      controls.append(
        thumbButton("move-left", "←", `Move photo ${n} left`, () => move(i, -1), locked || i === 0),
        thumbButton("remove-photo", "×", `Remove photo ${n}`, () => remove(i), locked),
        thumbButton("move-right", "→", `Move photo ${n} right`, () => move(i, 1), locked || i === photos.length - 1),
      );
      item.appendChild(controls);
      strip.insertBefore(item, addTile);
    });
    addTile.hidden = locked || photos.length >= MAX_PHOTOS;
  }

  fileInput.addEventListener("change", async () => {
    const files = Array.from(fileInput.files);
    fileInput.value = "";   // choosing the same file again still fires change
    let problem = null;
    for (const file of files) {
      if (photos.length >= MAX_PHOTOS) {
        problem = problem || MESSAGES.tooManyPhotos;
        break;
      }
      const error = await photoError(file);
      if (error) {
        problem = problem || error;
        continue;
      }
      photos.push({ key: "new" + nextKey++, kind: "new", file, url: URL.createObjectURL(file) });
    }
    renderStrip();
    showPhotoError(problem);
  });

  // ----- loading and showing -----

  function showMessage(text) {
    document.getElementById("form-loading").hidden = true;
    form.hidden = true;
    document.getElementById("form-message-text").textContent = text;
    document.getElementById("form-message-link").href = Nav.PAGES.browse;
    document.getElementById("form-message").hidden = false;
  }

  /** Fills the form from a ListingDetail (GET or a successful save). */
  function fill(listing) {
    input("title").value = listing.title || "";
    input("category").value = listing.categoryName || "";
    input("price").value = String(listing.price);
    input("condition").value = listing.conditionName || "";
    input("courseCode").value = listing.courseCode || "";
    input("pickupSpot").value = listing.pickupSpot || "";
    input("description").value = listing.description || "";
    version = listing.version;
    photos.forEach((p) => { if (p.kind === "new") URL.revokeObjectURL(p.url); });
    photos = (listing.photos || []).map((p) => ({ key: "keep" + p.id, kind: "keep", id: p.id, url: p.url }));

    locked = !listing.editable;
    const banner = document.getElementById("locked-banner");
    banner.textContent = locked
      ? `This listing is ${statusLabel(listing.status)}, so its details are locked. You can still change its status from your profile.`
      : "";
    banner.hidden = !locked;
    form.querySelectorAll("input, select, textarea").forEach((node) => { node.disabled = locked; });
    submitButton.hidden = locked;
    document.getElementById("conflict-banner").hidden = true;

    clearErrors();
    updateCounters();
    renderStrip();
    savedState = currentState();
  }

  async function loadListing() {
    document.getElementById("form-loading").hidden = false;
    try {
      const listing = await Api.get("api/listings/" + encodeURIComponent(listingId), { silent: true });
      document.getElementById("form-loading").hidden = true;
      if (!listing.owner) {
        showMessage(NOT_OWNER);
        return;
      }
      document.title = "Edit listing · USC Marketplace";
      document.getElementById("form-title").textContent = "Edit listing";
      document.getElementById("form-intro").textContent = "Changes show on Browse and on your profile right away.";
      fill(listing);
      form.hidden = false;
    } catch (err) {
      if (err.status === 404) showMessage(NOT_FOUND);
      else showMessage(err.message);
    }
  }

  // ----- saving -----

  function setBusy(busy) {
    submitButton.disabled = busy;
    submitButton.textContent = busy ? busyLabel : submitLabel;
  }

  function formData() {
    const data = new FormData();
    ["title", "category", "price", "condition", "courseCode", "pickupSpot", "description"]
      .forEach((name) => data.append(name, input(name).value));
    if (!editing) {
      photos.forEach((p) => data.append("photos", p.file, p.file.name));
      return data;
    }
    data.append("version", String(version));
    let newIndex = 0;
    photos.forEach((p) => {
      if (p.kind === "keep") {
        data.append("keepPhotoIds", String(p.id));
        data.append("photoOrder", "keep:" + p.id);
      } else {
        data.append("newPhotos", p.file, p.file.name);
        data.append("photoOrder", "new:" + newIndex++);
      }
    });
    return data;
  }

  function showConflict(message) {
    document.getElementById("conflict-text").textContent = message;
    document.getElementById("conflict-banner").hidden = false;
    document.getElementById("reload-button").focus();
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    // access.js turned the button into "Log in" or "Verify your email" (Enter in a field still submits the form).
    if (submitButton.dataset.blocked === "true" || submitButton.disabled || locked) return;
    setBusy(true);   // before anything is awaited, so a double click sends one request

    const errors = check();
    if (Object.keys(errors).length > 0) {
      showErrors(errors);
      setBusy(false);
      return;
    }
    clearErrors();
    document.getElementById("conflict-banner").hidden = true;

    try {
      if (!editing) {
        await Api.upload("api/listings", formData(), { silent: true });
        Toast.flash("Your listing is live.");
        // TODO dev plan step 7 (Browse): go to the Browse page instead, once it exists.
        location.href = Nav.PAGES.profile;
        return;
      }
      const listing = await Api.upload("api/listings/" + encodeURIComponent(listingId), formData(), { silent: true });
      fill(listing);
      setBusy(false);
      Toast.success("Changes saved.");
    } catch (err) {
      setBusy(false);
      const fields = err.fields || {};
      if (err.status === 409) {
        showConflict(err.message);
      } else if (fields.photos === PHOTOS_OUT_OF_DATE) {
        showConflict(PHOTOS_OUT_OF_DATE);   // the photos changed in another tab: the same fix, reload
      } else {
        showErrors(fields, Object.keys(fields).length > 0 ? null : err.message);
      }
    }
  });

  document.getElementById("reload-button").addEventListener("click", loadListing);

  document.getElementById("cancel-button").addEventListener("click", () => {
    if (currentState() !== savedState && !window.confirm("Discard your changes?")) return;
    if (history.length > 1) history.back();
    else location.href = Nav.PAGES.profile;
  });

  ["title", "pickupSpot", "description"].forEach((name) => input(name).addEventListener("input", () => updateCounter(name)));

  async function start() {
    const me = await Nav.ready;
    if (!me) {
      const page = location.pathname.split("/").pop();
      location.replace(Nav.PAGES.login + "?next=" + encodeURIComponent(page + location.search));
      return;
    }
    if (editing) {
      await loadListing();
    } else {
      updateCounters();
      renderStrip();
      savedState = currentState();
      form.hidden = false;
    }
  }

  document.addEventListener("DOMContentLoaded", start);
})();
