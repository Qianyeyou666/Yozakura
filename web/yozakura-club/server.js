const crypto = require("node:crypto");
const fs = require("node:fs");
const http = require("node:http");
const path = require("node:path");
const { URL } = require("node:url");

const PUBLIC_DIR = path.join(__dirname, "public");
const MAX_BODY_BYTES = 1024 * 1024;
const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const CLIENT_SESSION_TTL_MS = 10 * 60 * 1000;

const PRODUCT = Object.freeze({
  brand: {
    name: "Yozakura",
    version: "1.5.0",
    kicker: "RETAINED-MODE MINECRAFT CLIENT",
    headline: "轻量，但不妥协。",
    description: "为 Minecraft Forge 1.8.9 打造的轻量客户端。原生 Panel 交互、细粒度模块配置与可分享的 .yzk 配置，在一个克制的界面里完成。",
    compatibility: ["Forge 1.8.9", "Vanilla 1.8.9", "Lunar 1.8.9"]
  },
  modules: [
    { name: "AimAssist", category: "Combat", description: "可调节的目标辅助与命中盒内俯仰保持。", icon: "aim" },
    { name: "AutoClicker", category: "Combat", description: "按住左键时以可配置节奏执行点击。", icon: "click" },
    { name: "Backtrack", category: "Combat", description: "在近期历史位置上提供目标判定。", icon: "history" },
    { name: "Velocity", category: "Combat", description: "提供可配置的击退响应策略。", icon: "velocity" },
    { name: "BridgeAssist", category: "Movement", description: "面向搭路流程的输入与边缘辅助。", icon: "bridge" },
    { name: "Sprint", category: "Movement", description: "保持连贯的移动与疾跑状态。", icon: "move" },
    { name: "TargetESP", category: "Render", description: "用统一调色板突出当前目标。", icon: "target" },
    { name: "HUD", category: "Render", description: "可组合、可拖动的 Night Bloom HUD。", icon: "hud" },
    { name: "Config Manager", category: "Config", description: "保存、加载与分享 .yzk 配置。", icon: "config" }
  ],
  features: [
    { eyebrow: "PANEL", title: "原生，而且一致。", description: "客户端内直接使用 ClickGUI Panel 展示模块、账号、版本与云配置，不嵌入外部浏览器。" },
    { eyebrow: "CUSTOMIZATION", title: "细节，精确到每个控件。", description: "数值、模式、按键、可见性和配色保持同一套交互语义。" },
    { eyebrow: "CONFIGS", title: "保存、同步、分享。", description: "本地 .yzk 配置继续可用，登录后可将配置保存到个人云空间。" }
  ],
  plans: [
    { id: "quarter", name: "90 天", price: null, period: "90 天", available: false, featured: false, perks: ["完整客户端", "每月 1 次设备重置", "优先支持"] },
    { id: "month", name: "30 天", price: 15.55, period: "30 天", available: true, featured: true, perks: ["完整客户端", "绑定 1 台设备", "社区支持"] },
    { id: "lifetime", name: "永久", price: null, period: "一次购买", available: false, featured: false, perks: ["完整客户端", "每月 2 次设备重置", "早期功能体验", "长期更新"] }
  ],
  announcements: [
    { id: "panel-club", title: "Yozakura Club 面板上线", summary: "账号、版本、公告与云配置入口已经统一到客户端 Panel。", date: "2026-07-28" },
    { id: "v150", title: "v1.5.0", summary: "Panel ClickGUI、配置档案和 Night Bloom 视觉系统完成整合。", date: "2026-07-28" }
  ],
  release: {
    version: "1.5.0",
    channel: "stable",
    minecraft: "1.8.9",
    publishedAt: "2026-07-28",
    available: false,
    downloadUrl: null,
    notes: "Panel ClickGUI、配置档案与 Yozakura Club 集成。"
  }
});

