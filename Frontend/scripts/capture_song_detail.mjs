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

  const songDetailUrl = "https://producerworkbench.online/dashboard/songs/4d9896b9-27ba-429d-8637-dc4f288508e1";
  console.log(`Navigating to Song Detail page: ${songDetailUrl}...`);
  await page.goto(songDetailUrl, { waitUntil: "networkidle" });
  await page.waitForTimeout(2000);

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
  const filePath = path.join(outputDir, "sg-07.png");

  if (await mainLoc.isVisible()) {
    await mainLoc.screenshot({ path: filePath });
  } else {
    await page.screenshot({ path: filePath, fullPage: false });
  }

  console.log("✓ Saved exact Song Detail screenshot sg-07.png!");
  await browser.close();
}

main().catch((err) => {
  console.error("Error capturing song detail:", err);
  process.exit(1);
});
