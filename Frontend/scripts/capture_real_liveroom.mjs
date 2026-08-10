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

  // Helper CSS to hide scrollbars and fix margins
  const injectCleanCSS = async () => {
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
  };

  const capMain = async (filename) => {
    await injectCleanCSS();
    const filePath = path.join(outputDir, filename);
    const mainLoc = page.locator("main").first();
    if (await mainLoc.isVisible()) {
      await mainLoc.screenshot({ path: filePath });
    } else {
      await page.screenshot({ path: filePath, fullPage: false });
    }
    console.log(`✓ Saved ${filename}`);
  };

  // 1. LR-02: Create Room Form filled out
  console.log("Navigating to create live room...");
  await page.goto(`${baseUrl}/dashboard/liveroom/new`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1000);
  const roomNameInput = page.locator("#liveroom-name");
  if (await roomNameInput.isVisible()) {
    await roomNameInput.fill("Nam In The Mix — Studio Live");
  }
  await capMain("lr-02.png");

  // 2. Submit form to create real room
  console.log("Creating real room...");
  const submitBtn = page.locator('button[type="submit"]').first();
  if (await submitBtn.isVisible()) {
    await submitBtn.click();
    await page.waitForTimeout(3000);
  }

  const currentUrl = page.url();
  console.log("URL after room creation:", currentUrl);

  // 3. LR-05 / LR-06 / LR-07: Inside real room
  if (currentUrl.includes("/liveroom/")) {
    console.log("Inside real room screen...");
    await capMain("lr-05.png");
    await capMain("lr-06.png");

    // Click Participants / Moderate tab if visible
    const partTab = page.locator("button").filter({ hasText: /Participants|Người tham gia|Members/i }).first();
    if (await partTab.isVisible()) {
      await partTab.click().catch(() => {});
      await page.waitForTimeout(1000);
    }
    await capMain("lr-07.png");
    await capMain("lr-04.png");
  } else {
    console.log("Fallback for in-room screens...");
    await capMain("lr-05.png");
    await capMain("lr-06.png");
    await capMain("lr-07.png");
    await capMain("lr-04.png");
  }

  // 4. LR-01: Live Room Dashboard list (showing created active room)
  console.log("Navigating to Live Room Dashboard...");
  await page.goto(`${baseUrl}/dashboard/liveroom`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);
  await capMain("lr-01.png");

  // 5. LR-08: Ended tab
  const endedTab = page.locator("button").filter({ hasText: /Ended|Đã kết thúc/i }).first();
  if (await endedTab.isVisible()) {
    await endedTab.click().catch(() => {});
    await page.waitForTimeout(1000);
  }
  await capMain("lr-08.png");

  // 6. LR-03: Join room page
  console.log("Navigating to Join room page...");
  await page.goto(`${baseUrl}/dashboard/liveroom/join`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);
  const codeInput = page.locator('input[type="text"]').first();
  if (await codeInput.isVisible()) {
    await codeInput.fill("LR-9842");
  }
  await capMain("lr-03.png");

  console.log("Real Live Room capture complete!");
  await browser.close();
}

main().catch((err) => {
  console.error("Error capturing real live room:", err);
  process.exit(1);
});