function createApp(options = {}) {
  const dataDir = options.dataDir || process.env.YOZAKURA_DATA_DIR || path.join(__dirname, "data");
  const logger = options.logger || console;
  const store = createStore(dataDir);
  const limiter = createRateLimiter();
  const clientFile = resolvePrivateFile(
    options.clientFile === undefined
      ? process.env.YOZAKURA_CLIENT_FILE || path.join(__dirname, "releases", "yozakura-client.zip")
      : options.clientFile,
    "YOZAKURA_CLIENT_FILE"
  );
  const product = publicProduct(clientFile);
  const verifyClientProof = options.verifyClientProof || createClientProofVerifier(
    options.verifyServiceUrl || process.env.YOZAKURA_VERIFY_INTROSPECTION_URL
  );
  const verifyServiceSecret = options.verifyServiceSecret || process.env.YOZAKURA_VERIFY_SERVICE_SECRET;
  const redeemSubscription = options.redeemSubscription || createSubscriptionRedeemer(
    options.verifyRedeemUrl || process.env.YOZAKURA_VERIFY_REDEEM_URL,
    verifyServiceSecret
  );
  const fetchAccountProfile = options.fetchAccountProfile || createAccountProfileFetcher(
    options.verifyProfileUrl || process.env.YOZAKURA_VERIFY_PROFILE_URL,
    verifyServiceSecret
  );

  return http.createServer(async (request, response) => {
    try {
      applySecurityHeaders(response);
      const origin = request.socket.remoteAddress || "unknown";
      if (!limiter.allow(origin)) {
        return sendJson(response, 429, { error: "请求过于频繁，请稍后再试。" });
      }
      const target = new URL(request.url, "http://127.0.0.1");
      if (target.pathname.startsWith("/api/")) {
        return await routeApi(request, response, target, store,
          verifyClientProof, redeemSubscription, fetchAccountProfile,
          product, clientFile);
      }
      return serveStatic(request, response, target.pathname);
    } catch (error) {
      logger.error(error);
      if (!response.headersSent) {
        sendJson(response, error.statusCode || 500, { error: error.publicMessage || "服务器内部错误。" });
      } else {
        response.end();
      }
    }
  });
}

