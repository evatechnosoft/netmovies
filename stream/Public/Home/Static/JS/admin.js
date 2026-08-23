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

async function loadHealth() {
    $("#admin-health").innerHTML = "<p class='admin-muted'>Kontrol ediliyor…</p>";
    try {
        renderHealth(await jget("/api/admin/health"));
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
    loadHealth();

    $("#admin-min-rating").addEventListener("input", (e) => {
        $("#admin-min-rating-val").textContent = e.target.value;
    });
    $("#admin-save").addEventListener("click", save);
    $("#admin-health-refresh").addEventListener("click", loadHealth);
}

init();
