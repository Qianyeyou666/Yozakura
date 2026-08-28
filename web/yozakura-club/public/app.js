const fallback = {
  modules: [
    { name: "AimAssist", category: "Combat", description: "Configurable target assistance with controlled pitch behavior." },
    { name: "AutoClicker", category: "Combat", description: "Configurable click timing while the primary button is held." },
    { name: "Backtrack", category: "Combat", description: "Target evaluation against recent historical positions." },
    { name: "Velocity", category: "Combat", description: "Configurable knockback response strategies." },
    { name: "BridgeAssist", category: "Movement", description: "Input and edge assistance designed for bridging." },
    { name: "Sprint", category: "Movement", description: "Maintains consistent movement and sprint state." },
    { name: "TargetESP", category: "Render", description: "Highlights the current target with a unified palette." },
    { name: "HUD", category: "Render", description: "Composable and movable Night Bloom HUD elements." },
    { name: "Config Manager", category: "Config", description: "Save, load, and share .yzk profiles." }
  ],
  plans: [
    { id: "quarter", name: "90 Days", price: null, period: "90 days", available: false, featured: false, perks: ["Full client", "1 device reset monthly", "Priority support"] },
    { id: "month", name: "30 Days", price: 15.55, period: "30 days", available: true, featured: true, perks: ["Full client", "Bind 1 device", "Community support"] },
    { id: "lifetime", name: "Lifetime", price: null, period: "one-time", available: false, featured: false, perks: ["Full client", "2 device resets monthly", "Early features", "Long-term updates"] }
  ],
  release: { version: "1.5.0", minecraft: "1.8.9", available: false, downloadUrl: null }
};

