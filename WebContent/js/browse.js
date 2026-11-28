(() => {
  const form = document.querySelector("#search");
  const cards = document.querySelector("#cards");
  const summary = document.querySelector("#summary");
  const prev = document.querySelector("#prev"), next = document.querySelector("#next");
  const retry = document.querySelector("#retry");
  let request = 0, refreshTimer;
  const params = () => new URLSearchParams(location.search);

  function restore() {
    const query = params();
    for (const input of form.elements) {
      if (!input.name) continue;
      if (input.type === "checkbox") input.checked = query.get(input.name) === "true";
      else input.value = query.get(input.name) || (input.name === "sort" ? "newest" : "");
    }
  }

  function submit(page) {
    if (!form.reportValidity()) return;
    const query = new URLSearchParams(new FormData(form));
    for (const [key, val] of [...query]) if (!val) query.delete(key);
    query.set("page", page);
    history.pushState(null, "", "?" + query);
    load();
  }

  function renderCard(l, me) {
    const card = document.createElement("article");
    card.className = "card" + (l.status === "SOLD" ? " sold" : "");
    if (l.firstPhotoUrl) {
      const img = document.createElement("img");
      img.src = l.firstPhotoUrl; img.alt = ""; img.loading = "lazy"; card.append(img);
    } else {
      const empty = document.createElement("div");
      empty.className = "card-placeholder"; empty.textContent = "No photo"; card.append(empty);
    }
    const body = document.createElement("div");
    body.className = "card-body";
    const open = document.createElement("button");
    open.className = "listing-open"; open.textContent = l.title;
    open.onclick = () => ListingPopup.open(l.listingId, open);
    const price = document.createElement("p");
    price.textContent = "$" + l.priceDollars + " · " + ListingPopup.label(l.status);
    const meta = document.createElement("p");
    meta.className = "card-meta";
    meta.textContent = [l.category, l.sellerFirstName, new Date(l.createdAt).toLocaleString()].join(" · ");
    body.append(open, price, meta);
    if (me?.id !== l.sellerId) {
      const heart = document.createElement("button");
      heart.className = "heart";
      const draw = () => {
        heart.textContent = l.isSaved ? "♥ Saved" : "♡ Save";
        heart.setAttribute("aria-pressed", String(l.isSaved));
      };
      draw();
      heart.onclick = async () => {
        if (!me?.can?.SAVE) { ListingPopup.signIn(l.listingId, Boolean(me)); return; }
        heart.disabled = true;
        try {
          const result = await ListingPopup.save(l.listingId, !l.isSaved);
          l.isSaved = result.isSaved; draw();
        } catch (err) { Toast.error(err.message); }
        finally { heart.disabled = false; }
      };
      body.append(heart);
    }
    card.append(body);
    return card;
  }

  async function load() {
    const ticket = ++request;
    retry.hidden = true;
    Api.clearFormErrors(form);
    const query = params(); query.delete("listing");
    try {
      const [result, me] = await Promise.all([Api.get("api/listings?" + query, {silent:true}), Nav.ready]);
      if (ticket !== request) return;
      cards.replaceChildren(...result.cards.map(row => renderCard(row, me)));
      summary.textContent = result.totalCount ? result.totalCount + " listings · Page " + result.pageNumber :
          "No results. Try a different keyword or clear the price range.";
      prev.disabled = result.pageNumber <= 1; next.disabled = !result.hasNextPage;
      prev.onclick = () => submit(result.pageNumber - 1); next.onclick = () => submit(result.pageNumber + 1);
      const url = new URL(location.href);
      if (url.searchParams.has("page")) {
        url.searchParams.set("page", result.pageNumber); history.replaceState(null, "", url);
      }
    } catch (err) {
      if (ticket !== request) return;
      Api.showFormErrors(form, err); retry.hidden = false;
    }
  }

  form.onsubmit = e => { e.preventDefault(); submit(1); };
  form.onchange = e => { if (e.target.name !== "keyword") submit(1); };
  document.querySelector("#clear").onclick = () => {
    for (const input of form.querySelectorAll("details [name]")) {
      if (input.type === "checkbox") input.checked = false; else input.value = "";
    }
    submit(1);
  };
  retry.onclick = load;
  window.addEventListener("popstate", () => { restore(); load(); });
  window.addEventListener("marketplace-change", () => {
    clearTimeout(refreshTimer); refreshTimer = setTimeout(load, 80);
  });
  restore(); load();
})();
