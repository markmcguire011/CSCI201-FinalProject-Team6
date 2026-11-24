/*
 * report.js: the Report dialog (section 3, Reporting a listing or user).
 * Needs toast.js, api.js and css/forms.css. Any page with a Report button includes this file and calls:
 *
 *   ReportDialog.open("LISTING", 42, "Bose QC45 headphones");
 *   ReportDialog.open("USER", 17, "Jordan Lee");
 *
 * The dialog has the four reasons, an optional comment with a 300-character counter, and Submit.
 * Any logged-in user can report, verified or not. Guests should be shown "Log in to continue" instead
 * of the Report button; if a guest gets here anyway, the server answers "Login required".
 */
const ReportDialog = (() => {
  const MAX_COMMENT = 300;
  const REASONS = [
    ["SCAM", "Scam"],
    ["PROHIBITED_ITEM", "Prohibited item"],
    ["OFFENSIVE", "Offensive"],
    ["OTHER", "Other"],
  ];

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function open(targetType, targetId, label) {
    const dialog = el("dialog", "report-dialog");
    dialog.setAttribute("aria-labelledby", "report-title");
    const form = el("form");
    form.noValidate = true;

    const title = el("h2", null, targetType === "USER" ? "Report this user" : "Report this listing");
    title.id = "report-title";
    form.appendChild(title);
    if (label) form.appendChild(el("p", "form-intro", label));

    const reasonField = el("fieldset", "field");
    reasonField.appendChild(el("legend", null, "Reason"));
    const choices = el("div", "choice-list");
    REASONS.forEach(([value, text]) => {
      const row = el("label");
      const radio = el("input");
      radio.type = "radio";
      radio.name = "reason";
      radio.value = value;
      row.appendChild(radio);
      row.appendChild(document.createTextNode(" " + text));
      choices.appendChild(row);
    });
    reasonField.appendChild(choices);
    const reasonError = el("p", "field-error");
    reasonError.hidden = true;
    reasonField.appendChild(reasonError);
    form.appendChild(reasonField);

    const commentField = el("div", "field");
    const commentLabel = el("label", null, "Comment (optional)");
    commentLabel.htmlFor = "report-comment";
    const comment = el("textarea");
    comment.id = "report-comment";
    comment.name = "comment";
    const counter = el("p", "counter", "0/" + MAX_COMMENT);
    const commentError = el("p", "field-error");
    commentError.hidden = true;
    commentField.append(commentLabel, comment, counter, commentError);
    form.appendChild(commentField);

    const actions = el("div", "dialog-actions");
    const cancel = el("button", "btn btn-secondary", "Cancel");
    cancel.type = "button";
    const submit = el("button", "btn btn-primary", "Submit");
    submit.type = "submit";
    actions.append(cancel, submit);
    form.appendChild(actions);
    dialog.appendChild(form);

    function setError(note, message) {
      note.textContent = message || "";
      note.hidden = !message;
    }

    // No maxlength on the box: typing past the limit shows 301/300 in red, so the user sees why Submit is refused.
    comment.addEventListener("input", () => {
      const length = comment.value.length;
      counter.textContent = length + "/" + MAX_COMMENT;
      counter.classList.toggle("counter-over", length > MAX_COMMENT);
      if (length <= MAX_COMMENT) setError(commentError, "");
    });
    choices.addEventListener("change", () => setError(reasonError, ""));

    cancel.addEventListener("click", () => dialog.close());
    dialog.addEventListener("close", () => dialog.remove());
    // Clicking the dark area outside the dialog closes it.
    dialog.addEventListener("click", (event) => {
      if (event.target === dialog) dialog.close();
    });

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const picked = form.querySelector('input[name="reason"]:checked');
      setError(reasonError, picked ? "" : "Pick a reason");
      const tooLong = comment.value.length > MAX_COMMENT;
      setError(commentError, tooLong ? "Comment must be 300 characters or fewer." : "");
      if (!picked || tooLong) return;

      submit.disabled = true;   // two fast clicks cannot send two reports
      try {
        const answer = await Api.post("api/reports", {
          targetType,
          targetId,
          reason: picked.value,
          comment: comment.value,
        });
        dialog.close();
        Toast.success(answer.message);
      } catch (err) {
        // api.js already showed the message, e.g. "You already reported this".
        if (err.status === 409 || err.status === 404 || err.status === 401) dialog.close();
        else submit.disabled = false;
      }
    });

    document.body.appendChild(dialog);
    dialog.showModal();
    return dialog;
  }

  return { open };
})();
