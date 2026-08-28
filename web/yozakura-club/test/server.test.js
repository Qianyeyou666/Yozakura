const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const test = require("node:test");

const { createApp } = require("../server");

async function withServer(run, options = {}) {
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "yozakura-club-"));
  const app = createApp({ dataDir, logger: { log() {}, error() {} }, ...options });
  await new Promise((resolve) => app.listen(0, "127.0.0.1", resolve));
  const address = app.address();
  try {
    await run(`http://127.0.0.1:${address.port}`);
  } finally {
    await new Promise((resolve) => app.close(resolve));
    fs.rmSync(dataDir, { recursive: true, force: true });
  }
}

async function json(url, options) {
  const response = await fetch(url, options);
  const body = await response.json();
  return { response, body };
}

async function withClientRelease(run, options = {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "yozakura-download-"));
  const clientFile = path.join(directory, "yozakura-client.zip");
  fs.writeFileSync(clientFile, "protected-client", "utf8");
  try {
    await withServer(run, { ...options, clientFile });
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
  }
}

async function registerAccount(base, username) {
  return json(`${base}/api/auth/register`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ username, password: "a sufficiently long password" })
  });
}

test("public API exposes branded product data without a stale client download", async () => {
  await withServer(async (base) => {
    const { response, body } = await json(`${base}/api/public`);
    assert.equal(response.status, 200);
    assert.equal(body.brand.name, "Yozakura");
    assert.ok(body.modules.length >= 8);
    assert.equal(body.plans.length, 3);
    assert.equal(body.release.available, false);
    assert.equal(body.release.downloadUrl, null);
  });
});

test("public API exposes only the protected download endpoint when a release file exists", async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "yozakura-download-"));
  const clientFile = path.join(directory, "yozakura-client.zip");
  fs.writeFileSync(clientFile, "protected-client", "utf8");
  try {
    await withServer(async (base) => {
      const { response, body } = await json(`${base}/api/public`);
      assert.equal(response.status, 200);
      assert.equal(body.release.available, true);
      assert.equal(body.release.downloadUrl, "/api/client/download");
    }, { clientFile });
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
  }
});

test("client release download requires login and stays outside the public directory", async () => {
  await withClientRelease(async (base) => {
    const anonymous = await json(`${base}/api/client/download`);
    assert.equal(anonymous.response.status, 401);

    const bypass = await fetch(`${base}/downloads/yozakura-client.zip`);
    assert.equal(bypass.status, 404);
  });
});

test("client release download rejects a signed-in account without an active subscription", async () => {
  let profileCalls = 0;
  await withClientRelease(async (base) => {
    const registered = await registerAccount(base, "FreeDownloader");
    const rejected = await json(`${base}/api/client/download`, {
      headers: { authorization: `Bearer ${registered.body.token}` }
    });
    assert.equal(rejected.response.status, 403);
    assert.match(rejected.body.error, /有效订阅/);
  }, {
    fetchAccountProfile: async () => {
      profileCalls++;
      return { role: "free", durationDays: 0, activatedAt: null, expiresAt: null, disabled: false };
    }
  });
  assert.equal(profileCalls, 1);
});

test("client release download rejects pending, expired and disabled subscriptions", async (context) => {
  const cases = [
    { name: "PendingDownloader", profile: { role: "premium", activatedAt: null, expiresAt: null, disabled: false } },
    { name: "ExpiredDownloader", profile: { role: "premium", activatedAt: "2026-06-01T00:00:00Z", expiresAt: "2026-06-30T00:00:00Z", disabled: false } },
    { name: "DisabledDownloader", profile: { role: "premium", activatedAt: "2026-07-01T00:00:00Z", expiresAt: "2099-07-31T00:00:00Z", disabled: true } }
  ];
  for (const scenario of cases) {
    await context.test(scenario.name, async () => {
      await withClientRelease(async (base) => {
        const registered = await registerAccount(base, scenario.name);
        const rejected = await json(`${base}/api/client/download`, {
          headers: { authorization: `Bearer ${registered.body.token}` }
        });
        assert.equal(rejected.response.status, 403);
        assert.match(rejected.body.error, /有效订阅/);
      }, { fetchAccountProfile: async () => scenario.profile });
    });
  }
});