async function routeApi(request, response, target, store, verifyClientProof,
  redeemSubscription, fetchAccountProfile, product, clientFile) {
  const method = request.method || "GET";
  const route = target.pathname;

  if (method === "GET" && route === "/api/public") {
    return sendJson(response, 200, product);
  }
  if (method === "GET" && route === "/api/client/summary") {
    const user = optionalUser(request, store);
    return sendJson(response, 200, clientSummary(user, store, product));
  }
  if (method === "GET" && route === "/api/client/download") {
    const user = requireUser(request, store);
    if (!clientFile) return sendJson(response, 503, { error: "客户端发布包尚未配置。" });
    if (user.authSource !== "password" || !user.password) {
      return sendJson(response, 403, { error: "需要有效订阅才能下载客户端。" });
    }
    const remoteProfile = await fetchAccountProfile(user.username, user.password);
    if (!remoteProfile) {
      return sendJson(response, 503, { error: "订阅验证服务暂时不可用。" });
    }
    if (!subscriptionFor(user, remoteProfile).active || remoteProfile.disabled === true) {
      return sendJson(response, 403, { error: "需要有效订阅才能下载客户端。" });
    }
    return sendPrivateFile(response, clientFile);
  }
  if (method === "POST" && route === "/api/auth/client-exchange") {
    const proof = proofOfPossessionToken(request);
    if (!proof) return sendJson(response, 401, { error: "客户端验证证明缺失。" });
    const identity = await verifyClientProof(proof);
    const username = normalizeUsername(identity && identity.username);
    let user = store.findVerifiedUserByName(username);
    if (!user) user = store.createVerifiedUser(username);
    const token = store.createSession(user.id, CLIENT_SESSION_TTL_MS);
    return sendJson(response, 200, { token, user: publicUser(user) });
  }
  if (method === "POST" && route === "/api/auth/register") {
    const body = await readJson(request);
    const username = normalizeUsername(body.username);
    const password = validatePassword(body.password);
    if (store.findUserByName(username)) {
      return sendJson(response, 409, { error: "该用户名已存在。" });
    }
    const user = store.createUser(username, hashPassword(password));
    const token = store.createSession(user.id, SESSION_TTL_MS);
    return sendJson(response, 201, { token, user: publicUser(user) });
  }
  if (method === "POST" && route === "/api/auth/login") {
    const body = await readJson(request);
    const username = normalizeUsername(body.username);
    const user = store.findUserByName(username);
    if (!user || !verifyPassword(String(body.password || ""), user.password)) {
      return sendJson(response, 401, { error: "用户名或密码错误。" });
    }
    const token = store.createSession(user.id, SESSION_TTL_MS);
    return sendJson(response, 200, { token, user: publicUser(user) });
  }
  if (method === "POST" && route === "/api/auth/logout") {
    const token = bearerToken(request);
    if (token) store.deleteSession(token);
    return sendJson(response, 200, { ok: true });
  }
  if (method === "GET" && route === "/api/me") {
    const user = requireUser(request, store);
    const remoteProfile = user.authSource === "password" && user.password
      ? await fetchAccountProfile(user.username, user.password)
      : null;
    return sendJson(response, 200, accountProfile(user, store, remoteProfile));
  }
  if (method === "POST" && route === "/api/subscription/redeem") {
    const user = requireUser(request, store);
    if (user.authSource !== "password" || !user.password) {
      return sendJson(response, 409, { error: "该账号不能通过网站兑换卡密。" });
    }
    const body = await readJson(request);
    const licenseKey = validateLicenseKey(body.licenseKey);
    const entitlement = await redeemSubscription({
      username: user.username,
      passwordVerifier: user.password,
      licenseKey
    });
    store.applySubscription(user.id, entitlement);
    return sendJson(response, 200, {
      ok: true,
      subscription: subscriptionFor(user),
      role: entitlement.role,
      durationDays: entitlement.durationDays
    });
  }
  if (route === "/api/config-hall" && method === "GET") {
    return sendJson(response, 200, {
      configs: store.publicConfigs().map((config) => hallConfigSummary(config, store))
    });
  }
  if (route.startsWith("/api/config-hall/") && method === "GET") {
    const config = store.getPublicConfig(decodeURIComponent(route.slice("/api/config-hall/".length)));
    if (!config) return sendJson(response, 404, { error: "大厅配置不存在。" });
    return sendJson(response, 200, { config: hallConfig(config, store) });
  }
  if (route === "/api/configs" && method === "GET") {
    const user = requireUser(request, store);
    return sendJson(response, 200, { configs: store.configsFor(user.id).map(configSummary) });
  }
  if (route === "/api/configs" && method === "POST") {
    const user = requireUser(request, store);
    const body = await readJson(request);
    const config = store.saveConfig(user.id, validateConfigName(body.name), body.visibility === "public" ? "public" : "private", body.payload);
    return sendJson(response, 201, { config: configSummary(config) });
  }
  if (route.startsWith("/api/configs/") && method === "GET") {
    const user = requireUser(request, store);
    const config = store.getConfig(user.id, decodeURIComponent(route.slice("/api/configs/".length)));
    if (!config) return sendJson(response, 404, { error: "配置不存在。" });
    return sendJson(response, 200, { config });
  }
  if (route.startsWith("/api/configs/") && method === "DELETE") {
    const user = requireUser(request, store);
    const deleted = store.deleteConfig(user.id, decodeURIComponent(route.slice("/api/configs/".length)));
    return sendJson(response, deleted ? 200 : 404, deleted ? { ok: true } : { error: "配置不存在。" });
  }
  if (route === "/api/orders" && method === "POST") {
    const user = requireUser(request, store);
    const body = await readJson(request);
    const plan = product.plans.find((item) => item.id === body.planId);
    if (!plan) return sendJson(response, 400, { error: "套餐不存在。" });
    if (plan.available === false) return sendJson(response, 409, { error: "该套餐暂不售卖。" });
    const order = store.createOrder(user.id, plan);
    return sendJson(response, 201, { order, gateway: { configured: false, message: "订单已创建，尚未接入真实支付网关。" } });
  }
  if (route === "/api/health" && method === "GET") {
    return sendJson(response, 200, { ok: true, version: product.release.version });
  }
  return sendJson(response, 404, { error: "接口不存在。" });
}

function publicProduct(clientFile) {
  return Object.assign({}, PRODUCT, {
    release: Object.assign({}, PRODUCT.release, {
      available: Boolean(clientFile),
      downloadUrl: clientFile ? "/api/client/download" : null
    })
  });
}

function resolvePrivateFile(value, settingName) {
  const candidate = String(value || "").trim();
  if (!candidate) return null;
  const resolved = path.resolve(candidate);
  if (!fs.existsSync(resolved) || !fs.statSync(resolved).isFile()) return null;
  if (resolved === PUBLIC_DIR || resolved.startsWith(`${PUBLIC_DIR}${path.sep}`)) {
    throw new Error(`${settingName} must stay outside the public directory`);
  }
  return resolved;
}

