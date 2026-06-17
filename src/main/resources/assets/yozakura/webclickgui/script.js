(function () {
  const TOKEN = window.__YOZAKURA_TOKEN__ || "";
  const PORT = window.__YOZAKURA_PORT__ || "";

  const els = {
    statusText: document.getElementById("statusText"),
    clientName: document.getElementById("clientName"),
    clientInfo: document.getElementById("clientInfo"),
    viewToggleButton: document.getElementById("viewToggleButton"),
    themeToggleButton: document.getElementById("themeToggleButton"),
    categoryList: document.getElementById("categoryList"),
    content: document.getElementById("content"),
    detailPanel: document.getElementById("detailPanel"),
    moduleGrid: document.getElementById("moduleGrid"),
    detailIcon: document.getElementById("detailIcon"),
    detailName: document.getElementById("detailName"),
    detailDesc: document.getElementById("detailDesc"),
    detailCategory: document.getElementById("detailCategory"),
    detailKey: document.getElementById("detailKey"),
    toggleButton: document.getElementById("toggleButton"),
    closeDetailButton: document.getElementById("closeDetailButton"),
    prevModuleButton: document.getElementById("prevModuleButton"),
    nextModuleButton: document.getElementById("nextModuleButton"),
    bindInput: document.getElementById("bindInput"),
    valueList: document.getElementById("valueList"),
    petals: document.getElementById("petals")
  };

  const state = {
    data: null,
    selectedCategory: "ALL",
    selectedModule: "",
    detailOpen: false,
    theme: loadSetting("webclickgui-theme", "dark"),
    viewMode: loadSetting("webclickgui-view", "grid"),
    search: "",
    loading: false,
    requestId: 0,
    lastStateText: "",
    pendingScroll: null
  };

  const keyCodeMap = {
    Backquote: "GRAVE",
    Minus: "MINUS",
    Equal: "EQUALS",
    BracketLeft: "LBRACKET",
    BracketRight: "RBRACKET",
    Backslash: "BACKSLASH",
    Semicolon: "SEMICOLON",
    Quote: "APOSTROPHE",
    Comma: "COMMA",
    Period: "PERIOD",
    Slash: "SLASH",
    Space: "SPACE",
    Enter: "RETURN",
    NumpadEnter: "NUMPADENTER",
    Tab: "TAB",
    Escape: "ESCAPE",
    Backspace: "BACK",
    Delete: "DELETE",
    Insert: "INSERT",
    Home: "HOME",
    End: "END",
    PageUp: "PRIOR",
    PageDown: "NEXT",
    ArrowUp: "UP",
    ArrowDown: "DOWN",
    ArrowLeft: "LEFT",
    ArrowRight: "RIGHT",
    ShiftLeft: "LSHIFT",
    ShiftRight: "RSHIFT",
    ControlLeft: "LCONTROL",
    ControlRight: "RCONTROL",
    AltLeft: "LMENU",
    AltRight: "RMENU",
    CapsLock: "CAPITAL"
  };

  function init() {
    applyTheme();
    applyViewMode();
    setupPetals();
    bindStaticEvents();
    setInterval(refreshState, 650);
    refreshState();
  }

  function setupPetals() {
    for (let i = 0; i < 32; i++) {
      const petal = document.createElement("span");
      petal.className = "petal";
      petal.style.left = `${Math.random() * 100}%`;
      petal.style.animationDuration = `${10 + Math.random() * 16}s`;
      petal.style.animationDelay = `${-Math.random() * 18}s`;
      petal.style.opacity = `${0.14 + Math.random() * 0.34}`;
      petal.style.transform = `scale(${0.72 + Math.random() * 0.82})`;
      els.petals.appendChild(petal);
    }
  }

  function bindStaticEvents() {
    els.viewToggleButton.addEventListener("click", () => {
      setViewMode(state.viewMode === "grid" ? "list" : "grid");
    });
    els.themeToggleButton.addEventListener("click", () => {
      state.theme = state.theme === "dark" ? "light" : "dark";
      saveSetting("webclickgui-theme", state.theme);
      applyTheme();
    });
    els.toggleButton.addEventListener("click", (event) => {
      event.preventDefault();
      const module = selectedModule();
      if (module) {
        const next = !module.state;
        rememberScroll();
        applyModuleState(module.name, next);
        postJson("/api/module/toggle", { module: module.name, state: next });
      }
    });
    els.closeDetailButton.addEventListener("click", closeDetail);
    els.prevModuleButton.addEventListener("click", () => switchModule(-1));
    els.nextModuleButton.addEventListener("click", () => switchModule(1));
    els.bindInput.addEventListener("keydown", onBindKeyDown);
  }

  function setViewMode(mode) {
    if (state.viewMode === mode) {
      return;
    }
    state.viewMode = mode;
    saveSetting("webclickgui-view", state.viewMode);
    applyViewMode();
  }

  function applyTheme() {
    const light = state.theme === "light";
    document.body.classList.toggle("theme-light", light);
    els.themeToggleButton.textContent = light ? "☀" : "☾";
    els.themeToggleButton.setAttribute("aria-pressed", String(light));
  }

  function applyViewMode() {
    const list = state.viewMode === "list";
    els.moduleGrid.classList.toggle("list-view", list);
    els.viewToggleButton.textContent = list ? "☷" : "▦";
    els.viewToggleButton.setAttribute("aria-pressed", String(list));
  }

  async function refreshState() {
    if (state.loading) {
      return;
    }
    state.loading = true;
    const id = ++state.requestId;
    try {
      const response = await fetch(`/api/state?token=${encodeURIComponent(TOKEN)}`, {
        headers: { "X-Yozakura-Token": TOKEN },
        cache: "no-store"
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const text = await response.text();
      if (id !== state.requestId || (state.data && text === state.lastStateText)) {
        return;
      }
      const next = JSON.parse(text);
      state.lastStateText = text;
      state.data = next;
      if (state.selectedModule && !findModule(state.selectedModule)) {
        state.selectedModule = "";
        state.detailOpen = false;
      }
      syncHeader();
      render();
      restoreScroll();
    } catch (error) {
      syncOffline();
    } finally {
      state.loading = false;
    }
  }

  function syncHeader() {
    const data = state.data;
    els.statusText.textContent = "Connected";
    els.clientName.textContent = data.username || "Sakura User";
    els.clientInfo.textContent = PORT ? `127.0.0.1:${PORT}` : `127.0.0.1:${data.port || ""}`;
  }

  function syncOffline() {
    els.statusText.textContent = "Offline";
    els.clientInfo.textContent = PORT ? `127.0.0.1:${PORT}` : "Waiting for service";
  }

  function render() {
    const data = state.data;
    if (!data) {
      els.moduleGrid.innerHTML = `<div class="loading">Loading modules...</div>`;
      els.valueList.innerHTML = `<div class="empty-state">Waiting for local service.</div>`;
      return;
    }
    const categories = data.categories || [];
    const modules = data.modules || [];
    syncDetailPanelState();
    renderCategories(categories);
    renderModules(modules);
    renderDetail(modules);
  }

  function allCategory() {
    return {
      id: "ALL",
      name: "All",
      displayName: "All Modules",
      count: state.data?.moduleCount ?? 0,
      enabled: state.data?.enabledCount ?? 0
    };
  }

  function renderCategories(categories) {
    const list = [allCategory()].concat(categories);
    els.categoryList.innerHTML = list.map(renderSideCategory).join("");
    els.categoryList.querySelectorAll("[data-category]").forEach((button) => {
      button.addEventListener("click", () => {
        state.selectedCategory = button.getAttribute("data-category") || "ALL";
        render();
      });
    });
  }

  function renderSideCategory(category) {
    const active = state.selectedCategory === category.id;
    return `
      <button class="side-link ${active ? "active" : ""}" data-category="${escapeHtml(category.id)}" type="button">
        <span>${escapeHtml(iconForCategory(category.id))}</span>
        <strong>${escapeHtml(category.displayName || category.name || category.id)}</strong>
        <em>${escapeHtml(String(category.enabled ?? 0))}/${escapeHtml(String(category.count ?? 0))}</em>
      </button>
    `;
  }

  function renderModules(modules) {
    const scrollTop = els.moduleGrid.scrollTop || 0;
    const list = filteredModules(modules);
    if (!list.length) {
      els.moduleGrid.innerHTML = `<div class="loading">No modules found.</div>`;
      els.moduleGrid.scrollTop = scrollTop;
      return;
    }
    if (state.selectedModule && !list.some((module) => module.name === state.selectedModule)) {
      state.selectedModule = "";
      state.detailOpen = false;
    }
    els.moduleGrid.innerHTML = list.map(renderModuleCard).join("");
    els.moduleGrid.scrollTop = scrollTop;
    els.moduleGrid.querySelectorAll("[data-module-card]").forEach((card) => {
      card.addEventListener("click", (event) => {
        event.preventDefault();
        const name = card.getAttribute("data-module-card");
        const module = findModule(name);
        if (!module) {
          return;
        }
        const action = event.target.closest("[data-action]");
        if (action && action.getAttribute("data-action") === "toggle") {
          const next = !module.state;
          rememberScroll();
          applyModuleState(module.name, next);
          postJson("/api/module/toggle", { module: module.name, state: next });
          return;
        }
        if (action && action.getAttribute("data-action") === "settings") {
          state.selectedModule = name;
          state.detailOpen = true;
          render();
          return;
        }
        state.selectedModule = name;
        state.detailOpen = true;
        render();
      });
    });
  }

  function renderModuleCard(module) {
    const enabled = !!module.state;
    const selected = state.selectedModule === module.name;
    const icon = iconForModule(module);
    return `
      <article class="module-card ${enabled ? "enabled" : ""} ${selected ? "selected" : ""}" data-module-card="${escapeHtml(module.name)}">
        <div class="module-top">
          <div class="module-icon">${escapeHtml(icon)}</div>
          <div class="module-name">${escapeHtml(module.displayName || module.name)}</div>
          <button class="switch ${enabled ? "on" : ""}" type="button" data-action="toggle" aria-label="Toggle module"></button>
        </div>
        <div class="module-desc">${escapeHtml(module.description || "No description.")}</div>
        <div class="module-footer">
          <span class="pill">${escapeHtml(module.categoryName || module.category || "")}</span>
          <button class="settings-button" type="button" data-action="settings" aria-label="Open settings">⚙</button>
        </div>
      </article>
    `;
  }

  function renderDetail(modules) {
    const module = selectedModule();
    if (!module || !state.detailOpen) {
      els.detailIcon.textContent = "✦";
      els.detailName.textContent = "Select a module";
      els.detailDesc.textContent = "Choose a module to edit settings.";
      els.detailCategory.textContent = "Category";
      els.detailKey.textContent = "None";
      els.toggleButton.className = "switch big";
      els.bindInput.value = "";
      els.valueList.innerHTML = `<div class="empty-state">No module selected.</div>`;
      syncDetailPanelState();
      return;
    }
    state.selectedModule = module.name;
    els.detailIcon.textContent = iconForModule(module);
    els.detailName.textContent = module.displayName || module.name;
    els.detailDesc.textContent = module.description || "No description.";
    els.detailCategory.textContent = module.categoryName || module.category || "Category";
    els.detailKey.textContent = module.keyName || "None";
    els.toggleButton.className = `switch big ${module.state ? "on" : ""}`;
    els.bindInput.value = module.keyName === "None" ? "" : module.keyName;
    els.valueList.innerHTML = renderValues(module);
    bindValueEvents(module);
    syncDetailPanelState();
  }

  function closeDetail() {
    state.detailOpen = false;
    syncDetailPanelState();
    renderModules(state.data?.modules || []);
  }

  function syncDetailPanelState() {
    const open = !!state.detailOpen && !!selectedModule();
    els.content.classList.toggle("detail-open", open);
    els.content.classList.toggle("detail-closed", !open);
    els.detailPanel.setAttribute("aria-hidden", open ? "false" : "true");
  }

  function renderValues(module) {
    const values = module.values || [];
    if (!values.length) {
      return `<div class="empty-state">No settings on this module.</div>`;
    }
    return values.map((value) => {
      const type = String(value.type || "").toLowerCase();
      if (type === "boolean") {
        return `
          <div class="value-card">
            <div class="value-head">
              <strong>${escapeHtml(value.displayName || value.name)}</strong>
              <button class="switch ${asBoolean(value.current) ? "on" : ""}" type="button" data-value-toggle="${escapeHtml(value.name)}"></button>
            </div>
            <div class="value-current">${escapeHtml(asBoolean(value.current) ? "Enabled" : "Disabled")}</div>
          </div>
        `;
      }
      if (type === "number") {
        const min = Number(value.min ?? 0);
        const max = Number(value.max ?? 1);
        const step = Number(value.step ?? 1);
        const current = Number(value.current ?? 0);
        return `
          <div class="value-card">
            <div class="value-head">
              <strong>${escapeHtml(value.displayName || value.name)}</strong>
            </div>
            <div class="number-row">
              <input
                class="slider"
                type="range"
                min="${escapeHtml(String(min))}"
                max="${escapeHtml(String(max))}"
                step="${escapeHtml(String(step))}"
                value="${escapeHtml(String(current))}"
                data-value-slider="${escapeHtml(value.name)}"
              />
              <span class="number-box" data-number-preview="${escapeHtml(value.name)}">${escapeHtml(formatNumber(current))}</span>
            </div>
          </div>
        `;
      }
      if (type === "mode") {
        return `
          <div class="value-card">
            <div class="value-head">
              <strong>${escapeHtml(value.displayName || value.name)}</strong>
              <span class="value-current">${escapeHtml(String(value.current ?? ""))}</span>
            </div>
            <div class="mode-options">
              ${(value.options || []).map((option) => `
                <button
                  class="mode-button ${String(option) === String(value.current) ? "active" : ""}"
                  type="button"
                  data-value-mode="${escapeHtml(value.name)}"
                  data-option="${escapeHtml(String(option))}"
                >${escapeHtml(String(option))}</button>
              `).join("")}
            </div>
          </div>
        `;
      }
      return `
        <div class="value-card">
          <div class="value-head">
            <strong>${escapeHtml(value.displayName || value.name)}</strong>
            <span class="value-current">${escapeHtml(String(value.current ?? ""))}</span>
          </div>
        </div>
      `;
    }).join("");
  }

  function bindValueEvents(module) {
    els.valueList.querySelectorAll("[data-value-toggle]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        const value = getValue(module, button.getAttribute("data-value-toggle"));
        if (value) {
          rememberScroll();
          postValue(module, value, !asBoolean(value.current));
        }
      });
    });
    els.valueList.querySelectorAll("[data-value-slider]").forEach((input) => {
      input.addEventListener("input", () => {
        const preview = els.valueList.querySelector(`[data-number-preview="${cssEscape(input.getAttribute("data-value-slider"))}"]`);
        if (preview) {
          preview.textContent = formatNumber(Number(input.value));
        }
      });
      input.addEventListener("change", () => {
        const value = getValue(module, input.getAttribute("data-value-slider"));
        if (value) {
          const number = Number(input.value);
          rememberScroll();
          postValue(module, value, Number.isFinite(number) ? number : value.current);
        }
      });
    });
    els.valueList.querySelectorAll("[data-value-mode]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.preventDefault();
        const value = getValue(module, button.getAttribute("data-value-mode"));
        if (value) {
          rememberScroll();
          postValue(module, value, button.getAttribute("data-option") || button.textContent.trim());
        }
      });
    });
  }

  function filteredModules(modules) {
    return modules.filter((module) => {
      if (state.selectedCategory !== "ALL" && module.category !== state.selectedCategory) {
        return false;
      }
      return true;
    });
  }

  function switchModule(direction) {
    if (!state.data || !state.data.modules || !state.data.modules.length) {
      return;
    }
    const list = filteredModules(state.data.modules);
    if (!list.length) {
      return;
    }
    let index = list.findIndex((module) => module.name === state.selectedModule);
    index = index < 0 ? 0 : (index + direction + list.length) % list.length;
    state.selectedModule = list[index].name;
    render();
  }

  function selectedModule() {
    return findModule(state.selectedModule);
  }

  function applyModuleState(name, enabled) {
    const module = findModule(name);
    if (!module) {
      return;
    }
    module.state = !!enabled;
    state.lastStateText = "";
    render();
    restoreScroll();
  }

  function findModule(name) {
    if (!state.data || !state.data.modules || !name) {
      return null;
    }
    return state.data.modules.find((module) => module.name === name) || null;
  }

  function getValue(module, valueName) {
    return (module.values || []).find((value) => value.name === valueName) || null;
  }

  async function postValue(module, value, next) {
    await postJson("/api/value/set", {
      module: module.name,
      value: value.name,
      next
    });
  }

  async function postJson(path, payload) {
    try {
      await fetch(`${path}?token=${encodeURIComponent(TOKEN)}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Yozakura-Token": TOKEN
        },
        body: JSON.stringify(payload || {})
      });
    } catch (error) {
      console.error(error);
    } finally {
      setTimeout(refreshState, 80);
    }
  }

  function rememberScroll() {
    state.pendingScroll = {
      pageX: window.scrollX || 0,
      pageY: window.scrollY || 0,
      modules: els.moduleGrid ? els.moduleGrid.scrollTop : 0,
      values: els.valueList ? els.valueList.scrollTop : 0
    };
  }

  function restoreScroll() {
    const scroll = state.pendingScroll;
    if (!scroll) {
      return;
    }
    requestAnimationFrame(() => {
      if (els.moduleGrid) {
        els.moduleGrid.scrollTop = scroll.modules;
      }
      if (els.valueList) {
        els.valueList.scrollTop = scroll.values;
      }
      window.scrollTo(scroll.pageX, scroll.pageY);
      requestAnimationFrame(() => {
        if (els.moduleGrid) {
          els.moduleGrid.scrollTop = scroll.modules;
        }
        if (els.valueList) {
          els.valueList.scrollTop = scroll.values;
        }
        window.scrollTo(scroll.pageX, scroll.pageY);
        state.pendingScroll = null;
      });
    });
  }

  function onBindKeyDown(event) {
    event.preventDefault();
    const module = selectedModule();
    if (!module) {
      return;
    }
    if (event.key === "Escape") {
      els.bindInput.blur();
      return;
    }
    if (event.key === "Backspace") {
      els.bindInput.value = "";
      postJson("/api/key/set", { module: module.name, keyName: "NONE" });
      return;
    }
    const mapped = mapKey(event);
    if (mapped) {
      els.bindInput.value = mapped.label;
      postJson("/api/key/set", { module: module.name, keyName: mapped.label });
    }
  }

  function mapKey(event) {
    const code = event.code || "";
    if (code in keyCodeMap) {
      return { label: keyCodeMap[code] };
    }
    if (/^Key[A-Z]$/.test(code)) {
      return { label: code.slice(3) };
    }
    if (/^Digit[0-9]$/.test(code)) {
      return { label: code.slice(5) };
    }
    if (/^F([1-9]|1[0-9]|2[0-4])$/.test(code)) {
      return { label: code };
    }
    return null;
  }

  function iconForCategory(id) {
    const map = {
      Combat: "⚔",
      Movement: "↔",
      Render: "◐",
      Player: "⌂",
      World: "✧",
      Other: "✦",
      Global: "⚙",
      Config: "▣",
      ALL: "⌘"
    };
    return map[id] || "✦";
  }

  function iconForModule(module) {
    const name = String(module.name || "").toLowerCase();
    if (name.includes("aura")) return "⚔";
    if (name.includes("velocity")) return "≋";
    if (name.includes("scaffold")) return "□";
    if (name.includes("strafe")) return "◎";
    if (name.includes("slow") || name.includes("speed") || name.includes("sprint")) return "↝";
    if (name.includes("clicker")) return "⌁";
    if (name.includes("chest") || name.includes("inventory")) return "▤";
    if (name.includes("esp")) return "◉";
    if (name.includes("xray")) return "◆";
    if (name.includes("armor")) return "▥";
    if (name.includes("hitbox")) return "◇";
    if (name.includes("reach")) return "↗";
    if (name.includes("hud") || name.includes("gui") || name.includes("chams")) return "◐";
    if (name.includes("config") || name.includes("load") || name.includes("save")) return "⚙";
    return iconForCategory(module.category);
  }

  function asBoolean(value) {
    return value === true || value === "true" || value === 1 || value === "1";
  }

  function formatNumber(value) {
    if (!Number.isFinite(value)) {
      return "0";
    }
    return Math.abs(value - Math.round(value)) < 0.05 ? String(Math.round(value)) : value.toFixed(1).replace(/\.0$/, "");
  }

  function cssEscape(value) {
    if (window.CSS && CSS.escape) {
      return CSS.escape(value || "");
    }
    return String(value || "").replace(/["\\]/g, "\\$&");
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");
  }

  function loadSetting(key, fallback) {
    try {
      return localStorage.getItem(key) || fallback;
    } catch (ignored) {
      return fallback;
    }
  }

  function saveSetting(key, value) {
    try {
      localStorage.setItem(key, value);
    } catch (ignored) {
    }
  }

  window.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && document.activeElement !== els.bindInput) {
      els.bindInput.blur();
    }
  });

  init();
})();
