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

  console.log("Navigating to /dashboard/voice-tags/new for clean main element capture...");
  await page.goto("https://producerworkbench.online/dashboard/voice-tags/new", { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);

  // Click TTS button if present
  const ttsBtn = page.locator("button").filter({ hasText: /AI|Generate|TTS|Mic/i }).first();
  if (await ttsBtn.isVisible()) await ttsBtn.click();
  await page.waitForTimeout(1000);

  const mainLocator = page.locator("main");
  const screenshotPath = path.join(outputDir, "vt-03.png");
  
  if (await mainLocator.isVisible()) {
    console.log("Capturing main element...");
    await mainLocator.screenshot({ path: screenshotPath });
  } else {
    console.log("Fallback to page screenshot...");
    await page.screenshot({ path: screenshotPath });
  }

  console.log("✓ Saved cropped main element screenshot vt-03.png!");
  await browser.close();
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
