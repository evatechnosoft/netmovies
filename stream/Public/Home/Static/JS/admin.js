// NetMovies — Yönetim Paneli istemci mantığı
// Katalog + config + sağlık verisini çeker, kaynak/kategori/puan/öne-çıkan
// ayarlarını düzenleyip /api/admin/config'e kaydeder.

const $ = (sel) => document.querySelector(sel);

let CONFIG = null;
let CATALOG = [];

async function jget(url) {
    const r = await fetch(url, { headers: { "X-Requested-With": "fetch" } });
    return r.json();
}

function renderProviders() {
    const box = $("#admin-providers");
    const hidden = new Set(CONFIG.hidden_providers || []);
    box.innerHTML = CATALOG.map((p) => {
        const on = !hidden.has(p.name);
        return `<label class="admin-toggle">
            <input type="checkbox" data-provider="${p.name}" ${on ? "checked" : ""}>
            <span>${p.name}</span>
            <small>${p.main_url || ""}</small>
        </label>`;
    }).join("") || "<p>Kaynak bulunamadı.</p>";
}

function renderCategories() {
    const box = $("#admin-categories");
    const hidden = new Set(CONFIG.hidden_categories || []);
    const all = new Set();
    CATALOG.forEach((p) => (p.categories || []).forEach((c) => all.add(c)));
    const cats = [...all].sort((a, b) => a.localeCompare(b, "tr"));
    box.innerHTML = cats.map((c) => {
        const on = !hidden.has(c);
        return `<label class="admin-toggle">
            <input type="checkbox" data-category="${encodeURIComponent(c)}" ${on ? "checked" : ""}>
            <span>${c}</span>
        </label>`;
    }).join("") || "<p>Kategori bulunamadı.</p>";
}

function renderFeatured() {
    const box = $("#admin-featured");
    const list = CONFIG.featured || [];
    if (!list.length) {
        box.innerHTML = "<p class='admin-muted'>Henüz öne çıkan yok.</p>";
        return;
    }
    box.innerHTML = list.map((f, i) => `<div class="admin-featured-item">
        <span>${f.title || f.url} <small>${f.provider || ""}</small></span>
        <button type="button" class="button button-secondary" data-remove="${i}"><i class="fas fa-trash"></i></button>
    </div>`).join("");
    box.querySelectorAll("[data-remove]").forEach((btn) => {
        btn.addEventListener("click", () => {
            CONFIG.featured.splice(Number(btn.dataset.remove), 1);
            renderFeatured();
        });
    });
}

function renderRating() {
    const r = Number(CONFIG.min_rating || 0);
    $("#admin-min-rating").value = r;
    $("#admin-min-rating-val").textContent = r;
}

function renderProviderUrl() {
    const el = $("#admin-provider-url");
    if (el) el.value = CONFIG.provider_url || "";
    const st = $("#admin-provider-status");
    if (st) st.textContent = CONFIG.provider_url ? "Şu an: uzak sağlayıcı" : "Şu an: yerel motor";
}

function renderHealth(data) {
    const box = $("#admin-health");
    const plugins = (data && data.result && data.result.plugins) || [];
    if (!plugins.length) {
        box.innerHTML = "<p class='admin-muted'>Sağlık verisi alınamadı.</p>";
        return;
    }
    box.innerHTML = plugins.map((p) => {
        const cls = p.ok ? "ok" : "down";
        const dot = p.ok ? "🟢" : "🔴";
        return `<div class="admin-health-item ${cls}">
            <span>${dot} <strong>${p.plugin}</strong></span>
            <small>${p.status}${p.note ? " — " + p.note : ""}</small>
        </div>`;
    }).join("");
}

async function loadHealth(force = false) {
    // force=true → motorun 6 saatlik cache'ini atla, tüm domainleri CANLI yeniden tara.
    $("#admin-health").innerHTML = force
        ? "<p class='admin-muted'>Tüm kaynaklar canlı taranıyor… (birkaç saniye)</p>"
        : "<p class='admin-muted'>Kontrol ediliyor…</p>";
    try {
        renderHealth(await jget("/api/admin/health" + (force ? "?force=1" : "")));
    } catch {
        $("#admin-health").innerHTML = "<p class='admin-muted'>Sağlık kontrolü başarısız.</p>";
    }
}

function collectConfig() {
    const hidden_providers = [];
    document.querySelectorAll("[data-provider]").forEach((cb) => {
        if (!cb.checked) hidden_providers.push(cb.dataset.provider);
    });
    const hidden_categories = [];
    document.querySelectorAll("[data-category]").forEach((cb) => {
        if (!cb.checked) hidden_categories.push(decodeURIComponent(cb.dataset.category));
    });
    return {
        hidden_providers,
        hidden_categories,
        featured: CONFIG.featured || [],
        min_rating: Number($("#admin-min-rating").value || 0),
        provider_url: ($("#admin-provider-url")?.value || "").trim(),
    };
}

async function save() {
    const status = $("#admin-save-status");
    status.textContent = "Kaydediliyor…";
    try {
        const r = await fetch("/api/admin/config", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(collectConfig()),
        });
        const data = await r.json();
        if (data.ok) {
            CONFIG = data.config;
            status.textContent = "✓ Kaydedildi";
            setTimeout(() => (status.textContent = ""), 2500);
        } else {
            status.textContent = "Hata: " + (data.error || "bilinmeyen");
        }
    } catch (e) {
        status.textContent = "Kaydetme başarısız";
    }
}