function sendPrivateFile(response, file) {
  const filename = path.basename(file).replace(/[^A-Za-z0-9._-]/g, "_");
  const stat = fs.statSync(file);
  response.writeHead(200, {
    "content-type": "application/octet-stream",
    "content-disposition": `attachment; filename="${filename}"`,
    "content-length": stat.size,
    "cache-control": "private, no-store",
    pragma: "no-cache",
    expires: "0",
    "x-content-type-options": "nosniff"
  });
  fs.createReadStream(file).pipe(response);
}

function clientSummary(user, store, product) {
  return {
    service: "online",
    brand: product.brand.name,
    release: product.release,
    announcements: product.announcements.slice(0, 3),
    account: user ? { authenticated: true, username: user.username, subscription: subscriptionFor(user) } : { authenticated: false, username: "Guest", subscription: null },
    cloudConfigs: user ? store.configsFor(user.id).map(configSummary) : []
  };
}

function createStore(dataDir) {
  fs.mkdirSync(dataDir, { recursive: true });
  const file = path.join(dataDir, "store.json");
  let state = { users: [], sessions: [], configs: [], orders: [] };
  if (fs.existsSync(file)) {
    try {
      state = Object.assign(state, JSON.parse(fs.readFileSync(file, "utf8")));
    } catch (error) {
      throw new Error(`Unable to read ${file}: ${error.message}`);
    }
  }
  const persist = () => {
    const temp = `${file}.${process.pid}.${Date.now()}.tmp`;
    fs.writeFileSync(temp, JSON.stringify(state, null, 2), "utf8");
    fs.renameSync(temp, file);
  };
  const cleanSessions = () => {
    const now = Date.now();
    const next = state.sessions.filter((item) => item.expiresAt > now);
    if (next.length !== state.sessions.length) {
      state.sessions = next;
      persist();
    }
  };
  return {
    findUserByName(username) {
      return state.users.find((item) => item.username.toLowerCase() === username.toLowerCase()) || null;
    },
    findVerifiedUserByName(username) {
      return state.users.find((item) => item.authSource === "client-verification"
        && item.username.toLowerCase() === username.toLowerCase()) || null;
    },
    createUser(username, password) {
      const user = { id: crypto.randomUUID(), username, password, authSource: "password", createdAt: new Date().toISOString(), plan: "free", expiresAt: null };
      state.users.push(user);
      persist();
      return user;
    },
    createVerifiedUser(username) {
      const user = { id: crypto.randomUUID(), username, password: null, authSource: "client-verification", createdAt: new Date().toISOString(), plan: "free", expiresAt: null };
      state.users.push(user);
      persist();
      return user;
    },
    createSession(userId, ttl) {
      cleanSessions();
      const token = crypto.randomBytes(32).toString("base64url");
      state.sessions.push({ tokenHash: sha256(token), userId, expiresAt: Date.now() + ttl });
      persist();
      return token;
    },
    userForToken(token) {
      cleanSessions();
      const session = state.sessions.find((item) => item.tokenHash === sha256(token));
      return session ? state.users.find((item) => item.id === session.userId) || null : null;
    },
    deleteSession(token) {
      const hash = sha256(token);
      state.sessions = state.sessions.filter((item) => item.tokenHash !== hash);
      persist();
    },
    configsFor(userId) {
      return state.configs.filter((item) => item.userId === userId).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
    },
    publicConfigs() {
      return state.configs.filter((item) => item.visibility === "public")
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
    },
    getPublicConfig(id) {
      return state.configs.find((item) => item.visibility === "public" && item.id === id) || null;
    },
    usernameForUserId(userId) {
      const user = state.users.find((item) => item.id === userId);
      return user ? user.username : "Unknown";
    },
    saveConfig(userId, name, visibility, payload) {
      if (!payload || typeof payload !== "object" || Array.isArray(payload)) throw publicError(400, "配置内容必须是 JSON 对象。");
      const serialized = JSON.stringify(payload);
      if (Buffer.byteLength(serialized) > 512 * 1024) throw publicError(413, "单个配置不能超过 512 KiB。");
      let config = state.configs.find((item) => item.userId === userId && item.name.toLowerCase() === name.toLowerCase());
      const now = new Date().toISOString();
      if (config) {
        config.visibility = visibility;
        config.payload = payload;
        config.updatedAt = now;
      } else {
        config = { id: crypto.randomUUID(), userId, name, visibility, payload, createdAt: now, updatedAt: now };
        state.configs.push(config);
      }
      persist();
      return config;
    },
    getConfig(userId, idOrName) {
      return state.configs.find((item) => item.userId === userId && (item.id === idOrName || item.name.toLowerCase() === idOrName.toLowerCase())) || null;
    },
    deleteConfig(userId, idOrName) {
      const before = state.configs.length;
      state.configs = state.configs.filter((item) => !(item.userId === userId && (item.id === idOrName || item.name.toLowerCase() === idOrName.toLowerCase())));
      if (state.configs.length !== before) persist();
      return state.configs.length !== before;
    },
    createOrder(userId, plan) {
      const order = { id: crypto.randomUUID(), userId, planId: plan.id, planName: plan.name, amount: plan.price, currency: "CNY", status: "pending_gateway", createdAt: new Date().toISOString() };
      state.orders.push(order);
      persist();
      return order;
    },
    ordersFor(userId) {
      return state.orders.filter((item) => item.userId === userId).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    },
    applySubscription(userId, entitlement) {
      const user = state.users.find((item) => item.id === userId);
      if (!user) throw publicError(404, "账号不存在。");
      user.plan = String(entitlement.role || "premium");
      user.subscriptionDays = Number(entitlement.durationDays || 0);
      user.subscriptionActivatedAt = entitlement.activatedAt || null;
      user.expiresAt = entitlement.expiresAt || null;
      persist();
      return user;
    }
  };
}