test("client release download fails closed when the authority profile is unavailable", async () => {
  await withClientRelease(async (base) => {
    const registered = await registerAccount(base, "AuthorityUnavailable");
    const rejected = await json(`${base}/api/client/download`, {
      headers: { authorization: `Bearer ${registered.body.token}` }
    });
    assert.equal(rejected.response.status, 503);
    assert.match(rejected.body.error, /订阅验证服务/);
  }, { fetchAccountProfile: async () => null });
});

test("client release download succeeds only with a current authoritative subscription", async () => {
  await withClientRelease(async (base) => {
    const registered = await registerAccount(base, "SubscribedDownloader");
    const response = await fetch(`${base}/api/client/download`, {
      headers: { authorization: `Bearer ${registered.body.token}` }
    });
    assert.equal(response.status, 200);
    assert.match(response.headers.get("content-disposition"), /yozakura-client\.zip/);
    assert.equal(response.headers.get("cache-control"), "private, no-store");
    assert.equal(await response.text(), "protected-client");
  }, {
    fetchAccountProfile: async () => ({
      role: "premium",
      durationDays: 30,
      activatedAt: "2026-07-28T08:00:00Z",
      expiresAt: "2099-08-27T08:00:00Z",
      disabled: false
    })
  });
});

test("pricing keeps the 30-day plan centered at 15.55 and disables the other plans", async () => {
  await withServer(async (base) => {
    const { body } = await json(`${base}/api/public`);
    assert.deepEqual(body.plans.map((plan) => plan.id), ["quarter", "month", "lifetime"]);
    assert.equal(body.plans[1].price, 15.55);
    assert.equal(body.plans[1].available, true);
    assert.equal(body.plans[1].featured, true);
    assert.equal(body.plans[0].available, false);
    assert.equal(body.plans[2].available, false);
  });

  const script = fs.readFileSync(path.join(__dirname, "..", "public", "app.js"), "utf8");
  assert.match(script, /pricing\.unavailable/);
  assert.match(script, /plan\.available === false/);
});

test("login dialog close button bypasses form validation and closes explicitly", () => {
  const html = fs.readFileSync(path.join(__dirname, "..", "public", "index.html"), "utf8");
  const script = fs.readFileSync(path.join(__dirname, "..", "public", "app.js"), "utf8");
  assert.match(html, /id="authClose"[^>]*type="button"/);
  assert.match(script, /#authClose/);
  assert.match(script, /authDialog\.close\(\)/);
});

test("home page defaults to English and moves download and redemption into account center", () => {
  const html = fs.readFileSync(path.join(__dirname, "..", "public", "index.html"), "utf8");
  assert.match(html, /<html lang="en">/);
  assert.match(html, /id="languageToggle"/);
  assert.match(html, /id="accountDialog"/);
  assert.match(html, /id="clientDownload"/);
  assert.match(html, /id="licenseRedeemForm"/);
  assert.doesNotMatch(html, /<section[^>]+id="download"/);
  assert.doesNotMatch(html, /<section[^>]+id="licenseRedeem"/);
});

test("frontend downloads only for an active account through an authenticated API request", () => {
  const script = fs.readFileSync(path.join(__dirname, "..", "public", "app.js"), "utf8");
  assert.match(script, /state\.account\.subscription\.active/);
  assert.match(script, /download\.subscriptionRequired/);
  assert.match(script, /fetch\("\/api\/client\/download"/);
  assert.match(script, /authorization: `Bearer \$\{token\}`/);
  assert.match(script, /URL\.createObjectURL/);
});

test("registration accepts 6-character passwords and rejects shorter ones", async () => {
  await withServer(async (base) => {
    const accepted = await json(`${base}/api/auth/register`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username: "SixPass", password: "123456" })
    });
    assert.equal(accepted.response.status, 201);

    const rejected = await json(`${base}/api/auth/register`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username: "FivePass", password: "12345" })
    });
    assert.equal(rejected.response.status, 400);
    assert.match(rejected.body.error, /6-128/);

    const html = fs.readFileSync(path.join(__dirname, "..", "public", "index.html"), "utf8");
    assert.match(html, /id="authPassword"[^>]*minlength="6"/);
  });
});

