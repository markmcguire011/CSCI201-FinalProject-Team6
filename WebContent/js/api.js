/*
 * api.js: sends requests to the servlets and handles their errors in one place (section 16).
 * Needs toast.js, so include it after toast.js on every page:
 *   <script src="js/toast.js"></script>
 *   <script src="js/api.js"></script>
 *
 * Every method returns the JSON answer. If the server answers {"error": "..."}, the message is shown
 * with Toast.error and an Api.Error is thrown, so the page can stop what it was doing:
 *
 *   const listing = await Api.get("api/listings/42");
 *   await Api.post("api/saved", { listingId: 42 });
 *
 * For forms, pass { silent: true } and show the errors in red under the form instead:
 *   try { await Api.post("api/register", data, { silent: true }); }
 *   catch (err) { Api.showFormErrors(form, err); }
 *
 * URLs have no leading slash (api/..., not /api/...), so they work under /USCMarketplace/.
 */
const Api = (() => {
  const GENERIC_ERROR = "Something went wrong. Please try again.";
  const OFFLINE_ERROR = "Can't reach the server. Check your internet connection and try again.";

  class ApiError extends Error {
    constructor(status, message, fields) {
      super(message);
      this.status = status;          // 400, 401, 403, 404, 409, 500, or 0 when offline
      this.fields = fields || {};    // field name -> message, for forms
    }
  }

  async function request(method, url, body, options = {}) {
    const init = { method, headers: { Accept: "application/json" }, credentials: "same-origin" };
    if (body instanceof FormData) {
      init.body = body;   // file uploads: the browser sets the multipart header itself
    } else if (body !== undefined) {
      init.headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(body);
    }

    let response;
    try {
      response = await fetch(url, init);
    } catch (networkError) {
      return fail(new ApiError(0, OFFLINE_ERROR), options);
    }

    const text = await response.text();
    let data = null;
    if (text) {
      try {
        data = JSON.parse(text);
      } catch (e) {
        data = null;   // not JSON (e.g. a Tomcat error page): use the general message
      }
    }

    if (!response.ok) {
      const message = (data && data.error) || GENERIC_ERROR;
      return fail(new ApiError(response.status, message, data && data.fields), options);
    }
    return data;
  }

  function fail(error, options) {
    if (!options.silent && typeof Toast !== "undefined") Toast.error(error.message);
    throw error;
  }

  /** Shows an error in red under the form, and each field's error under that field. */
  function showFormErrors(form, error) {
    clearFormErrors(form);
    const fields = (error && error.fields) || {};
    Object.keys(fields).forEach((name) => {
      const input = form.querySelector(`[name="${CSS.escape(name)}"]`);
      if (!input) return;
      input.setAttribute("aria-invalid", "true");
      const note = document.createElement("p");
      note.className = "field-error";
      note.textContent = fields[name];
      input.insertAdjacentElement("afterend", note);
    });
    const summary = document.createElement("p");
    summary.className = "form-error";
    summary.setAttribute("role", "alert");
    summary.textContent = (error && error.message) || GENERIC_ERROR;
    form.appendChild(summary);
  }

  function clearFormErrors(form) {
    form.querySelectorAll(".field-error, .form-error").forEach((el) => el.remove());
    form.querySelectorAll("[aria-invalid]").forEach((el) => el.removeAttribute("aria-invalid"));
  }

  return {
    Error: ApiError,
    get: (url, options) => request("GET", url, undefined, options),
    post: (url, body, options) => request("POST", url, body, options),
    put: (url, body, options) => request("PUT", url, body, options),
    del: (url, options) => request("DELETE", url, undefined, options),
    /** formData holds the form fields and files, e.g. new FormData(formElement). */
    upload: (url, formData, options) => request("POST", url, formData, options),
    showFormErrors,
    clearFormErrors,
  };
})();