function createRateLimiter() {
  const buckets = new Map();
  return {
    allow(key) {
      const now = Date.now();
      const bucket = buckets.get(key) || { start: now, count: 0 };
      if (now - bucket.start > 60_000) {
        bucket.start = now;
        bucket.count = 0;
      }
      bucket.count++;
      buckets.set(key, bucket);
      if (buckets.size > 2048) {
        for (const [entry, value] of buckets) if (now - value.start > 120_000) buckets.delete(entry);
      }
      return bucket.count <= 180;
    }
  };
}

function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.pbkdf2Sync(password, salt, 160_000, 32, "sha256");
  return `pbkdf2-sha256$160000$${salt.toString("base64")}$${hash.toString("base64")}`;
}

function verifyPassword(password, encoded) {
  try {
    const [algorithm, rounds, salt, expected] = String(encoded).split("$");
    if (algorithm !== "pbkdf2-sha256") return false;
    const actual = crypto.pbkdf2Sync(password, Buffer.from(salt, "base64"), Number(rounds), 32, "sha256");
    return crypto.timingSafeEqual(actual, Buffer.from(expected, "base64"));
  } catch (_) {
    return false;
  }
}

function createAccountProfileFetcher(profileUrl, serviceSecret) {
  const endpoint = String(profileUrl || "").trim();
  const secret = String(serviceSecret || "");
  return async (username, passwordVerifier) => {
    if (!endpoint || secret.length < 43 || !passwordVerifier) return null;
    const timestamp = Math.floor(Date.now() / 1000);
    const canonical = `YOZAKURA-SERVICE-1\nACCOUNT-PROFILE\n${timestamp}\n${username}\n${passwordVerifier}`;
    const signature = crypto.createHmac("sha256", secret).update(canonical).digest("base64url");
    const body = new URLSearchParams({
      username,
      password_verifier: passwordVerifier,
      timestamp: String(timestamp),
      signature
    });
    try {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: {
          "content-type": "application/x-www-form-urlencoded;charset=UTF-8",
          accept: "application/json"
        },
        body,
        signal: AbortSignal.timeout(5000)
      });
      if (response.status === 404) return null;
      const result = await response.json();
      return response.ok && result && result.ok === true && result.user ? result.user : null;
    } catch (_) {
      return null;
    }
  };
}

