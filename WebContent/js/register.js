/*
 * register.js: the Register page (section 3, Registering).
 *
 * The form is checked here first for quick feedback, with the same rules and messages as the server.
 * The server check (Validator.validateRegistration) is the one that counts.
 * Each error shows in red under its own field, and the other fields keep their values.
 */
(() => {
  const form = document.getElementById("register-form");
  const button = document.getElementById("register-button");
  const FIELDS = ["fullName", "email", "password", "confirmPassword", "accountType"];

  function readForm() {
    const picked = form.querySelector('input[name="accountType"]:checked');
    return {
      fullName: form.fullName.value,
      email: form.email.value,
      password: form.password.value,
      confirmPassword: form.confirmPassword.value,
      accountType: picked ? picked.value : null,
    };
  }

  /** The same checks as Validator.validateRegistration on the server. */
  function check(data) {
    const errors = {};
    const name = data.fullName.trim();
    if (name.length < 1 || name.length > 50) errors.fullName = "Enter your full name (up to 50 characters)";
    if (!/^[a-z0-9._%+-]+@usc\.edu$/.test(data.email.trim().toLowerCase())) errors.email = "Use your USC email address";
    if (data.password.length < 8) errors.password = "Password must be at least 8 characters";
    if (data.password !== data.confirmPassword) errors.confirmPassword = "Passwords do not match";
    if (!data.accountType) errors.accountType = "Choose Student or Alumni";
    return errors;
  }

  function clearErrors() {
    form.querySelectorAll(".field-error, .form-error").forEach((el) => el.remove());
    form.querySelectorAll("[aria-invalid]").forEach((el) => el.removeAttribute("aria-invalid"));
  }

  /** Puts each message under its field. A message with no field goes above the button. */
  function showErrors(errors, summary) {
    clearErrors();
    let first = null;
    FIELDS.forEach((name) => {
      if (!errors[name]) return;
      const field = document.getElementById(name);
      const note = document.createElement("p");
      note.className = "field-error";
      note.id = name + "-error";
      note.textContent = errors[name];
      field.setAttribute("aria-invalid", "true");
      field.setAttribute("aria-describedby", note.id);
      field.closest(".field").appendChild(note);
      if (!first) first = field;
    });
    if (summary) {
      const note = document.createElement("p");
      note.className = "form-error";
      note.setAttribute("role", "alert");
      note.textContent = summary;
      button.insertAdjacentElement("beforebegin", note);
    }
    if (first) (first.matches("fieldset") ? first.querySelector("input") : first).focus();
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = readForm();
    const errors = check(data);
    if (Object.keys(errors).length > 0) {
      showErrors(errors);
      return;
    }

    clearErrors();
    button.disabled = true;   // a double click cannot create two accounts
    button.textContent = "Registering...";
    try {
      await Api.post("api/register", data, { silent: true });
      Toast.flash("Account created. Enter the code we emailed you.");
      location.href = "verify.html";
    } catch (err) {
      const fields = err.fields || {};
      showErrors(fields, Object.keys(fields).length > 0 ? null : err.message);
      button.disabled = false;
      button.textContent = "Register";
    }
  });
})();