async function init() {
    const shell = $("#admin-shell");
    try {
        const [cfg, cat] = await Promise.all([
            jget("/api/admin/config"),
            jget("/api/admin/catalog"),
        ]);
        CONFIG = cfg;
        CATALOG = (cat && cat.plugins) || [];
    } catch {
        $("#admin-loading").textContent = "Veri yüklenemedi. Motor çalışıyor mu?";
        return;
    }

    $("#admin-loading").hidden = true;
    shell.querySelectorAll("section.admin-card, .admin-actions").forEach((el) => (el.hidden = false));
    shell.dataset.status = "ready";

    renderProviders();
    renderCategories();
    renderFeatured();
    renderRating();
    renderProviderUrl();
    loadHealth();
    loadRepos();

    $("#admin-min-rating").addEventListener("input", (e) => {
        $("#admin-min-rating-val").textContent = e.target.value;
    });
    $("#admin-save").addEventListener("click", save);
    $("#admin-health-refresh").addEventListener("click", () => loadHealth(true));
    $("#admin-repo-add")?.addEventListener("click", addRepo);

    $("#admin-provider-watchbuddy")?.addEventListener("click", () => {
        // NOT: kök adres (/api/v1 EKLEME) — client uç noktaları kendi ekler.
        $("#admin-provider-url").value = "https://stream.watchbuddy.tv";
        $("#admin-provider-status").textContent = "Kaydet + sayfayı yenile → uzak sağlayıcı aktif olur (203 eklenti).";
    });
    $("#admin-provider-clear")?.addEventListener("click", () => {
        $("#admin-provider-url").value = "";
        $("#admin-provider-status").textContent = "Kaydet + sayfayı yenile → yerel motora döner.";
    });
}

async function loadRepos() {
    const box = $("#admin-repos-list");
    if (!box) return;
    box.innerHTML = "<p class='admin-muted'>Eklenti havuzları taranıyor…</p>";
    try {
        const data = await jget("/api/admin/repos");
        const repos = (data && data.repos) || [];
        if (!repos.length) {
            box.innerHTML = "<p class='admin-muted'>Kayıtlı eklenti havuzu yok.</p>";
            return;
        }
        box.innerHTML = repos.map((r, idx) => {
            const pluginCount = (r.plugins || []).length;
            const errBadge = r.error ? `<span style="color:#ef4444; font-size:.8rem;">(${r.error})</span>` : "";
            const pluginNames = (r.plugins || []).slice(0, 10).map(p => `<span style="background:rgba(255,255,255,.07); padding:2px 6px; border-radius:4px; font-size:.75rem;">${p.name}</span>`).join(" ");
            const moreBadge = pluginCount > 10 ? `<span style="color:var(--muted); font-size:.75rem;">+${pluginCount - 10} eklenti daha</span>` : "";

            return `<div class="admin-card" style="margin-bottom:.5rem; padding:.8rem 1rem; background:rgba(255,255,255,.02);">
                <div style="display:flex; align-items:center; justify-content:space-between; gap:.8rem;">
                    <div>
                        <strong>${r.name}</strong> ${errBadge}
                        <div style="color:var(--muted); font-size:.78rem; word-break:break-all;">${r.url}</div>
                    </div>
                    <button type="button" class="button button-secondary" data-remove-repo="${idx}"><i class="fas fa-trash"></i></button>
                </div>
                <div style="margin-top:.6rem; display:flex; flex-wrap:wrap; gap:.35rem; align-items:center;">
                    ${pluginNames} ${moreBadge}
                </div>
            </div>`;
        }).join("");

        box.querySelectorAll("[data-remove-repo]").forEach(btn => {
            btn.addEventListener("click", async () => {
                const idx = Number(btn.dataset.removeRepo);
                const currentRepos = (CONFIG.custom_repos || []);
                currentRepos.splice(idx, 1);
                await fetch("/api/admin/repos", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ repos: currentRepos }),
                });
                CONFIG.custom_repos = currentRepos;
                loadRepos();
            });
        });
    } catch {
        box.innerHTML = "<p class='admin-muted'>Eklenti havuzları yüklenemedi.</p>";
    }
}

async function addRepo() {
    const input = $("#admin-repo-url");
    let url = (input?.value || "").trim();
    if (!url) return;
    
    // GitHub repo linki verildiğinde otomatik raw repo.json adresine çevir
    if (url.includes("github.com") && !url.includes("raw.githubusercontent.com")) {
        url = url.replace("github.com", "raw.githubusercontent.com").replace(/\/$/, "") + "/master/repo.json";
    }

    const currentRepos = CONFIG.custom_repos || [];
    currentRepos.push({
        name: url.split("/")[4] || "Özel Repo",
        url: url,
        enabled: true,
    });

    await fetch("/api/admin/repos", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ repos: currentRepos }),
    });

    CONFIG.custom_repos = currentRepos;
    if (input) input.value = "";
    loadRepos();
}

init();
