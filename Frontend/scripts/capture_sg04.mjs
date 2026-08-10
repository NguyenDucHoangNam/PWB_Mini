import { chromium } from "playwright";
import fs from "fs";
import path from "path";

async function main() {
  const outputDir = path.resolve("public/images/walkthrough");
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    deviceScaleFactor: 2,
  });
  const page = await context.newPage();

  console.log("Logging in...");
  await page.goto("https://producerworkbench.online/login", { waitUntil: "networkidle" });
  await page.fill("#email", "pro1@gmail.com");
  await page.fill("#password", "@NamHoang511");
  await page.click('button[type="submit"]');
  await page.waitForURL((url) => url.href.includes("/dashboard"), { timeout: 15000 }).catch(() => {});

  console.log("Navigating to /dashboard/songs/new for sg-04...");
  await page.goto("https://producerworkbench.online/dashboard/songs/new", { waitUntil: "networkidle" });
  await page.waitForTimeout(2000);

  // Click label for attachVoiceTag if visible
  const label = page.locator('label').filter({ hasText: /Attach|Voice Tag|gán/i }).first();
  if (await label.isVisible()) {
    await label.click().catch(() => {});
    await page.waitForTimeout(1000);
  }

  const filePath = path.join(outputDir, "sg-04.png");
  await page.screenshot({ path: filePath, fullPage: false });
  console.log("✓ Saved sg-04.png");

  await browser.close();
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