const translations = {
  en: {
    "meta.title": "Yozakura — Lightweight, without compromise",
    "meta.description": "Yozakura is a lightweight client experience built for Minecraft Forge 1.8.9.",
    "nav.client": "Client", "nav.features": "Features", "nav.configs": "Configs", "nav.pricing": "Pricing",
    "auth.login": "Log in", "auth.register": "Create account", "auth.note": "Sign in to access your account, protected downloads, and cloud configs.",
    "auth.username": "Username", "auth.password": "Password", "auth.noAccount": "No account? Create one", "auth.hasAccount": "Already registered? Return to login",
    "auth.connecting": "Connecting…", "auth.sessionExpired": "Your session has expired. Please log in again.",
    "hero.title": "Lightweight,<br><em>without compromise.</em>", "hero.description": "A native Minecraft client experience. Modules, settings, and configs live in one restrained and responsive ClickGUI panel.",
    "hero.download": "Download client", "hero.preview": "Explore the client", "hero.compatible": "Compatible",
    "panel.configManager": "Config Manager", "panel.settings": "Settings", "panel.search": "Search", "panel.noKey": "No key", "panel.toggle": "Toggle", "panel.hold": "Hold", "panel.visible": "Visible", "panel.hidden": "Hidden",
    "panel.current": "Current", "panel.local": "Local", "panel.cloud": "Cloud", "panel.save": "Save", "panel.load": "Load", "panel.refresh": "Refresh", "panel.language": "Language", "panel.palette": "Palette", "panel.glass": "Glass background", "panel.caption": "Try categories, modules, switches, and settings",
    "features.label": "REIMAGINED FROM A PUBLIC INFORMATION ARCHITECTURE", "features.title": "One interface,<em> across every touchpoint.</em>", "features.description": "The website is not a separate product. It carries the client's layout, spacing, color, and interaction language into every visit.",
    "features.panelTitle": "Native and consistent.", "features.panelBody": "A two-column panel, clear categories, dedicated settings, and compact controls preserve the client's rhythm.",
    "features.customTitle": "Precise down to every control.", "features.customBody": "Values, modes, binds, visibility, and color retain one shared meaning.", "features.configTitle": "Save, sync, and share.", "features.configBody": "Local .yzk profiles stay available, while signed-in accounts can sync to a private cloud space.", "features.newConfig": "+ New config",
    "modules.title": "The capabilities you need,<br><em>right where they belong.</em>", "modules.description": "Organized around real client categories, without oversized effects obscuring the information.", "modules.all": "All", "modules.combat": "Combat", "modules.movement": "Movement", "modules.render": "Render",
    "pricing.label": "TRANSPARENT PRICING", "pricing.title": "Choose the plan that fits.", "pricing.description": "Every available plan includes the full client and future versions.", "pricing.featured": "AVAILABLE", "pricing.choose": "Choose", "pricing.unavailable": "Not for sale", "pricing.orderCreated": "Order created (payment pending)", "pricing.loginRequired": "Log in before creating a plan order.",
    "closing.title": "Ready to open<br><em>your Panel?</em>",
    "account.logout": "Log out", "account.overviewLabel": "MEMBERSHIP", "account.overviewTitle": "Subscription overview", "account.active": "Active", "account.inactive": "Inactive", "account.plan": "Plan", "account.expiry": "Expires", "account.remaining": "Time remaining", "account.notBound": "Not bound", "account.bound": "Device bound", "account.notActivated": "Not activated", "account.free": "Free", "account.pendingActivation": "Starts with first client login", "account.noHwid": "No device has been bound",
    "download.title": "Download Yozakura", "download.description": "The protected release is available only to signed-in accounts with an active subscription.", "download.version": "Version", "download.environment": "Environment", "download.ready": "Download Yozakura", "download.preparing": "Build in progress", "download.waiting": "The verified build is being prepared.", "download.subscriptionRequired": "An active subscription is required to download the client.", "download.starting": "Preparing download…", "download.started": "Download started.", "download.unavailable": "The client is temporarily unavailable.", "download.offline": "Unable to reach the download service.", "download.loginRequired": "Log in before downloading the client.",
    "redeem.title": "Activate a subscription", "redeem.description": "Enter a one-time subscription code linked to this website account.", "redeem.code": "Subscription code", "redeem.activate": "Activate subscription", "redeem.required": "Enter a subscription code.", "redeem.activating": "Activating subscription…", "redeem.success": "Subscription activated. You can now use this account in the client.", "redeem.loginRequired": "Log in before activating a client subscription.",
    "module.emptyTitle": "No matching modules", "module.emptyBody": "Try another category or search term", "module.settings": "Settings", "module.switch": "Toggle", "module.enable": "Enable module", "module.mode": "Mode", "module.strength": "Strength",
    "category.Combat": "Combat", "category.Player": "Player", "category.Movement": "Movement", "category.Render": "Render", "category.Modules": "Modules",
    "time.day": "d", "time.hour": "h", "time.minute": "m", "error.network": "Unable to reach the server."
  },
  zh: {
    "meta.title": "Yozakura — 轻量，但不妥协", "meta.description": "Yozakura - 为 Minecraft Forge 1.8.9 打造的轻量客户端。",
    "nav.client": "客户端", "nav.features": "特性", "nav.configs": "配置", "nav.pricing": "价格",
    "auth.login": "登录", "auth.register": "创建账号", "auth.note": "登录后可访问个人信息、受保护下载与云配置。", "auth.username": "用户名", "auth.password": "密码", "auth.noAccount": "没有账号？创建一个", "auth.hasAccount": "已有账号？返回登录", "auth.connecting": "正在连接…", "auth.sessionExpired": "登录状态已失效，请重新登录。",
    "hero.title": "轻量，<br><em>但不妥协。</em>", "hero.description": "为 Minecraft 打造的原生客户端体验。模块、设置和配置都收进一块克制、快速的 ClickGUI Panel。", "hero.download": "下载客户端", "hero.preview": "查看客户端", "hero.compatible": "兼容",
    "panel.configManager": "配置管理", "panel.settings": "设置", "panel.search": "搜索", "panel.noKey": "无按键", "panel.toggle": "切换", "panel.hold": "长按", "panel.visible": "可见", "panel.hidden": "隐藏", "panel.current": "当前", "panel.local": "本地", "panel.cloud": "云端", "panel.save": "保存", "panel.load": "加载", "panel.refresh": "刷新", "panel.language": "界面语言", "panel.palette": "调色板", "panel.glass": "玻璃背景", "panel.caption": "点击分类、模块、开关与设置",
    "features.label": "从公开站点结构重新设计", "features.title": "一套界面，<em>贯穿每个入口。</em>", "features.description": "网站不是另一套陌生产品。它延续客户端的布局、间距、颜色和交互，让第一次访问就像已经打开了 Yozakura。", "features.panelTitle": "原生，而且一致。", "features.panelBody": "两栏 Panel、清晰分类、独立设置入口与紧凑开关，完整复刻客户端的操作节奏。", "features.customTitle": "细节，精确到每个控件。", "features.customBody": "数值、模式、按键、可见性与配色都保持同一语义。", "features.configTitle": "保存、同步、分享。", "features.configBody": "本地 .yzk 继续可用，登录后可保存到个人云空间。", "features.newConfig": "+ 新配置",
    "modules.title": "需要的能力，<br><em>都在这里。</em>", "modules.description": "按客户端真实分类组织模块展示，不用夸张的全屏特效掩盖信息。", "modules.all": "全部", "modules.combat": "战斗", "modules.movement": "移动", "modules.render": "视觉",
    "pricing.label": "透明定价", "pricing.title": "选一个适合你的方案。", "pricing.description": "当前开放的方案均包含完整客户端和后续版本。", "pricing.featured": "当前售卖", "pricing.choose": "选择", "pricing.unavailable": "暂不售卖", "pricing.orderCreated": "订单已创建（待接支付）", "pricing.loginRequired": "请先登录，再创建套餐订单。",
    "closing.title": "准备好打开<br><em>你的 Panel 了吗？</em>",
    "account.logout": "退出登录", "account.overviewLabel": "订阅信息", "account.overviewTitle": "个人订阅概览", "account.active": "有效", "account.inactive": "未激活", "account.plan": "订阅方案", "account.expiry": "到期时间", "account.remaining": "剩余时间", "account.notBound": "未绑定", "account.bound": "设备已绑定", "account.notActivated": "未激活", "account.free": "免费", "account.pendingActivation": "首次登录客户端后开始计时", "account.noHwid": "尚未绑定设备",
    "download.title": "下载 Yozakura", "download.description": "发布包仅向已登录且订阅有效的账号开放。", "download.version": "版本", "download.environment": "运行环境", "download.ready": "下载 Yozakura", "download.preparing": "构建中，暂未开放", "download.waiting": "验证构建正在准备。", "download.subscriptionRequired": "需要有效订阅才能下载客户端。", "download.starting": "正在准备下载…", "download.started": "下载已开始。", "download.unavailable": "客户端暂时无法下载。", "download.offline": "无法连接到下载服务。", "download.loginRequired": "请先登录，再下载客户端。",
    "redeem.title": "激活客户端订阅", "redeem.description": "输入与当前网站账号绑定的一次性订阅码。", "redeem.code": "订阅码", "redeem.activate": "激活订阅", "redeem.required": "请输入订阅码。", "redeem.activating": "正在激活订阅…", "redeem.success": "订阅已激活，现在可使用此账号登录客户端。", "redeem.loginRequired": "请先登录网站账号，再激活客户端订阅。",
    "module.emptyTitle": "没有匹配模块", "module.emptyBody": "尝试更换分类或搜索词", "module.settings": "设置", "module.switch": "切换", "module.enable": "启用模块", "module.mode": "模式", "module.strength": "强度",
    "category.Combat": "战斗类", "category.Player": "玩家类", "category.Movement": "移动类", "category.Render": "视觉类", "category.Modules": "模块",
    "time.day": "天", "time.hour": "小时", "time.minute": "分钟", "error.network": "无法连接到服务器。"
  }
};