test("register, login and protected profile flow", async () => {
  await withServer(async (base) => {
    const registered = await json(`${base}/api/auth/register`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username: "SakuraUser", password: "correct horse battery" })
    });
    assert.equal(registered.response.status, 201);
    assert.ok(registered.body.token);
    assert.equal(registered.body.user.username, "SakuraUser");

    const profile = await json(`${base}/api/me`, {
      headers: { authorization: `Bearer ${registered.body.token}` }
    });
    assert.equal(profile.response.status, 200);
    assert.equal(profile.body.user.username, "SakuraUser");

    const rejected = await json(`${base}/api/auth/login`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username: "SakuraUser", password: "wrong password" })
    });
    assert.equal(rejected.response.status, 401);
  });
});

test("profile exposes subscription timing and only a masked hardware binding", async () => {
  const expiresAt = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString();
  await withServer(async (base) => {
    const registered = await json(`${base}/api/auth/register`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username: "ProfileUser", password: "correct horse battery" })
    });
    const redeemed = await json(`${base}/api/subscription/redeem`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${registered.body.token}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({ licenseKey: "YOZA-abcdefghijklmnopqrstuvwx" })
    });
    assert.equal(redeemed.response.status, 200);

    const profile = await json(`${base}/api/me`, {
      headers: { authorization: `Bearer ${registered.body.token}` }
    });
    assert.equal(profile.body.subscription.durationDays, 30);
    assert.equal(profile.body.subscription.activatedAt, "2026-07-28T08:00:00Z");
    assert.equal(profile.body.subscription.expiresAt, expiresAt);
    assert.ok(profile.body.subscription.remainingMillis > 0);
    assert.equal(profile.body.hardware.bound, true);
    assert.equal(profile.body.hardware.maskedId, "abcd12••••••wxyz89");
    assert.equal(JSON.stringify(profile.body).includes("abcd1234567890wxyz89"), false);
  }, {
    redeemSubscription: async () => ({
      role: "premium",
      durationDays: 30,
      activatedAt: "2026-07-28T08:00:00Z",
      expiresAt
    }),
    fetchAccountProfile: async (username, passwordVerifier) => {
      assert.equal(username, "ProfileUser");
      assert.match(passwordVerifier, /^pbkdf2-sha256\$160000\$/);
      return {
        role: "premium",
        durationDays: 30,
        activatedAt: "2026-07-28T08:00:00Z",
        expiresAt,
        hardwareBound: true,
        hardwareFingerprint: "abcd1234567890wxyz89"
      };
    }
  });
});

test("website account redeems a license without forwarding its plaintext password", async () => {
  const calls = [];
  await withServer(async (base) => {
    const registered = await json(`${base}/api/auth/register`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username: "LicensedUser", password: "correct horse battery" })
    });
    const redeemed = await json(`${base}/api/subscription/redeem`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${registered.body.token}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({ licenseKey: "YOZA-abcdefghijklmnopqrstuvwx" })
    });
    assert.equal(redeemed.response.status, 200);
    assert.equal(redeemed.body.subscription.active, true);
    assert.equal(redeemed.body.subscription.plan, "premium");

    const profile = await json(`${base}/api/me`, {
      headers: { authorization: `Bearer ${registered.body.token}` }
    });
    assert.equal(profile.body.subscription.plan, "premium");
  }, {
    redeemSubscription: async (request) => {
      calls.push(request);
      return { role: "premium", durationDays: 90, activatedAt: null, expiresAt: null };
    }
  });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].username, "LicensedUser");
  assert.match(calls[0].passwordVerifier, /^pbkdf2-sha256\$160000\$/);
  assert.equal(JSON.stringify(calls).includes("correct horse battery"), false);
});