function createSubscriptionRedeemer(redeemUrl, serviceSecret) {
  const endpoint = String(redeemUrl || "").trim();
  const secret = String(serviceSecret || "");
  return async ({ username, passwordVerifier, licenseKey }) => {
    if (!endpoint || secret.length < 43) throw publicError(503, "订阅兑换服务尚未配置。");
    const timestamp = Math.floor(Date.now() / 1000);
    const canonical = `YOZAKURA-SERVICE-1\nLICENSE-REDEEM\n${timestamp}\n${username}\n${passwordVerifier}\n${licenseKey}`;
    const signature = crypto.createHmac("sha256", secret).update(canonical).digest("base64url");
    const body = new URLSearchParams({
      username,
      password_verifier: passwordVerifier,
      license_key: licenseKey,
      timestamp: String(timestamp),
      signature
    });
    let response;
    try {
      response = await fetch(endpoint, {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded;charset=UTF-8", accept: "application/json" },
        body,
        signal: AbortSignal.timeout(8000)
      });
    } catch (_) {
      throw publicError(502, "订阅兑换服务不可用。");
    }
    let result;
    try {
      result = await response.json();
    } catch (_) {
      throw publicError(502, "订阅兑换服务返回无效响应。");
    }
    if (!response.ok || !result || result.ok !== true || !result.user) {
      const message = result && result.error && result.error.message;
      throw publicError(response.status === 422 ? 422 : 502, licenseErrorMessage(message));
    }
    return {
      role: result.user.role,
      durationDays: result.user.durationDays,
      activatedAt: result.user.activatedAt,
      expiresAt: result.user.expires
    };
  };
}

function licenseErrorMessage(message) {
  const value = String(message || "").toLowerCase();
  if (value.includes("not found")) return "卡密不存在或已被禁用。";
  if (value.includes("already redeemed")) return "该卡密已被兑换。";
  if (value.includes("credential")) return "网站账号与验证账号凭据不一致，请联系管理员迁移旧账号。";
  if (value.includes("already linked")) return "该账号已绑定客户端订阅。";
  return "卡密兑换失败。";
}

function createClientProofVerifier(introspectionUrl) {
  const endpoint = String(introspectionUrl || "").trim();
  return async (proof) => {
    if (!endpoint) throw publicError(503, "客户端验证服务尚未配置。");
    let response;
    try {
      response = await fetch(endpoint, {
        method: "POST",
        headers: { authorization: `PoP ${proof}`, accept: "application/json" },
        signal: AbortSignal.timeout(8000)
      });
    } catch (_) {
      throw publicError(502, "客户端验证服务不可用。");
    }
    let body;
    try {
      body = await response.json();
    } catch (_) {
      throw publicError(502, "客户端验证服务返回无效响应。");
    }
    if (!response.ok || !body || body.active !== true || !body.username) {
      throw publicError(401, "客户端验证已失效。");
    }
    return { username: body.username };
  };
}

function normalizeUsername(value) {
  const username = String(value || "").trim();
  if (!/^[A-Za-z0-9_\-\u4e00-\u9fff]{3,24}$/.test(username)) throw publicError(400, "用户名需为 3-24 位中文、字母、数字、下划线或连字符。");
  return username;
}

function validatePassword(value) {
  const password = String(value || "");
  if (password.length < 6 || password.length > 128) throw publicError(400, "密码长度需为 6-128 位。");
  return password;
}

function validateConfigName(value) {
  const name = String(value || "").trim();
  if (!/^[A-Za-z0-9_\-\u4e00-\u9fff]{1,48}$/.test(name)) throw publicError(400, "配置名仅支持 1-48 位中文、字母、数字、下划线或连字符。");
  return name;
}

function validateLicenseKey(value) {
  const licenseKey = String(value || "").trim();
  if (!/^YOZA-[A-Za-z0-9_-]{20,59}$/.test(licenseKey)) {
    throw publicError(400, "卡密格式不正确。");
  }
  return licenseKey;
}

function accountProfile(user, store, remoteProfile) {
  const profile = remoteProfile && typeof remoteProfile === "object" ? remoteProfile : null;
  return {
    user: publicUser(user),
    subscription: subscriptionFor(user, profile),
    hardware: hardwareFor(profile),
    orders: store.ordersFor(user.id)
  };
}

function subscriptionFor(user, remoteProfile) {
  const profile = remoteProfile && typeof remoteProfile === "object" ? remoteProfile : {};
  const plan = profile.role || user.plan || "free";
  const activatedAt = profile.activatedAt || user.subscriptionActivatedAt || null;
  const expiresAt = profile.expiresAt || profile.expires || user.expiresAt || null;
  const activatedAtRequired = Boolean(profile && Object.keys(profile).length);
  const active = profile.disabled !== true
    && Boolean(plan && plan !== "free")
    && (!activatedAtRequired || Boolean(activatedAt))
    && !isExpired(expiresAt);
  return {
    plan,
    active,
    durationDays: Number(profile.durationDays || user.subscriptionDays || 0),
    activatedAt,
    expiresAt,
    remainingMillis: remainingMillis(expiresAt)
  };
}