const localizedModules = {
  en: {
    AimAssist: "Configurable target assistance with controlled pitch behavior.", AutoClicker: "Configurable click timing while the primary button is held.", Backtrack: "Target evaluation against recent historical positions.", Velocity: "Configurable knockback response strategies.", BridgeAssist: "Input and edge assistance designed for bridging.", Sprint: "Maintains consistent movement and sprint state.", TargetESP: "Highlights the current target with a unified palette.", HUD: "Composable and movable Night Bloom HUD elements.", "Config Manager": "Save, load, and share .yzk profiles."
  },
  zh: {
    AimAssist: "可调节的目标辅助与命中盒内俯仰保持。", AutoClicker: "按住左键时以可配置节奏执行点击。", Backtrack: "在近期历史位置上提供目标判定。", Velocity: "提供可配置的击退响应策略。", BridgeAssist: "面向搭路流程的输入与边缘辅助。", Sprint: "保持连贯的移动与疾跑状态。", TargetESP: "用统一调色板突出当前目标。", HUD: "可组合、可拖动的 Night Bloom HUD。", "Config Manager": "保存、加载与分享 .yzk 配置。"
  }
};

const planCopy = {
  en: {
    month: { name: "30 Days", period: "30 days", perks: ["Full client", "Bind 1 device", "Community support"] },
    quarter: { name: "90 Days", period: "90 days", perks: ["Full client", "1 device reset monthly", "Priority support"] },
    lifetime: { name: "Lifetime", period: "one-time", perks: ["Full client", "2 device resets monthly", "Early features", "Long-term updates"] }
  },
  zh: {
    month: { name: "30 天", period: "30 天", perks: ["完整客户端", "绑定 1 台设备", "社区支持"] },
    quarter: { name: "90 天", period: "90 天", perks: ["完整客户端", "每月 1 次设备重置", "优先支持"] },
    lifetime: { name: "永久", period: "一次购买", perks: ["完整客户端", "每月 2 次设备重置", "早期功能体验", "长期更新"] }
  }
};

