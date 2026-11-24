/*
 * admin.js: the Admin Dashboard (section 3, Resolving a report).
 *
 * Lists the open reports. Each one has Hide, Suspend 7 days, Suspend permanently and Dismiss.
 * Hide is greyed out when the report is about a user, because only a listing can be hidden. The server checks that the user is an administrator on every
 * request, so opening this page as anyone else only shows a message.
 */
(() => {
  const list = document.getElementById("report-list");
  const note = document.getElementById("admin-note");

  const ACTIONS = [
    { action: "HIDE_LISTING", label: "Hide", style: "btn-secondary", listingOnly: true, done: "Listing hidden. The seller was notified." },
    { action: "SUSPEND_7_DAYS", label: "Suspend 7 days", style: "btn-danger", done: "User suspended for 7 days.",
      confirm: (r) => "Suspend " + (r.reportedUserName || "this user") + " for 7 days?" },
    { action: "SUSPEND_PERMANENT", label: "Suspend permanently", style: "btn-danger", done: "User suspended permanently.",
      confirm: (r) => "Suspend " + (r.reportedUserName || "this user") + " permanently? They will not be able to log in." },
    { action: "DISMISS", label: "Dismiss", style: "btn-secondary", done: "Report dismissed." },
  ];

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;   // textContent, so user text cannot inject HTML
    return node;
  }

  function showMessage(text) {
    list.textContent = "";
    list.appendChild(el("li", "empty-note", text));
  }

  function when(isoText) {
    const date = new Date(isoText);
    return isNaN(date) ? "" : date.toLocaleString([], { month: "short", day: "numeric", year: "numeric", hour: "numeric", minute: "2-digit" });
  }

  function card(report) {
    const item = el("li", "report-card");
    const kind = report.targetType === "LISTING" ? "Listing" : "User";
    item.appendChild(el("h2", null, kind + ": " + report.targetLabel));
    item.appendChild(el("span", "report-tag", report.reasonLabel));
    if (report.comment) item.appendChild(el("p", "report-comment", "“" + report.comment + "”"));

    let meta = "Reported by " + report.reporterName + " · " + when(report.createdAt);
    if (report.targetType === "LISTING" && report.reportedUserName) meta += " · Posted by " + report.reportedUserName;
    item.appendChild(el("p", "report-meta", meta));

    const actions = el("div", "report-actions");
    ACTIONS.forEach((choice) => {
      const button = el("button", "btn " + choice.style, choice.label);
      button.type = "button";
      if (choice.listingOnly && report.targetType !== "LISTING") {
        // Every report shows the same four buttons; Hide only works when the report is about a listing.
        button.disabled = true;
        button.dataset.never = "true";
        button.title = "Only a reported listing can be hidden.";
      }
      button.addEventListener("click", () => resolve(report, choice, item));
      actions.appendChild(button);
    });
    item.appendChild(actions);
    return item;
  }

  async function resolve(report, choice, item) {
    if (choice.confirm && !window.confirm(choice.confirm(report))) return;
    const buttons = item.querySelectorAll("button");
    buttons.forEach((b) => (b.disabled = true));   // a double click cannot send two actions
    try {
      await Api.post("api/admin/reports/" + report.id + "/resolve", { action: choice.action });
      Toast.success(choice.done);
      item.remove();
      if (!list.children.length) showMessage("No open reports.");
    } catch (err) {
      // api.js already showed the message. 409 means another administrator handled it first.
      if (err.status === 409 || err.status === 404) load();
      else buttons.forEach((b) => (b.disabled = b.dataset.never === "true"));
    }
  }

  async function load() {
    try {
      const reports = await Api.get("api/admin/reports", { silent: true });
      if (reports.length === 0) {
        showMessage("No open reports.");
        return;
      }
      list.textContent = "";
      reports.forEach((report) => list.appendChild(card(report)));
    } catch (err) {
      note.hidden = true;
      if (err.status === 401) {
        list.textContent = "";
        const item = el("li", "empty-note");
        const link = el("a", null, "Log in to continue");
        link.href = Nav.PAGES.login + "?next=admin.html";
        item.appendChild(link);
        list.appendChild(item);
      } else {
        showMessage(err.message);
      }
    }
  }

  document.addEventListener("DOMContentLoaded", load);
})();
