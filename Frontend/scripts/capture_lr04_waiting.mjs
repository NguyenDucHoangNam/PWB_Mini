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
  const baseUrl = "https://producerworkbench.online";

  console.log("Logging in...");
  await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
  await page.fill("#email", "pro1@gmail.com");
  await page.fill("#password", "@NamHoang511");
  await page.click('button[type="submit"]');
  await page.waitForURL((url) => url.href.includes("/dashboard"), { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(1500);

  console.log("Navigating to Join room page for waiting door screen...");
  await page.goto(`${baseUrl}/dashboard/liveroom/join`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);

  // Fill sample code "LR-9842" to lookup
  const codeInput = page.locator('input[type="text"]').first();
  if (await codeInput.isVisible()) {
    await codeInput.fill("LR-9842");
    await page.waitForTimeout(500);
  }

  // Click lookup / find room button
  const findBtn = page.locator("button").filter({ hasText: /Find room|Tìm phòng/i }).first();
  if (await findBtn.isVisible()) {
    await findBtn.click().catch(() => {});
    await page.waitForTimeout(1000);
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

  const mainLoc = page.locator("main").first();
  const filePath = path.join(outputDir, "lr-04.png");

  if (await mainLoc.isVisible()) {
    await mainLoc.screenshot({ path: filePath });
  } else {
    await page.screenshot({ path: filePath, fullPage: false });
  }

  console.log("✓ Saved correct Waiting Room Door screenshot lr-04.png!");
  await browser.close();
}

main().catch((err) => {
  console.error("Error capturing lr-04:", err);
  process.exit(1);
});