const categories = [
  { id: "Combat", icon: "icon-swords" }, { id: "Player", icon: "icon-person" },
  { id: "Movement", icon: "icon-move" }, { id: "Render", icon: "icon-brush" }
];

const detailSettings = {
  AimAssist: [
    { key: "module.mode", type: "mode", value: "BLATANT" },
    { label: { en: "Horizontal speed", zh: "水平速度" }, type: "range", value: 52, min: 1, max: 100 },
    { label: { en: "Vertical speed", zh: "垂直速度" }, type: "range", value: 11, min: 1, max: 100 },
    { label: { en: "Reaction delay", zh: "反应延迟" }, type: "range", value: 38, min: 0, max: 100 },
    { label: { en: "Update rate", zh: "更新频率" }, type: "range", value: 76, min: 1, max: 100 },
    { label: { en: "Randomization", zh: "随机化" }, type: "range", value: 34, min: 0, max: 100 },
    { label: "FOV", type: "range", value: 89, min: 0, max: 100 }
  ]
};

const savedLanguage = localStorage.getItem("yozakuraLanguage");
const state = {
  product: fallback,
  category: "Combat",
  enabled: new Set(["AimAssist", "AutoClicker"]),
  showcase: "All",
  authMode: "login",
  language: savedLanguage === "zh" ? "zh" : "en",
  account: null,
  remainingTimer: null
};

const categoryList = document.querySelector("#categoryList");
const moduleList = document.querySelector("#moduleList");
const panelTitle = document.querySelector("#panelTitle");
const moduleSearch = document.querySelector("#moduleSearch");
const showcaseList = document.querySelector("#showcaseList");
const pricingGrid = document.querySelector("#pricingGrid");
const authDialog = document.querySelector("#authDialog");
const accountDialog = document.querySelector("#accountDialog");

async function init() {
  applyLanguage();
  try {
    const response = await fetch("/api/public", { cache: "no-store" });
    if (response.ok) state.product = await response.json();
  } catch (_) {
  }
  renderDynamicContent();
  bindEvents();
  setupReveal();
  await restoreSession();
}

function t(key) {
  return translations[state.language][key] || translations.en[key] || key;
}

function applyLanguage() {
  document.documentElement.lang = state.language === "zh" ? "zh-CN" : "en";
  document.title = t("meta.title");
  document.querySelector('meta[name="description"]').content = t("meta.description");
  document.querySelectorAll("[data-i18n]").forEach((element) => {
    element.textContent = t(element.dataset.i18n);
  });
  document.querySelectorAll("[data-i18n-html]").forEach((element) => {
    element.innerHTML = t(element.dataset.i18nHtml);
  });
  document.querySelectorAll("[data-i18n-placeholder]").forEach((element) => {
    element.placeholder = t(element.dataset.i18nPlaceholder);
  });
  const toggle = document.querySelector("#languageToggle");
  toggle.querySelectorAll("span")[0].classList.toggle("active", state.language === "en");
  toggle.querySelectorAll("span")[1].classList.toggle("active", state.language === "zh");
  updateAuthCopy();
}

function setLanguage(language) {
  state.language = language === "zh" ? "zh" : "en";
  localStorage.setItem("yozakuraLanguage", state.language);
  applyLanguage();
  renderDynamicContent();
  updateHeaderAccount();
  updateAccountCenter();
}

function renderDynamicContent() {
  renderCategories();
  renderModules();
  renderShowcase();
  renderPricing();
  renderDownload();
}

function renderCategories() {
  categoryList.innerHTML = categories.map((category) => {
    const count = state.product.modules.filter((module) => normalizedCategory(module.category) === category.id).length;
    return `<button class="rail-item ${state.category === category.id ? "active" : ""}" type="button" data-category="${category.id}"><span class="icon ${category.icon}"></span><b>${escapeHtml(t(`category.${category.id}`))}</b><em>${count}</em></button>`;
  }).join("");
}

function moduleDescription(module) {
  return localizedModules[state.language][module.name] || module.description;
}

