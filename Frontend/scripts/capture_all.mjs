import { chromium } from "playwright";
import fs from "fs";
import path from "path";

async function main() {
  const outputDir = path.resolve("public/images/walkthrough");
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }

  console.log("Launching browser for clean content-element capture...");
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

  // Helper function to capture screenshot of content element safely
  async function cap(url, filename, actionBefore) {
    try {
      console.log(`[CAP] ${filename} -> ${url}`);
      await page.goto(url, { waitUntil: "networkidle" }).catch(() => {});
      await page.waitForTimeout(1500);

      if (actionBefore) {
        await actionBefore(page).catch(() => {});
        await page.waitForTimeout(1500);
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
      const filePath = path.join(outputDir, filename);

      if (await mainLoc.isVisible()) {
        await mainLoc.screenshot({ path: filePath });
      } else {
        await page.screenshot({ path: filePath, fullPage: false });
      }

      console.log(`✓ Saved ${filename}`);
    } catch (err) {
      console.error(`✗ Error capturing ${filename}:`, err.message);
    }
  }

  // --- Voice Tags ---
  await cap(`${baseUrl}/dashboard/voice-tags`, "vt-01.png");
  await cap(`${baseUrl}/dashboard/voice-tags/new`, "vt-02.png");

  // VT-03: TTS Form
  await cap(`${baseUrl}/dashboard/voice-tags/new`, "vt-03.png", async (p) => {
    const ttsBtn = p.locator("button").filter({ hasText: /AI|Generate|TTS|Mic/i }).first();
    if (await ttsBtn.isVisible()) await ttsBtn.click();
  });

  // VT-04: Upload Form
  await cap(`${baseUrl}/dashboard/voice-tags/new`, "vt-04.png", async (p) => {
    const uploadBtn = p.locator("button").filter({ hasText: /Upload|own audio/i }).first();
    if (await uploadBtn.isVisible()) await uploadBtn.click();
  });

  // VT-05: Voice Tag Preview / Playing state
  await cap(`${baseUrl}/dashboard/voice-tags`, "vt-05.png", async (p) => {
    const playBtn = p.locator("button").filter({ hasText: /Preview/i }).first();
    if (await playBtn.isVisible()) await playBtn.click();
  });

  // VT-06: Voice Tags list after save
  await cap(`${baseUrl}/dashboard/voice-tags`, "vt-06.png");

  // VT-07: Voice Tag edit / action menu
  await cap(`${baseUrl}/dashboard/voice-tags`, "vt-07.png", async (p) => {
    const actionBtn = p.locator("button").filter({ hasText: /Edit|More|Actions|Rename/i }).first();
    if (await actionBtn.isVisible()) await actionBtn.click();
  });

  // --- Songs ---
  await cap(`${baseUrl}/dashboard/songs`, "sg-01.png");
  await cap(`${baseUrl}/dashboard/songs/new`, "sg-02.png");
  await cap(`${baseUrl}/dashboard/songs/new`, "sg-03.png");
  await cap(`${baseUrl}/dashboard/songs/new`, "sg-04.png");
  await cap(`${baseUrl}/dashboard/songs/new`, "sg-05.png");
  await cap(`${baseUrl}/dashboard/songs/new`, "sg-06.png");
  await cap(`${baseUrl}/dashboard/songs`, "sg-08.png");

  // Detail song
  try {
    await page.goto(`${baseUrl}/dashboard/songs`, { waitUntil: "networkidle" });
    const firstSongLink = page.locator('a[href*="/dashboard/songs/"]').first();
    if (await firstSongLink.isVisible()) {
      const songHref = await firstSongLink.getAttribute("href");
      if (songHref && !songHref.endsWith("/new")) {
        await cap(`${baseUrl}${songHref}`, "sg-07.png");
      } else {
        await cap(`${baseUrl}/dashboard/songs`, "sg-07.png");
      }
    } else {
      await cap(`${baseUrl}/dashboard/songs`, "sg-07.png");
    }
  } catch (e) {
    await cap(`${baseUrl}/dashboard/songs`, "sg-07.png");
  }

  // --- Live Room ---
  await cap(`${baseUrl}/dashboard/liveroom`, "lr-01.png");
  await cap(`${baseUrl}/dashboard/liveroom/new`, "lr-02.png");
  await cap(`${baseUrl}/dashboard/liveroom/join`, "lr-03.png");
  await cap(`${baseUrl}/dashboard/liveroom`, "lr-04.png");
  await cap(`${baseUrl}/dashboard/liveroom`, "lr-05.png");
  await cap(`${baseUrl}/dashboard/liveroom`, "lr-06.png");
  await cap(`${baseUrl}/dashboard/liveroom`, "lr-07.png");
  await cap(`${baseUrl}/dashboard/liveroom`, "lr-08.png", async (p) => {
    const endedTab = p.locator("button").filter({ hasText: /Ended|Đã kết thúc/i }).first();
    if (await endedTab.isVisible()) await endedTab.click();
  });

  console.log("Full cropped content capture completed!");
  await browser.close();
}

main().catch((err) => {
  console.error("Error in capture_all:", err);
  process.exit(1);
});