function hardwareFor(remoteProfile) {
  const profile = remoteProfile && typeof remoteProfile === "object" ? remoteProfile : {};
  const fingerprint = String(profile.hardwareFingerprint || "");
  const bound = Boolean(profile.hardwareBound || fingerprint);
  return {
    required: Boolean(profile.bindHardware),
    bound,
    maskedId: bound && fingerprint ? maskHardwareFingerprint(fingerprint) : null
  };
}

function maskHardwareFingerprint(value) {
  const fingerprint = String(value || "");
  if (fingerprint.length <= 12) return fingerprint ? "••••••" : null;
  return `${fingerprint.slice(0, 6)}••••••${fingerprint.slice(-6)}`;
}

function remainingMillis(expiresAt) {
  if (!expiresAt) return null;
  const timestamp = Date.parse(expiresAt);
  return Number.isFinite(timestamp) ? Math.max(0, timestamp - Date.now()) : null;
}

function isExpired(expiresAt) {
  const remaining = remainingMillis(expiresAt);
  return remaining !== null && remaining <= 0;
}

function publicUser(user) {
  return { id: user.id, username: user.username, createdAt: user.createdAt };
}

function configSummary(config) {
  return { id: config.id, name: config.name, visibility: config.visibility, createdAt: config.createdAt, updatedAt: config.updatedAt };
}

function hallConfigSummary(config, store) {
  return Object.assign(configSummary(config), { owner: store.usernameForUserId(config.userId) });
}

function hallConfig(config, store) {
  return Object.assign(hallConfigSummary(config, store), { payload: config.payload });
}

function bearerToken(request) {
  const authorization = String(request.headers.authorization || "");
  return authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
}

function proofOfPossessionToken(request) {
  const authorization = String(request.headers.authorization || "");
  return authorization.startsWith("PoP ") ? authorization.slice(4).trim() : "";
}

function optionalUser(request, store) {
  const token = bearerToken(request);
  return token ? store.userForToken(token) : null;
}

function requireUser(request, store) {
  const user = optionalUser(request, store);
  if (!user) throw publicError(401, "请先登录。");
  return user;
}

async function readJson(request) {
  let size = 0;
  const chunks = [];
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw publicError(413, "请求内容过大。");
    chunks.push(chunk);
  }
  if (!chunks.length) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch (_) {
    throw publicError(400, "JSON 格式无效。");
  }
}

function serveStatic(request, response, pathname) {
  if (request.method !== "GET" && request.method !== "HEAD") return sendJson(response, 405, { error: "Method Not Allowed" });
  const requested = pathname === "/" ? "/index.html" : pathname;
  const normalized = path.normalize(decodeURIComponent(requested)).replace(/^(\.\.(\/|\\|$))+/, "");
  const file = path.join(PUBLIC_DIR, normalized);
  if (!file.startsWith(PUBLIC_DIR) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    return sendJson(response, 404, { error: "页面不存在。" });
  }
  const extension = path.extname(file).toLowerCase();
  const mime = { ".html": "text/html; charset=utf-8", ".css": "text/css; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".svg": "image/svg+xml", ".json": "application/json; charset=utf-8" }[extension] || "application/octet-stream";
  response.writeHead(200, { "content-type": mime, "cache-control": extension === ".html" ? "no-cache" : "public, max-age=3600" });
  if (request.method === "HEAD") return response.end();
  fs.createReadStream(file).pipe(response);
}

function applySecurityHeaders(response) {
  response.setHeader("x-content-type-options", "nosniff");
  response.setHeader("x-frame-options", "DENY");
  response.setHeader("referrer-policy", "strict-origin-when-cross-origin");
  response.setHeader("permissions-policy", "camera=(), microphone=(), geolocation=()");
  response.setHeader("content-security-policy", "default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; connect-src 'self'; base-uri 'none'; form-action 'self'");
}

function sendJson(response, status, payload) {
  const body = JSON.stringify(payload);
  response.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "content-length": Buffer.byteLength(body) });
  response.end(body);
}

function publicError(statusCode, publicMessage) {
  const error = new Error(publicMessage);
  error.statusCode = statusCode;
  error.publicMessage = publicMessage;
  return error;
}

function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

if (require.main === module) {
  const port = Number(process.env.PORT || 4173);
  const server = createApp();
  server.listen(port, "127.0.0.1", () => console.log(`Yozakura Club listening on http://127.0.0.1:${port}`));
}

module.exports = { createApp, PRODUCT, hashPassword, verifyPassword };