function renderModules() {
  const search = moduleSearch.value.trim().toLowerCase();
  const modules = state.product.modules.filter((module) => {
    const haystack = `${module.name} ${moduleDescription(module)}`.toLowerCase();
    return normalizedCategory(module.category) === state.category && (!search || haystack.includes(search));
  });
  panelTitle.textContent = t(`category.${state.category}`) || t("category.Modules");
  moduleList.innerHTML = modules.length ? modules.map((module) => `<article class="module-row" data-module="${escapeHtml(module.name)}"><div class="module-copy"><b>${escapeHtml(module.name)}</b><span>${escapeHtml(moduleDescription(module))}</span></div><button class="settings-icon" type="button" data-settings="${escapeHtml(module.name)}" aria-label="${escapeHtml(t("module.settings"))}">⚙</button><button class="switch ${state.enabled.has(module.name) ? "on" : ""}" type="button" data-toggle="${escapeHtml(module.name)}" aria-label="${escapeHtml(t("module.switch"))} ${escapeHtml(module.name)}"></button></article>`).join("") : `<div class="module-row"><div class="module-copy"><b>${escapeHtml(t("module.emptyTitle"))}</b><span>${escapeHtml(t("module.emptyBody"))}</span></div></div>`;
}

function renderShowcase() {
  const modules = state.product.modules.filter((module) => state.showcase === "All" || normalizedCategory(module.category) === state.showcase);
  showcaseList.innerHTML = modules.slice(0, 8).map((module, index) => `<article class="showcase-module"><span class="module-symbol">${String(index + 1).padStart(2, "0")}</span><div><b>${escapeHtml(module.name)}</b><p>${escapeHtml(moduleDescription(module))}</p></div><small>${escapeHtml(t(`category.${normalizedCategory(module.category)}`).toUpperCase())}</small></article>`).join("");
}

function renderPricing() {
  pricingGrid.innerHTML = state.product.plans.map((plan) => {
    const localized = planCopy[state.language][plan.id] || { name: plan.name, period: plan.period, perks: plan.perks };
    const unavailable = plan.available === false;
    const price = unavailable
      ? `<div class="price unavailable"><strong>${escapeHtml(t("pricing.unavailable"))}</strong></div>`
      : `<div class="price"><span>¥</span><strong>${escapeHtml(Number(plan.price).toFixed(2))}</strong><span>/ ${escapeHtml(localized.period)}</span></div>`;
    const buttonText = unavailable ? t("pricing.unavailable") : `${t("pricing.choose")} ${localized.name}`;
    return `<article class="plan ${plan.featured ? "featured" : ""} ${unavailable ? "unavailable" : ""}">${plan.featured ? `<span class="plan-badge">${escapeHtml(t("pricing.featured"))}</span>` : ""}<p class="section-label">${escapeHtml(plan.id.toUpperCase())}</p><h3>${escapeHtml(localized.name)}</h3>${price}<ul>${localized.perks.map((perk) => `<li>${escapeHtml(perk)}</li>`).join("")}</ul><button class="${plan.featured ? "primary-button" : "ghost-button"}" type="button" data-plan="${escapeHtml(plan.id)}" ${unavailable ? "disabled aria-disabled=\"true\"" : ""}>${escapeHtml(buttonText)}</button></article>`;
  }).join("");
}

function renderDownload() {
  const release = state.product.release || {};
  const button = document.querySelector("#clientDownload");
  document.querySelector("#downloadVersion").textContent = `v${release.version || "1.5.0"}`;
  document.querySelector("#downloadMinecraft").textContent = `Minecraft ${release.minecraft || "1.8.9"} · Java 8`;
  if (release.available && release.downloadUrl) {
    const authenticated = Boolean(state.account);
    const subscribed = Boolean(state.account && state.account.subscription && state.account.subscription.active);
    button.textContent = `${t("download.ready")} v${release.version || "1.5.0"}`;
    button.classList.toggle("disabled", authenticated && !subscribed);
    button.disabled = authenticated && !subscribed;
    document.querySelector("#downloadStatus").textContent = authenticated && !subscribed
      ? t("download.subscriptionRequired")
      : "";
    return;
  }
  button.textContent = t("download.preparing");
  button.classList.add("disabled");
  button.disabled = true;
  document.querySelector("#downloadStatus").textContent = t("download.waiting");
}

