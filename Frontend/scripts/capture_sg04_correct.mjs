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
  await page.waitForTimeout(1500);

  console.log("Navigating to /dashboard/songs/new for sg-04 (Tagged song mode)...");
  await page.goto("https://producerworkbench.online/dashboard/songs/new", { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);

  // Click the Attach Voice Tag checkbox label
  console.log("Checking Attach Voice Tag checkbox...");
  const checkboxLabel = page.locator('label').filter({ hasText: /Attach Voice Tag|gán/i }).first();
  if (await checkboxLabel.isVisible()) {
    await checkboxLabel.click();
    await page.waitForTimeout(1000);
  }

  // Click VoiceTagPicker to select a Voice Tag if present
  console.log("Selecting a Voice Tag from picker...");
  const pickerTrigger = page.locator('button#voice-tag-select, button').filter({ hasText: /Search voice tags|Select voice tag|Nam in the mix|Tag/i }).first();
  if (await pickerTrigger.isVisible()) {
    await pickerTrigger.click();
    await page.waitForTimeout(800);

    // Pick the first option inside the dropdown popup if visible
    const firstOption = page.locator('div[class*="neu-raised"] button, div button').filter({ hasText: /Nam|Tag|Producer/i }).first();
    if (await firstOption.isVisible()) {
      await firstOption.click();
      await page.waitForTimeout(800);
    }
  }

  await page.addStyleTag({
    content: `
      html, body {
        background-color: #e0e5ec !important;
        overflow: hidden !important;
        margin: 0 !important;
        padding: 0 !important;
      }
      ::-webkit-scrollbar {
        display: none !important;
      }
    `,
  }).catch(() => {});

  await page.waitForTimeout(1000);

  const mainLoc = page.locator("main").first();
  const filePath = path.join(outputDir, "sg-04.png");

  if (await mainLoc.isVisible()) {
    await mainLoc.screenshot({ path: filePath });
  } else {
    await page.screenshot({ path: filePath, fullPage: false });
  }

  console.log("✓ Saved correct Tagged Song screenshot sg-04.png!");
  await browser.close();
}

main().catch((err) => {
  console.error("Error capturing sg-04:", err);
  process.exit(1);
});
