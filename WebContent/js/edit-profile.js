/*
 * edit-profile.js: the Edit Profile page (section 6, Editing a profile).
 *
 * Only the logged-in user's own profile can be edited; a guest is sent to the login page.
 * Saving sends a multipart form to POST api/profile: fullName, bio, removePhoto ("true" or absent),
 * and the file "photo" when a new one was chosen. The server checks everything again
 * (Validator.validateProfile); the photo is checked here first so a file that is too large is never sent.
 */
(() => {
  const MAX_NAME = 50;
  const MAX_BIO = 300;
  const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
  const PHOTO_SIZE_ERROR = "Photo must be 5 MB or smaller.";
  const PHOTO_TYPE_ERROR = "Photo must be a JPG or PNG file.";
  const FIELDS = ["photo", "fullName", "bio"];

  const form = document.getElementById("profile-form");
  const nameInput = document.getElementById("fullName");
  const bioInput = document.getElementById("bio");
  const fileInput = document.getElementById("photo");
  const preview = document.getElementById("photo-preview");
  const removeButton = document.getElementById("remove-photo");
  const saveButton = document.getElementById("save-button");

  let currentPhotoUrl = null;   // the saved photo, from the server
  let chosenFile = null;        // a new file picked in this visit
  let previewUrl = null;        // object URL for chosenFile's preview
  let removePhoto = false;

  /** "Maya Chen" becomes "MC", the same rule as User.getInitials on the server. */
  function initials(name) {
    return (name || "").trim().split(/\s+/).filter(Boolean).slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase()).join("");
  }

  /** Shows the new file, the saved photo, or the initials, in that order. */
  function showPhoto() {
    preview.textContent = "";
    const url = previewUrl || (removePhoto ? null : currentPhotoUrl);
    if (url) {
      const img = document.createElement("img");
      img.src = url;
      img.alt = "Your profile photo";
      preview.appendChild(img);
      preview.classList.remove("profile-initials");
    } else {
      preview.textContent = initials(nameInput.value);
      preview.classList.add("profile-initials");
    }
    removeButton.disabled = !url;
  }

  /** "12/50", red once past the limit. Save stays enabled so the server's message can explain. */
  function updateCounter(input, max) {
    const counter = document.getElementById(input.id + "-counter");
    const length = input.value.trim().length;
    counter.textContent = length + "/" + max;
    counter.classList.toggle("counter-over", length > max);
  }

  function clearErrors() {
    form.querySelectorAll(".field-error, .form-error").forEach((el) => el.remove());
    form.querySelectorAll("[aria-invalid]").forEach((el) => el.removeAttribute("aria-invalid"));
  }

  function clearFieldError(name) {
    const note = document.getElementById(name + "-error");
    if (note) note.remove();
    document.getElementById(name).removeAttribute("aria-invalid");
  }

  /** Puts each message under its field, the way register.js does. A message with no field goes above Save. */
  function showErrors(errors, summary) {
    clearErrors();
    let first = null;
    FIELDS.forEach((name) => {
      if (!errors[name]) return;
      const input = document.getElementById(name);
      const note = document.createElement("p");
      note.className = "field-error";
      note.id = name + "-error";
      note.textContent = errors[name];
      input.setAttribute("aria-invalid", "true");
      input.setAttribute("aria-describedby", note.id);
      input.closest(".field").appendChild(note);
      if (!first) first = input;
    });
    if (summary) {
      const note = document.createElement("p");
      note.className = "form-error";
      note.setAttribute("role", "alert");
      note.textContent = summary;
      saveButton.closest(".form-buttons").insertAdjacentElement("beforebegin", note);
    }
    if (first) first.focus();
  }

  /** Reads the first bytes of the file, like the server, so a renamed text file is caught here too. */
  async function isJpgOrPng(file) {
    const bytes = new Uint8Array(await file.slice(0, 4).arrayBuffer());
    const jpg = bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
    const png = bytes.length >= 4 && bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47;
    return jpg || png;
  }

  async function photoError(file) {
    if (file.size > MAX_PHOTO_BYTES) return PHOTO_SIZE_ERROR;
    if (!(await isJpgOrPng(file))) return PHOTO_TYPE_ERROR;
    return null;
  }

  function forgetChosenFile() {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = null;
    chosenFile = null;
    fileInput.value = "";
  }

  fileInput.addEventListener("change", async () => {
    const file = fileInput.files[0];
    if (!file) return;
    clearFieldError("photo");
    const problem = await photoError(file);
    if (problem) {
      forgetChosenFile();
      showPhoto();
      showErrors({ photo: problem });
      return;
    }
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    chosenFile = file;
    previewUrl = URL.createObjectURL(file);
    removePhoto = false;   // choosing a new photo undoes Remove
    showPhoto();
  });

  removeButton.addEventListener("click", () => {
    forgetChosenFile();
    removePhoto = true;
    clearFieldError("photo");
    showPhoto();
  });

  nameInput.addEventListener("input", () => {
    updateCounter(nameInput, MAX_NAME);
    if (preview.classList.contains("profile-initials")) preview.textContent = initials(nameInput.value);
  });
  bioInput.addEventListener("input", () => updateCounter(bioInput, MAX_BIO));

  function setSaving(saving) {
    saveButton.disabled = saving;
    saveButton.textContent = saving ? "Saving…" : "Save";
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    // access.js turned the button into "Verify your email" (Enter in a field still submits the form).
    if (saveButton.dataset.blocked === "true" || saveButton.disabled) return;
    // Disable first, before anything is awaited, so a double click cannot send it twice.
    setSaving(true);

    // The photo was checked when it was chosen; check again in case the file changed on disk.
    if (chosenFile) {
      const problem = await photoError(chosenFile);
      if (problem) {
        showErrors({ photo: problem });
        setSaving(false);
        return;
      }
    }

    const data = new FormData();
    data.append("fullName", nameInput.value);
    data.append("bio", bioInput.value);
    if (removePhoto) data.append("removePhoto", "true");
    if (chosenFile) data.append("photo", chosenFile, chosenFile.name);

    clearErrors();
    try {
      await Api.upload("api/profile", data, { silent: true });
      Toast.flash("Profile updated.");
      location.href = Nav.PAGES.profile;
    } catch (err) {
      const fields = err.fields || {};
      showErrors(fields, Object.keys(fields).length > 0 ? null : err.message);
      setSaving(false);
    }
  });

  async function load() {
    const me = await Nav.ready;
    if (!me) {
      location.replace(Nav.PAGES.login + "?next=" + encodeURIComponent("edit-profile.html"));
      return;
    }
    document.getElementById("cancel-button").href = Nav.PAGES.profile;
    try {
      const profile = await Api.get("api/profile?id=" + encodeURIComponent(me.id));
      nameInput.value = profile.fullName;
      bioInput.value = profile.bio || "";
      document.getElementById("email").textContent = profile.email || "";
      document.getElementById("accountType").textContent = profile.accountType || "";
      currentPhotoUrl = profile.photoUrl || null;
      updateCounter(nameInput, MAX_NAME);
      updateCounter(bioInput, MAX_BIO);
      showPhoto();
      form.hidden = false;
    } catch (err) {
      // api.js already showed the error
    }
  }

  document.addEventListener("DOMContentLoaded", load);
})();