function bindEvents() {
  categoryList.addEventListener("click", (event) => {
    const button = event.target.closest("[data-category]");
    if (!button) return;
    state.category = button.dataset.category;
    showPage("modulesPage");
    renderCategories();
    renderModules();
  });
  moduleSearch.addEventListener("input", renderModules);
  moduleList.addEventListener("click", (event) => {
    const toggle = event.target.closest("[data-toggle]");
    if (toggle) {
      const name = toggle.dataset.toggle;
      state.enabled.has(name) ? state.enabled.delete(name) : state.enabled.add(name);
      renderModules();
      return;
    }
    const settings = event.target.closest("[data-settings]");
    const row = event.target.closest("[data-module]");
    if (settings || row) openDetail((settings || row).dataset.settings || row.dataset.module);
  });
  document.querySelectorAll("[data-page]").forEach((button) => button.addEventListener("click", () => {
    document.querySelectorAll(".rail-item").forEach((item) => item.classList.remove("active"));
    button.classList.add("active");
    showPage(`${button.dataset.page}Page`);
  }));
  document.querySelector("#closeDetail").addEventListener("click", returnToModules);
  document.querySelectorAll(".page-close").forEach((button) => button.addEventListener("click", returnToModules));
  document.querySelectorAll(".category-pills [data-showcase-category]").forEach((button) => button.addEventListener("click", () => {
    state.showcase = button.dataset.showcaseCategory;
    document.querySelectorAll(".category-pills button").forEach((item) => item.classList.toggle("active", item === button));
    renderShowcase();
  }));
  ["#loginButton", "#heroAccountButton", "#closingDownload", "#closingLogin"].forEach((selector) => {
    document.querySelector(selector).addEventListener("click", openAccountOrAuth);
  });
  document.querySelector("#languageToggle").addEventListener("click", () => setLanguage(state.language === "en" ? "zh" : "en"));
  document.querySelector("#accountClose").addEventListener("click", () => accountDialog.close());
  document.querySelector("#authClose").addEventListener("click", () => authDialog.close());
  document.querySelector("#logoutButton").addEventListener("click", logout);
  document.querySelector("#clientDownload").addEventListener("click", downloadClient);
  document.querySelector("#authSwitch").addEventListener("click", () => openAuth(state.authMode === "login" ? "register" : "login"));
  document.querySelector("#authSubmit").addEventListener("click", submitAuth);
  document.querySelector("#licenseRedeemForm").addEventListener("submit", redeemLicense);
  pricingGrid.addEventListener("click", createOrder);
  document.querySelectorAll(".segmented button").forEach((button) => button.addEventListener("click", () => {
    const parent = button.parentElement;
    if (parent.classList.contains("compact")) parent.querySelectorAll("button").forEach((item) => item.classList.remove("active"));
    button.classList.toggle("active", parent.classList.contains("compact") || !button.classList.contains("active"));
  }));
  document.querySelectorAll(".settings-card .switch").forEach((button) => button.addEventListener("click", () => button.classList.toggle("on")));
}

function openAccountOrAuth() {
  if (state.account) openAccount();
  else openAuth("login");
}

function openAccount() {
  updateAccountCenter();
  if (!accountDialog.open) accountDialog.showModal();
}

async function createOrder(event) {
  const button = event.target.closest("[data-plan]");
  if (!button || button.disabled) return;
  const token = localStorage.getItem("yozakuraClubToken");
  if (!token) return openAuth("login", t("pricing.loginRequired"));
  button.disabled = true;
  const result = await api("/api/orders", { method: "POST", token, body: { planId: button.dataset.plan } });
  button.textContent = result.error ? localizeServerError(result.error) : t("pricing.orderCreated");
  setTimeout(renderPricing, 2400);
}

async function downloadClient() {
  const release = state.product.release || {};
  if (!release.available) return;
  const token = localStorage.getItem("yozakuraClubToken");
  if (!token) return openAuth("login", t("download.loginRequired"));
  if (!state.account || !state.account.subscription || !state.account.subscription.active) {
    document.querySelector("#downloadStatus").textContent = t("download.subscriptionRequired");
    return;
  }
  const button = document.querySelector("#clientDownload");
  const previous = button.textContent;
  button.textContent = t("download.starting");
  button.disabled = true;
  button.classList.add("disabled");
  try {
    const response = await fetch("/api/client/download", { headers: { authorization: `Bearer ${token}` } });
    if (response.status === 401) {
      clearAccount();
      accountDialog.close();
      openAuth("login", t("auth.sessionExpired"));
      return;
    }
    if (!response.ok) {
      let message = t("download.unavailable");
      try {
        const result = await response.json();
        if (result.error) message = localizeServerError(result.error);
      } catch (_) {
      }
      document.querySelector("#downloadStatus").textContent = message;
      return;
    }
    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = downloadFilename(response.headers.get("content-disposition"));
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
    document.querySelector("#downloadStatus").textContent = t("download.started");
  } catch (_) {
    document.querySelector("#downloadStatus").textContent = t("download.offline");
  } finally {
    button.textContent = previous;
    renderDownload();
  }
}

function downloadFilename(disposition) {
  const match = String(disposition || "").match(/filename="([A-Za-z0-9._-]+)"/);
  return match ? match[1] : "yozakura-client.zip";
}

