import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import assert from "node:assert/strict";
import { chromium } from "playwright";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const origin = process.env.CAPTURE_ORIGIN || "http://127.0.0.1:3000";
const output = path.join(root, "screenshots");
const captures = [];
const failures = [];
const browser = await chromium.launch({
  executablePath:
    process.env.PLAYWRIGHT_CHROME_PATH ||
    (process.platform === "darwin"
      ? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
      : undefined),
  headless: true,
});

async function request(page, resource, method = "GET", data) {
  const headers = { Origin: origin };
  if (method !== "GET") {
    const response = await page.request.get(`${origin}/api/backend/api/v1/auth/csrf`);
    const csrf = await response.json();
    headers[csrf.headerName] = csrf.token;
  }
  const response = await page.request.fetch(`${origin}/api/backend/api/v1/${resource}`, {
    method,
    headers,
    data,
  });
  assert.equal(response.ok(), true, `La requête ${resource} a échoué : ${response.status()}`);
  return response.json();
}

async function capture(page, name) {
  await page.evaluate(async () => {
    await document.fonts.ready;
    await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  });
  assert.equal(await page.locator("html").getAttribute("lang"), "fr");
  assert.equal(
    await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),
    false,
    `Débordement horizontal : ${name}`,
  );
  const primary = await page
    .locator("html")
    .evaluate((element) => getComputedStyle(element).getPropertyValue("--primary").trim());
  assert.equal(primary, "#164737", "Le thème vert doit être conservé.");
  const text = await page.locator("body").innerText();
  assert.doesNotMatch(
    text,
    /\b(Sign in|Sign out|Workspace|Charging stations|Charging sessions|No records|Save record|Connected|Offline|Administrator|Driver|Please wait|Close)\b/,
    `Texte anglais : ${name}`,
  );
  assert.match(
    await page.locator("body").evaluate((element) => getComputedStyle(element).fontFamily),
    /Source Sans 3/,
  );
  await page.screenshot({ path: path.join(output, `${name}.png`), fullPage: true });
  captures.push(name);
  console.log(`Capture : ${name}`);
}

async function login(page, credentials, role) {
  await page.goto(`${origin}/auth/login`);
  await page.getByLabel("Espace client", { exact: true }).fill(credentials.tenant);
  await page.getByLabel("Adresse électronique", { exact: true }).fill(credentials.roles[role]);
  await page.getByLabel("Mot de passe", { exact: true }).fill(credentials.password);
  await page.getByRole("button", { name: "Se connecter", exact: true }).click();
  await page
    .getByRole("heading", {
      name: role === "DRIVER" ? "Sessions de recharge" : "Vue d’ensemble du réseau",
      exact: true,
    })
    .waitFor();
}

async function workspacePage(page, route, heading) {
  await page.goto(`${origin}/${route}`);
  await page.getByRole("heading", { name: heading, exact: true }).waitFor();
}