test("subscription redemption requires an authenticated website account", async () => {
  await withServer(async (base) => {
    const rejected = await json(`${base}/api/subscription/redeem`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ licenseKey: "YOZA-abcdefghijklmnopqrstuvwx" })
    });
    assert.equal(rejected.response.status, 401);
  }, { redeemSubscription: async () => ({}) });
});

test("frontend account center owns card redemption and supports bilingual account copy", () => {
  const html = fs.readFileSync(path.join(__dirname, "..", "public", "index.html"), "utf8");
  const script = fs.readFileSync(path.join(__dirname, "..", "public", "app.js"), "utf8");
  assert.match(html, /id="accountDialog"/);
  assert.match(html, /id="licenseKey"/);
  assert.match(html, /id="subscriptionRemaining"/);
  assert.match(html, /id="accountHwid"/);
  assert.match(script, /api\("\/api\/subscription\/redeem"/);
  assert.match(script, /localStorage\.getItem\("yozakuraClubToken"\)/);
  assert.match(script, /const translations =/);
  assert.match(script, /document\.documentElement\.lang/);
});

test("verified client proof exchanges for an isolated Club session", async () => {
  const calls = [];
  await withServer(async (base) => {
    const exchanged = await json(`${base}/api/auth/client-exchange`, {
      method: "POST",
      headers: { authorization: "PoP native-proof" }
    });
    assert.equal(exchanged.response.status, 200);
    assert.ok(exchanged.body.token);
    assert.equal(exchanged.body.user.username, "VerifiedUser");

    const profile = await json(`${base}/api/me`, {
      headers: { authorization: `Bearer ${exchanged.body.token}` }
    });
    assert.equal(profile.response.status, 200);
    assert.equal(profile.body.user.username, "VerifiedUser");
  }, {
    verifyClientProof: async (proof) => {
      calls.push(proof);
      return { username: "VerifiedUser" };
    }
  });
  assert.deepEqual(calls, ["native-proof"]);
});

test("verified client identity stays isolated from a same-name website account", async () => {
  await withServer(async (base) => {
    const registered = await json(`${base}/api/auth/register`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username: "SharedName", password: "a sufficiently long password" })
    });
    const exchanged = await json(`${base}/api/auth/client-exchange`, {
      method: "POST",
      headers: { authorization: "PoP native-proof" }
    });
    assert.equal(registered.response.status, 201);
    assert.equal(exchanged.response.status, 200);
    assert.notEqual(exchanged.body.user.id, registered.body.user.id);
  }, {
    verifyClientProof: async () => ({ username: "SharedName" })
  });
});

test("client exchange rejects missing proof and never trusts a posted username", async () => {
  await withServer(async (base) => {
    const rejected = await json(`${base}/api/auth/client-exchange`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username: "ForgedUser" })
    });
    assert.equal(rejected.response.status, 401);
  }, {
    verifyClientProof: async () => {
      throw Object.assign(new Error("proof required"), { statusCode: 401, publicMessage: "客户端验证已失效。" });
    }
  });
});

