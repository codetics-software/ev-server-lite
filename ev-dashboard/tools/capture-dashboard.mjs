import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import assert from "node:assert/strict";
import { chromium } from "playwright";

async function main() {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
  const ready = await fetch("http://127.0.0.1:3000/api/backend/api/v1/auth/providers");
  assert.equal(ready.ok, true, "The local H2 test harness must be ready before capturing");
  const credentials = JSON.parse(
    await fs.readFile(path.join(root, ".local/browser-credentials.json"), "utf8"),
  );
  const output = path.join(root, "screenshots");
  await fs.mkdir(output, { recursive: true });
  const browser = await chromium.launch({
    executablePath:
      process.env.PLAYWRIGHT_CHROME_PATH ||
      (process.platform === "darwin"
        ? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
        : undefined),
    headless: true,
  });
  const failures = [];
  const captures = [];

  async function backendRequest(page, resource, method = "GET", data) {
    const headers = { Origin: "http://127.0.0.1:3000" };

    if (method !== "GET") {
      const response = await page.request.get("http://127.0.0.1:3000/api/backend/api/v1/auth/csrf");
      const csrf = await response.json();
      headers[csrf.headerName] = csrf.token;
    }

    const response = await page.request.fetch(
      `http://127.0.0.1:3000/api/backend/api/v1/${resource}`,
      {
        method,
        headers,
        data,
      },
    );
    assert.equal(response.ok(), true, `Unexpected response for ${resource}`);
    return response.json();
  }

  async function capture(page, name) {
    await page.evaluate(async () => {
      await document.fonts.ready;
      await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
    });
    await page.screenshot({
      path: path.join(output, name + ".png"),
      fullPage: true,
    });
    assert.equal(
      await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth),
      false,
      name + " overflows",
    );
    captures.push(name);
  }

  try {
    for (const role of ["ADMIN", "OPERATOR", "VIEWER", "DRIVER"]) {
      const context = await browser.newContext({
        viewport: { width: 1512, height: 1050 },
      });
      const page = await context.newPage();
      page.on("pageerror", (error) => failures.push(error.message));
      page.on("console", (message) => {
        if (message.type() === "error") {
          failures.push(message.text());
        }
      });
      await page.goto("http://127.0.0.1:3000/auth/login");

      await page.locator("main[aria-busy=false]").waitFor({ timeout: 60000 });
      if (role === "ADMIN") {
        await capture(page, "Anonymous - Sign In");
      }

      await page.getByLabel("Workspace", { exact: true }).fill(credentials.tenant);
      await page.getByLabel("Email address").fill(credentials.roles[role]);
      await page.getByLabel("Password", { exact: true }).fill(credentials.password);
      await page.getByRole("button", { name: "Sign in", exact: true }).click();
      await page.waitForURL(role === "DRIVER" ? "**/transactions" : "http://127.0.0.1:3000/", {
        timeout: 60000,
      });
      await page
        .getByRole("heading", {
          name: role === "DRIVER" ? "Charging sessions" : "Your network, at a glance.",
          exact: true,
        })
        .waitFor();
      await page
        .locator("[data-slot=skeleton]")
        .waitFor({ state: "detached", timeout: 30000 })
        .catch(() => {});

      if (role === "ADMIN") {
        await capture(page, "Administrator - Overview");
        await page.goto("http://127.0.0.1:3000/stations");
        await page.getByText("Paris · Atelier", { exact: true }).waitFor();
        await capture(page, "Administrator - Charging Stations");
        const tokens = await backendRequest(page, "tokens");
        const token = tokens.find((item) => item.uid === "TEST-DRIVER");
        const expiry = new Date(Date.now() + 86400000).toISOString();
        await backendRequest(page, "tokens", "POST", {
          uid: token.uid,
          userId: token.user_id,
          enabled: true,
          expiresAt: expiry,
        });
        await page.goto("http://127.0.0.1:3000/tokens");
        const tokenRow = page.getByRole("row").filter({ hasText: "TEST-DRIVER" });
        await tokenRow.getByRole("button", { name: "Edit", exact: true }).click();
        assert.equal(await page.getByLabel("User ID (optional)").inputValue(), token.user_id);
        const shownExpiry = await page.getByLabel("Expiry (optional)").inputValue();
        assert.equal(new Date(shownExpiry).valueOf(), new Date(expiry).valueOf());
        await page.getByRole("button", { name: "Save record", exact: true }).click();
        await page.getByRole("dialog").waitFor({ state: "hidden" });
        const savedTokens = await backendRequest(page, "tokens");
        const savedToken = savedTokens.find((item) => item.uid === "TEST-DRIVER");
        assert.equal(savedToken.user_id, token.user_id);
        assert.equal(new Date(savedToken.expires_at).valueOf(), new Date(expiry).valueOf());

        const previousAssets = await backendRequest(page, "records/assets");
        for (const asset of previousAssets.items) {
          if (asset.name === "Browser verification meter") {
            await backendRequest(page, `records/assets/${asset.id}`, "DELETE");
          }
        }
        await page.goto("http://127.0.0.1:3000/assets");
        await page.getByText("Campus — solar array", { exact: true }).waitFor();
        await page.getByRole("button", { name: "Add record", exact: true }).click();
        await page.getByLabel("Asset name", { exact: true }).fill("Browser verification meter");
        await page.getByLabel("Power (kW)", { exact: true }).fill("22");
        await page.getByRole("button", { name: "Save record", exact: true }).click();
        await page.getByRole("dialog").waitFor({ state: "hidden" });
        await page.getByText("Browser verification meter", { exact: true }).waitFor();
        await capture(page, "Administrator - Assets");
        await page.goto("http://127.0.0.1:3000/users");
        await page.getByText("Operator account", { exact: true }).waitFor();
        await capture(page, "Administrator - People and Roles");
        await page.setViewportSize({ width: 390, height: 844 });
        await page.goto("http://127.0.0.1:3000/");
        await page
          .getByRole("heading", {
            name: "Your network, at a glance.",
            exact: true,
          })
          .waitFor();
        await capture(page, "Administrator - Mobile Overview");
      }

      if (role === "OPERATOR") {
        await page.goto("http://127.0.0.1:3000/stations");
        await page.getByText("Paris · Atelier", { exact: true }).waitFor();
        assert.equal(
          await page.getByRole("button", { name: "Add record", exact: true }).count(),
          0,
        );
        await capture(page, "Operator - Charging Stations");
      }

      if (role === "VIEWER" || role === "DRIVER") {
        await page.goto("http://127.0.0.1:3000/transactions");
        await page.getByRole("heading", { name: "Charging sessions", exact: true }).waitFor();
        await page.getByRole("cell", { name: "CDT-1001", exact: true }).waitFor();
        await capture(page, role === "VIEWER" ? "Viewer - Sessions" : "Driver - My Sessions");
        assert.equal(
          await page.getByRole("link", { name: "People & roles", exact: true }).count(),
          0,
        );
      }

      await context.close();
    }

    assert.deepEqual(failures, []);
    await fs.writeFile(
      path.join(root, "docs/browser-results.json"),
      JSON.stringify(
        {
          executedAt: new Date().toISOString(),
          backend: "Spring Security + Spring MVC + JPA through a MockMvc stdio test transport",
          database: "H2 in PostgreSQL compatibility mode",
          fixtures: "Synthetic local data; OCPP transactions created through OcppService",
          assertions: [
            "Authenticated login for four roles",
            "Role-based navigation and station creation controls",
            "Asset creation through the dashboard form",
            "Token edits preserve owner and expiry",
            "Desktop and mobile overflow checks",
            "No browser page errors",
          ],
          screenshots: captures,
          pageErrors: failures,
        },
        null,
        2,
      ),
    );
    console.log(
      JSON.stringify({
        screenshots: captures.length,
        pageErrors: failures.length,
      }),
    );
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
