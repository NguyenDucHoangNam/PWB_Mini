import { chromium } from "playwright";
import fs from "fs";
import path from "path";

async function main() {
  const outputDir = path.resolve("public/images/walkthrough");
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }

  console.log("Launching browser...");
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    deviceScaleFactor: 2,
  });
  const page = await context.newPage();

  console.log("Navigating to login page...");
  await page.goto("https://producerworkbench.online/login", { waitUntil: "networkidle" });
  await page.waitForTimeout(1000);

  console.log("Filling credentials...");
  await page.fill("#email", "pro1@gmail.com");
  await page.fill("#password", "@NamHoang511");

  console.log("Submitting login form...");
  await page.click('button[type="submit"]');

  console.log("Waiting for navigation to dashboard...");
  await page.waitForURL((url) => url.href.includes("/dashboard"), { timeout: 15000 }).catch(err => {
    console.log("URL after timeout:", page.url());
  });

  await page.waitForTimeout(2000);
  console.log("Current URL after login:", page.url());

  console.log("Navigating to /dashboard/voice-tags...");
  await page.goto("https://producerworkbench.online/dashboard/voice-tags", { waitUntil: "networkidle" });
  await page.waitForTimeout(3000);

  const screenshotPath = path.join(outputDir, "vt-01.png");
  console.log(`Capturing screenshot to ${screenshotPath}...`);
  await page.screenshot({ path: screenshotPath, fullPage: false });

  console.log("Screenshot captured successfully!");
  await browser.close();
}

main().catch((err) => {
  console.error("Error capturing screenshot:", err);
  process.exit(1);
});