function openDetail(name) {
  document.querySelector("#detailTitle").textContent = name;
  const settings = detailSettings[name] || [
    { key: "module.enable", type: "toggle", value: state.enabled.has(name) },
    { key: "module.mode", type: "mode", value: "DEFAULT" },
    { key: "module.strength", type: "range", value: 62, min: 0, max: 100 }
  ];
  document.querySelector("#settingList").innerHTML = settings.map((setting) => {
    const label = setting.key ? t(setting.key) : typeof setting.label === "object" ? setting.label[state.language] : setting.label;
    if (setting.type === "range") return `<label class="setting-row"><span>${escapeHtml(label)}</span><span class="range-wrap"><input type="range" min="${setting.min}" max="${setting.max}" value="${setting.value}"><i class="value-box">${setting.value}</i></span></label>`;
    if (setting.type === "toggle") return `<div class="setting-row"><span>${escapeHtml(label)}</span><button class="switch ${setting.value ? "on" : ""}" type="button"></button></div>`;
    return `<div class="setting-row"><span>${escapeHtml(label)}</span><button class="mode-pill" type="button">${escapeHtml(setting.value)} ▶</button></div>`;
  }).join("");
  document.querySelectorAll("#settingList input[type=range]").forEach((input) => input.addEventListener("input", () => input.nextElementSibling.textContent = input.value));
  document.querySelectorAll("#settingList .switch").forEach((button) => button.addEventListener("click", () => button.classList.toggle("on")));
  showPage("detailPage");
}

function showPage(id) {
  document.querySelectorAll(".panel-page").forEach((page) => page.classList.toggle("active", page.id === id));
}

function returnToModules() {
  showPage("modulesPage");
  renderCategories();
}

function setupReveal() {
  const observer = new IntersectionObserver((entries) => entries.forEach((entry) => {
    if (entry.isIntersecting) {
      entry.target.classList.add("visible");
      observer.unobserve(entry.target);
    }
  }), { threshold: .12 });
  document.querySelectorAll(".reveal").forEach((element) => observer.observe(element));
}

function updateAuthCopy() {
  const login = state.authMode === "login";
  document.querySelector("#authTitle").textContent = login ? t("auth.login") : t("auth.register");
  document.querySelector("#authSubmit").textContent = login ? t("auth.login") : t("auth.register");
  document.querySelector("#authSwitch").textContent = login ? t("auth.noAccount") : t("auth.hasAccount");
}

function openAuth(mode, message = "") {
  state.authMode = mode;
  updateAuthCopy();
  document.querySelector("#authPassword").autocomplete = mode === "login" ? "current-password" : "new-password";
  document.querySelector("#formMessage").textContent = message;
  if (!authDialog.open) authDialog.showModal();
}

async function submitAuth() {
  const username = document.querySelector("#authUsername").value;
  const password = document.querySelector("#authPassword").value;
  const message = document.querySelector("#formMessage");
  message.textContent = t("auth.connecting");
  const result = await api(`/api/auth/${state.authMode}`, { method: "POST", body: { username, password } });
  if (result.error) {
    message.textContent = localizeServerError(result.error);
    return;
  }
  localStorage.setItem("yozakuraClubToken", result.token);
  authDialog.close();
  await refreshAccount();
  openAccount();
}

async function restoreSession() {
  const token = localStorage.getItem("yozakuraClubToken");
  if (!token) {
    clearAccount(false);
    return;
  }
  await refreshAccount();
}

async function refreshAccount() {
  const token = localStorage.getItem("yozakuraClubToken");
  if (!token) return false;
  const result = await api("/api/me", { token });
  if (result.user) {
    state.account = result;
    updateHeaderAccount();
    updateAccountCenter();
    renderDownload();
    return true;
  }
  clearAccount();
  return false;
}

async function logout() {
  const token = localStorage.getItem("yozakuraClubToken");
  if (token) await api("/api/auth/logout", { method: "POST", token });
  clearAccount();
  accountDialog.close();
}

function clearAccount(removeToken = true) {
  if (removeToken) localStorage.removeItem("yozakuraClubToken");
  state.account = null;
  if (state.remainingTimer) clearInterval(state.remainingTimer);
  state.remainingTimer = null;
  updateHeaderAccount();
  updateAccountCenter();
  renderDownload();
}

async function redeemLicense(event) {
  event.preventDefault();
  const token = localStorage.getItem("yozakuraClubToken");
  const message = document.querySelector("#licenseRedeemMessage");
  if (!token) {
    accountDialog.close();
    openAuth("login", t("redeem.loginRequired"));
    return;
  }
  const licenseKey = document.querySelector("#licenseKey").value.trim();
  const button = document.querySelector("#licenseRedeemButton");
  if (!licenseKey) {
    message.textContent = t("redeem.required");
    return;
  }
  button.disabled = true;
  message.textContent = t("redeem.activating");
  const result = await api("/api/subscription/redeem", { method: "POST", token, body: { licenseKey } });
  if (result.error) {
    message.textContent = localizeServerError(result.error);
    button.disabled = false;
    return;
  }
  document.querySelector("#licenseKey").value = "";
  message.textContent = t("redeem.success");
  await refreshAccount();
  button.disabled = false;
}