try {
  await fs.mkdir(output, { recursive: true });
  const ready = await fetch(`${origin}/api/backend/api/v1/public/workspaces/options`);
  assert.equal(ready.ok, true, "Le serveur local de test doit être disponible.");
  const credentials = JSON.parse(
    await fs.readFile(path.join(root, ".local/browser-credentials.json"), "utf8"),
  );
  for (const role of process.env.CAPTURE_ROLES?.split(",") || [
    "ADMIN",
    "OPERATOR",
    "VIEWER",
    "DRIVER",
  ]) {
    const context = await browser.newContext({
      viewport: { width: 1440, height: 1000 },
      locale: "fr-FR",
    });
    const page = await context.newPage();
    page.on("pageerror", (error) => failures.push(error.message));
    await page.goto(`${origin}/auth/login`);
    await page.locator("main[aria-busy=false]").waitFor();
    if (role === "ADMIN") await capture(page, "01-connexion");
    await login(page, credentials, role);

    if (role === "ADMIN") {
      await page.locator(".overview-metrics").waitFor();
      await capture(page, "02-reseau-ordinateur");
      await workspacePage(page, "stations", "Bornes de recharge");
      await page.getByText("Paris · Atelier", { exact: true }).waitFor();
      await capture(page, "03-bornes");
      await page
        .getByRole("row")
        .filter({ hasText: "Paris · Atelier" })
        .getByRole("button", { name: "Consulter", exact: true })
        .click();
      await page.getByText("Commande OCPP 1.6", { exact: true }).waitFor();
      await capture(page, "04-detail-borne");
      await page.getByRole("button", { name: "Fermer", exact: true }).click();

      const tokens = await request(page, "tokens");
      const token = tokens.find((item) => item.uid === "TEST-DRIVER");
      const expiry = new Date(Date.now() + 86400000).toISOString();
      await request(page, "tokens", "POST", {
        uid: token.uid,
        userId: token.user_id,
        enabled: true,
        expiresAt: expiry,
      });
      await workspacePage(page, "tokens", "Badges et accès");
      await page
        .getByRole("row")
        .filter({ hasText: "TEST-DRIVER" })
        .getByRole("button", { name: "Modifier", exact: true })
        .click();
      assert.equal(
        await page.getByLabel("Identifiant de l’utilisateur (facultatif)").inputValue(),
        token.user_id,
      );
      assert.equal(
        new Date(await page.getByLabel("Expiration (facultative)").inputValue()).valueOf(),
        new Date(expiry).valueOf(),
      );
      await page.getByRole("button", { name: "Enregistrer", exact: true }).click();
      await page.getByRole("dialog").waitFor({ state: "hidden" });
      const saved = (await request(page, "tokens")).find((item) => item.uid === token.uid);
      assert.equal(saved.user_id, token.user_id);
      assert.equal(new Date(saved.expires_at).valueOf(), new Date(expiry).valueOf());

      const previousAssets = await request(page, "records/assets");
      for (const asset of previousAssets.items.filter(
        (item) => item.name === "Compteur de vérification",
      )) {
        await request(page, `records/assets/${asset.id}`, "DELETE");
      }
      await workspacePage(page, "assets", "Équipements");
      await page.getByRole("button", { name: "Ajouter un équipement", exact: true }).click();
      await page
        .getByLabel("Nom de l’équipement", { exact: true })
        .fill("Compteur de vérification");
      await page.getByLabel("Puissance (kW)", { exact: true }).fill("22");
      await page.getByRole("button", { name: "Enregistrer", exact: true }).click();
      await page.getByRole("dialog").waitFor({ state: "hidden" });
      await page.getByText("Compteur de vérification", { exact: true }).waitFor();
      await capture(page, "05-equipements");
      for (const [route, heading, file] of [
        ["users", "Utilisateurs et rôles", "06-utilisateurs"],
        ["pricing-definitions", "Tarifs", "07-tarifs"],
        ["payments", "Paiements", "08-paiements"],
        ["evses", "Points de charge publics", "09-points-publics"],
      ]) {
        await workspacePage(page, route, heading);
        if (route === "payments")
          await page.getByText("Prêt à encaisser", { exact: true }).waitFor();
        else await page.getByRole("row").nth(1).waitFor();
        await capture(page, file);
      }
      const evses = await request(page, "evses");
      const qr = await page.request.get(`${origin}/api/backend/api/v1/evses/${evses[0].id}/qr`);
      assert.equal(qr.ok(), true);
      assert.match(await qr.text(), /^<svg/);
      assert.equal(evses[0].portalUrl, `${origin}/charge/codetics/${credentials.publicEvse}`);
      for (const [width, name] of [
        [768, "10-reseau-tablette"],
        [390, "11-reseau-mobile"],
      ]) {
        await page.setViewportSize({ width, height: 844 });
        await workspacePage(page, "", "Vue d’ensemble du réseau");
        await page.locator(".overview-metrics").waitFor();
        await capture(page, name);
      }
      await page
        .getByRole("button", { name: "Afficher ou masquer la navigation", exact: true })
        .click();
      await page.getByRole("dialog").waitFor();
      await capture(page, "12-navigation-mobile");
      await page.keyboard.press("Escape");
      await page.goto(evses[0].portalUrl);
      await page.getByRole("button", { name: "Payer et démarrer", exact: true }).waitFor();
      assert.equal(
        await page.getByRole("button", { name: "Payer et démarrer", exact: true }).isEnabled(),
        true,
      );
      await capture(page, "13-portail-recharge-mobile");
      await page.setViewportSize({ width: 1440, height: 1000 });
      await capture(page, "14-portail-recharge-ordinateur");
      // Le prestataire de test est injecté uniquement dans le serveur local H2.
      await page.route("https://checkout.stripe.com/test/**", (route) =>
        route.fulfill({
          contentType: "text/html",
          body: "<html lang='fr'><body>Paiement de test</body></html>",
        }),
      );
      await page.getByRole("button", { name: "Payer et démarrer", exact: true }).click();
      await page.waitForURL("https://checkout.stripe.com/test/**");
      const orderId = new URL(page.url()).pathname.split("/").at(-1);
      const notification = await page.request.post(`${origin}/api/backend/api/v1/stripe/webhook`, {
        headers: { "Stripe-Signature": "signature-de-test" },
        data: {
          id: `evt_${orderId}`,
          account: "acct_demo",
          type: "checkout.session.completed",
          data: {
            id: `cs_${orderId}`,
            metadata: { charging_order_id: orderId },
            payment_status: "paid",
            amount_total: 2000,
            currency: "eur",
            payment_intent: `pi_${orderId}`,
          },
        },
      });
      assert.equal(notification.status(), 204);
      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto(`${evses[0].portalUrl}/sessions/${orderId}`);
      await page.getByRole("heading", { name: "Démarrage demandé", exact: true }).waitFor();
      assert.equal(
        await page.getByRole("button", { name: "Arrêter la recharge", exact: true }).count(),
        0,
      );
      await capture(page, "18-session-paiement-mobile");
    } else if (role === "OPERATOR") {
      await workspacePage(page, "stations", "Bornes de recharge");
      await page.getByText("Paris · Atelier", { exact: true }).waitFor();
      assert.equal(
        await page.getByRole("button", { name: "Ajouter une borne", exact: true }).count(),
        0,
      );
      await capture(page, "15-exploitant-bornes");
    } else {
      await workspacePage(page, "transactions", "Sessions de recharge");
      await page
        .getByText("CDT-1001", { exact: true })
        .waitFor({ timeout: 10000 })
        .catch(async (error) => {
          console.log((await page.locator("body").innerText()).slice(0, 4000));
          console.log(JSON.stringify(await request(page, "transactions?offset=0&limit=50")));
          throw error;
        });
      assert.equal(
        await page.getByRole("link", { name: "Utilisateurs et rôles", exact: true }).count(),
        0,
      );
      await capture(page, role === "VIEWER" ? "16-lecteur-sessions" : "17-conducteur-sessions");
    }
    await context.close();
  }
  assert.deepEqual(failures, []);
  console.log(JSON.stringify({ captures: captures.length, erreurs: failures.length }));
} finally {
  await browser.close();
}