test("public config hall can be listed and downloaded without authentication", async () => {
  await withServer(async (base) => {
    const registered = await json(`${base}/api/auth/register`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username: "HallOwner", password: "a sufficiently long password" })
    });
    const headers = {
      authorization: `Bearer ${registered.body.token}`,
      "content-type": "application/json"
    };
    const created = await json(`${base}/api/configs`, {
      method: "POST",
      headers,
      body: JSON.stringify({ name: "public-legit", visibility: "public", payload: { Reach: { state: true } } })
    });
    await json(`${base}/api/configs`, {
      method: "POST",
      headers,
      body: JSON.stringify({ name: "private-legit", visibility: "private", payload: { Reach: { state: false } } })
    });

    const hall = await json(`${base}/api/config-hall`);
    assert.equal(hall.response.status, 200);
    assert.equal(hall.body.configs.length, 1);
    assert.equal(hall.body.configs[0].name, "public-legit");
    assert.equal(hall.body.configs[0].owner, "HallOwner");

    const downloaded = await json(`${base}/api/config-hall/${created.body.config.id}`);
    assert.equal(downloaded.response.status, 200);
    assert.equal(downloaded.body.config.payload.Reach.state, true);
    assert.equal(downloaded.body.config.owner, "HallOwner");
    assert.equal(Object.prototype.hasOwnProperty.call(downloaded.body.config, "userId"), false);
  });
});

test("only the owner can delete an uploaded hall config", async () => {
  await withServer(async (base) => {
    const owner = await json(`${base}/api/auth/register`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username: "DeleteOwner", password: "123456" })
    });
    const other = await json(`${base}/api/auth/register`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username: "DeleteOther", password: "123456" })
    });
    const created = await json(`${base}/api/configs`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${owner.body.token}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({ name: "owner-only", visibility: "public", payload: { Reach: { state: true } } })
    });
    const configUrl = `${base}/api/configs/${created.body.config.id}`;

    const anonymous = await json(configUrl, { method: "DELETE" });
    assert.equal(anonymous.response.status, 401);

    const rejected = await json(configUrl, {
      method: "DELETE",
      headers: { authorization: `Bearer ${other.body.token}` }
    });
    assert.equal(rejected.response.status, 404);

    const before = await json(`${base}/api/config-hall`);
    assert.equal(before.body.configs.length, 1);

    const deleted = await json(configUrl, {
      method: "DELETE",
      headers: { authorization: `Bearer ${owner.body.token}` }
    });
    assert.equal(deleted.response.status, 200);
    assert.equal(deleted.body.ok, true);

    const after = await json(`${base}/api/config-hall`);
    assert.equal(after.body.configs.length, 0);
  });
});

test("authenticated users can save and list cloud configs", async () => {
  await withServer(async (base) => {
    const registered = await json(`${base}/api/auth/register`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username: "ConfigOwner", password: "a sufficiently long password" })
    });
    const headers = {
      authorization: `Bearer ${registered.body.token}`,
      "content-type": "application/json"
    };
    const created = await json(`${base}/api/configs`, {
      method: "POST",
      headers,
      body: JSON.stringify({ name: "hypixel", visibility: "private", payload: { modules: { AimAssist: true } } })
    });
    assert.equal(created.response.status, 201);
    assert.equal(created.body.config.name, "hypixel");

    const list = await json(`${base}/api/configs`, { headers });
    assert.equal(list.response.status, 200);
    assert.equal(list.body.configs.length, 1);
    assert.equal(list.body.configs[0].name, "hypixel");
  });
});

test("unavailable plans cannot create orders", async () => {
  await withServer(async (base) => {
    const registered = await json(`${base}/api/auth/register`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username: "UnavailableBuyer", password: "another long password" })
    });
    const order = await json(`${base}/api/orders`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${registered.body.token}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({ planId: "lifetime" })
    });
    assert.equal(order.response.status, 409);
    assert.match(order.body.error, /暂不售卖/);
  });
});

test("order endpoint creates a pending gateway order without claiming payment", async () => {
  await withServer(async (base) => {
    const registered = await json(`${base}/api/auth/register`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ username: "Buyer", password: "another long password" })
    });
    const order = await json(`${base}/api/orders`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${registered.body.token}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({ planId: "month" })
    });
    assert.equal(order.response.status, 201);
    assert.equal(order.body.order.status, "pending_gateway");
    assert.equal(order.body.order.currency, "CNY");
  });
});
