const ListingPopup = (() => {
  let dialog, panel, openId, opener, scroll, version, photo = 0, request = 0;
  const label = value => value.charAt(0) + value.slice(1).toLowerCase();
  const url = id => "api/listings/" + encodeURIComponent(id);
  const change = data => window.dispatchEvent(new CustomEvent("marketplace-change", {detail:data}));
  const save = (id, desired) => Api.post(url(id) + "/saved", {isSaved:desired}, {silent:true});

  function node(tag, text) {
    const el = document.createElement(tag);
    if (text != null) el.textContent = text;
    return el;
  }
  function button(text, fn) {
    const el = node("button", text);
    el.type = "button"; el.onclick = fn;
    return el;
  }
  function signIn(id, verify) {
    const next = new URL(location.href);
    next.searchParams.set("listing", id);
    const page = next.pathname.split("/").pop() || "index.html";
    location.href = (verify ? "verify.html" : Nav.PAGES.login) + "?next=" + encodeURIComponent(page + next.search);
  }
  function setAddress(id) {
    const address = new URL(location.href);
    if (id) address.searchParams.set("listing", id); else address.searchParams.delete("listing");
    history.replaceState(null, "", address);
  }

  async function open(id, origin) {
    if (!dialog) {
      opener = origin; scroll = window.scrollY;
      dialog = node("dialog"); dialog.className = "listing-dialog"; dialog.setAttribute("aria-label", "Listing details");
      const close = button("Close", () => dialog.close());
      close.className = "close"; panel = node("div"); panel.setAttribute("aria-live", "polite");
      dialog.append(close, panel); document.body.append(dialog);
      dialog.onclick = e => {
        const box = dialog.getBoundingClientRect();
        if (e.target === dialog && (e.clientX < box.left || e.clientX > box.right || e.clientY < box.top || e.clientY > box.bottom)) dialog.close();
      };
      dialog.onclose = () => {
        request++; openId = null; version = null;
        dialog.remove(); dialog = null; panel = null; setAddress(null);
        const focus = opener?.isConnected ? opener : document.querySelector("#results, #listings");
        if (focus) { focus.tabIndex = focus.tabIndex < 0 ? -1 : focus.tabIndex; focus.focus({preventScroll:true}); }
        window.scrollTo(0, scroll);
      };
      dialog.showModal();
    }
    openId = id; version = null; photo = 0; setAddress(id);
    panel.textContent = "Loading listing…";
    await refresh();
  }

  async function refresh() {
    if (!openId) return;
    const ticket = ++request;
    panel.inert = true;
    try {
      const data = await Api.get(url(openId) + "/popup", {silent:true});
      if (ticket !== request) return;
      version = data.details.version; render(data);
    } catch (err) {
      if (ticket !== request) return;
      version = null; panel.replaceChildren(node("p", err.message));
      if (err.status !== 404) panel.append(button("Retry", refresh));
    } finally { if (ticket === request && panel) panel.inert = false; }
  }

  function gallery(l) {
    const box = node("div"); box.className = "listing-gallery";
    function draw() {
      box.replaceChildren();
      if (!l.photos.length) { box.textContent = "No photo"; return; }
      if (photo >= l.photos.length) photo = 0;
      const img = node("img"); img.src = l.photos[photo].url; img.alt = l.title + ", photo " + (photo+1);
      box.append(img);
      if (l.photos.length > 1) {
        const nav = node("nav"); nav.setAttribute("aria-label", "Listing photos");
        l.photos.forEach((_, i) => {
          const select = button("Photo " + (i+1), () => { photo = i; draw(); });
          select.disabled = (photo === i); nav.append(select);
        });
        box.append(nav);
      }
    }
    draw(); return box;
  }

  function statusForm(data) {
    const l = data.details, form = node("form");
    form.className = "status-form";
    const status = node("select"); status.name = "status";
    const statuses = l.status === "EXPIRED" ? ["EXPIRED", "AVAILABLE"] : ["AVAILABLE","PENDING","SOLD","INACTIVE"];
    statuses.filter(s => s !== "SOLD" || l.status !== "INACTIVE" || data.saleRecorded)
        .forEach(s => status.add(new Option(label(s), s, false, s === l.status)));
    const heading = node("label","Listing status"); heading.append(status); form.append(heading);
    const sale = node("fieldset"); sale.append(node("legend","Complete the sale"));
    const buyer = node("select"); buyer.name = "buyerId"; buyer.required = true;
    buyer.add(new Option("Choose a buyer",""));
    data.choices.forEach(c => buyer.add(new Option(c.name,c.id)));
    const buyerLabel = node("label","Buyer from this listing’s conversations"); buyerLabel.append(buyer);
    const proof = node("input"); proof.type = "file"; proof.accept = "image/jpeg,image/png,application/pdf"; proof.name = "proof"; proof.required = true;
    const proofLabel = node("label","Private payment proof (JPG, PNG or PDF, up to 5 MB)"); proofLabel.append(proof);
    sale.append(buyerLabel,proofLabel);
    const submit = node("button","Update status"), error = node("p"); error.setAttribute("role","alert");
    function select() {
      sale.hidden = sale.disabled = status.value !== "SOLD" || data.saleRecorded;
      submit.disabled = status.value === l.status || (!sale.disabled && (!buyer.value || !proof.files.length));
    }
    status.onchange = select; select(); form.append(sale,submit,error);
    buyer.onchange = proof.onchange = select;
    form.onsubmit = async e => {
      e.preventDefault(); const fields = new FormData(form); fields.set("version",l.version);
      submit.disabled = true;
      try {
        await Api.upload(url(l.id) + "/status",fields,{silent:true});
        change({id:l.id}); Toast.success("Listing status updated.");
      } catch (err) {
        error.textContent = err.message; select();
        if (err.status === 409) error.append(" ",button("Refresh listing",refresh));
      }
    };
    return form;
  }

  function render(data) {
    const l = data.details, p = data.permissions;
    panel.replaceChildren(node("h2",l.title), gallery(l), node("p","$" + l.price + " · " + label(l.status)), node("p",l.description));
    const fields = node("dl");
    for (const [key,value] of Object.entries({Category:l.category, Condition:l.condition, Course:l.courseCode,
            Pickup:l.pickupSpot, Posted:new Date(l.createdAt).toLocaleString()})) {
      if (value) fields.append(node("dt",key),node("dd",value));
    }
    const seller = node("a",l.sellerName); seller.href = Nav.PAGES.profile + "?id=" + l.sellerId;
    const rating = data.reviewCount ? data.sellerRating.toFixed(1) + " (" + data.reviewCount + (data.reviewCount === 1 ? " review)" : " reviews)") : "No reviews yet";
    panel.append(fields,seller,document.createTextNode(" · " + rating));
    const actions = node("div"); actions.className = "listing-actions"; panel.append(actions);
    function action(title, reason, fn) {
      const b = button(title, async () => {
        b.disabled = true;
        try { await fn(); } catch (err) { Toast.error(err.message); }
        finally { if (b.isConnected) b.disabled = false; }
      });
      actions.append(b);
      if (reason) { b.disabled = true; actions.append(node("span",reason)); }
    }
    if (p.reportReason === "Login required") {
      actions.append(button("Log in to continue", () => signIn(l.id,false))); return;
    }
    if (!l.owner) {
      if (p.messageReason?.startsWith("Verify")) actions.append(button("Verify your USC email to continue", () => signIn(l.id,true)));
      else {
        action("Message Seller",p.messageReason,async () => {
          const result = await Api.post(url(l.id) + "/conversation",{}, {silent:true});
          if (openId === l.id) location.href = Nav.PAGES.messages + "?conversationId=" + result.conversationId;
        });
        action(data.isSaved ? "Remove from saved" : "Save",p.saveReason,async () => {
          await save(l.id,!data.isSaved); change({id:l.id});
        });
      }
    }
    action("Report listing",p.reportReason,() => ReportDialog.open("LISTING",l.id,l.title));
    if (p.canChangeStatus) panel.append(statusForm(data));
  }

  window.addEventListener("marketplace-change", e => {
    const d = e.detail || {};
    if (openId && (!d.id || (d.id === openId && (!d.version || version == null || d.version > version)))) refresh();
  });
  let delay = 2000, stopped = false, socket, timer;
  function connect() {
    const address = new URL("live",location.href);
    address.protocol = location.protocol === "https:" ? "wss:" : "ws:";
    socket = new WebSocket(address);
    socket.onopen = () => { delay = 2000; change({}); };
    socket.onmessage = e => {
      const event = JSON.parse(e.data);
      if (["listing.created", "listing.updated", "listing.status"].includes(event.type)) change(event.data);
      if (event.type === "review.created" || event.type === "rating.updated") change({});
    };
    socket.onclose = () => {
      if (!stopped) { timer = setTimeout(connect,delay); delay = Math.min(8000,delay*2); }
    };
  }
  window.addEventListener("pagehide", () => { stopped = true; clearTimeout(timer); socket?.close(); });
  window.addEventListener("pageshow", () => { stopped = false; connect(); });
  document.addEventListener("DOMContentLoaded", () => {
    const id = Number(new URLSearchParams(location.search).get("listing"));
    if (Number.isSafeInteger(id) && id > 0) open(id,null);
  });
  return {open,save,label,signIn};
})();