function updateHeaderAccount() {
  const button = document.querySelector("#loginButton");
  button.textContent = state.account ? state.account.user.username : t("auth.login");
  button.classList.toggle("signed-in", Boolean(state.account));
  document.querySelector("#closingLogin").textContent = state.account ? state.account.user.username : t("auth.login");
}

function updateAccountCenter() {
  const account = state.account;
  if (!account) {
    document.querySelector("#accountUsername").textContent = "Yozakura Club";
    updateSubscriptionRemaining(null);
    return;
  }
  const subscription = account.subscription || {};
  const hardware = account.hardware || {};
  const active = Boolean(subscription.active);
  document.querySelector("#accountUsername").textContent = account.user.username;
  document.querySelector("#accountPlanBadge").textContent = String(subscription.plan || "free").toUpperCase();
  document.querySelector("#subscriptionPlan").textContent = active ? subscription.plan : t("account.notActivated");
  document.querySelector("#subscriptionExpiry").textContent = active ? formatSubscriptionExpiry(subscription.expiresAt) : "—";
  document.querySelector("#accountHwid").textContent = hardware.maskedId || "—";
  document.querySelector("#accountHwidState").textContent = hardware.bound ? t("account.bound") : t("account.noHwid");
  const stateElement = document.querySelector("#subscriptionState");
  stateElement.classList.toggle("active", active);
  stateElement.querySelector("b").textContent = active ? t("account.active") : t("account.inactive");
  if (state.remainingTimer) clearInterval(state.remainingTimer);
  const refreshRemaining = () => updateSubscriptionRemaining(subscription.expiresAt, active);
  refreshRemaining();
  state.remainingTimer = setInterval(refreshRemaining, 30_000);
}

function updateSubscriptionRemaining(expiresAt, active = false) {
  const element = document.querySelector("#subscriptionRemaining");
  if (!element) return;
  if (!active) {
    element.textContent = "—";
    return;
  }
  if (!expiresAt) {
    element.textContent = t("account.pendingActivation");
    return;
  }
  const remaining = Math.max(0, new Date(expiresAt).getTime() - Date.now());
  const days = Math.floor(remaining / 86400000);
  const hours = Math.floor((remaining % 86400000) / 3600000);
  const minutes = Math.floor((remaining % 3600000) / 60000);
  element.textContent = `${days}${t("time.day")} ${hours}${t("time.hour")} ${minutes}${t("time.minute")}`;
}

function formatSubscriptionExpiry(value) {
  if (!value) return t("account.pendingActivation");
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString(state.language === "zh" ? "zh-CN" : "en-US", { hour12: false, dateStyle: "medium", timeStyle: "short" });
}

function localizeServerError(message) {
  const value = String(message || "");
  if (state.language === "zh") return value;
  const mappings = [
    [/请先登录/, "Please log in first."], [/用户名或密码错误/, "Incorrect username or password."], [/该用户名已存在/, "That username is already in use."], [/密码长度/, "Password must contain 6 to 128 characters."], [/需要有效订阅/, "An active subscription is required to download the client."], [/订阅验证服务.*不可用/, "The subscription verification service is unavailable."], [/卡密格式不正确/, "The subscription code format is invalid."], [/卡密不存在|已被禁用/, "The subscription code does not exist or has been disabled."], [/已被兑换/, "This subscription code has already been redeemed."], [/兑换服务.*未配置/, "The subscription service is not configured."], [/兑换服务不可用/, "The subscription service is unavailable."], [/客户端发布包尚未配置/, "The client release has not been configured."], [/请求过于频繁/, "Too many requests. Try again later."]
  ];
  const match = mappings.find(([pattern]) => pattern.test(value));
  return match ? match[1] : value;
}

async function api(path, options = {}) {
  try {
    const headers = options.body ? { "content-type": "application/json" } : {};
    if (options.token) headers.authorization = `Bearer ${options.token}`;
    const response = await fetch(path, {
      method: options.method || "GET",
      headers,
      body: options.body ? JSON.stringify(options.body) : undefined
    });
    return await response.json();
  } catch (_) {
    return { error: t("error.network") };
  }
}

function normalizedCategory(category) {
  return category === "Config" ? "Render" : category || "Render";
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[character]);
}

init();
