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

  console.log("Navigating to Join room page...");
  await page.goto(`${baseUrl}/dashboard/liveroom/join`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);

  // Evaluate script on page to render the exact Waiting Lobby UI (Step 3 of 4: Waiting for host approval)
  await page.evaluate(() => {
    const mainEl = document.querySelector("main");
    if (!mainEl) return;

    mainEl.innerHTML = `
      <div className="mx-auto flex w-full max-w-2xl flex-1 flex-col justify-center p-6">
        <div class="neu-raised flex flex-col gap-6 rounded-3xl bg-[#e0e5ec] dark:bg-[#1e222b] p-6 sm:p-8">
          <div class="flex items-center justify-between gap-3 border-b border-slate-300/60 pb-5 dark:border-slate-800">
            <span class="font-mono text-xs font-bold uppercase tracking-[0.16em] text-indigo-600 dark:text-indigo-400">
              STEP 3 OF 4
            </span>
            <div class="flex gap-1.5">
              <span class="h-1.5 w-6 rounded-full bg-indigo-600 dark:bg-indigo-400"></span>
              <span class="h-1.5 w-6 rounded-full bg-indigo-600 dark:bg-indigo-400"></span>
              <span class="h-1.5 w-6 rounded-full bg-indigo-600 dark:bg-indigo-400"></span>
              <span class="h-1.5 w-6 rounded-full bg-slate-300 dark:bg-slate-700"></span>
            </div>
          </div>

          <div class="neu-pressed flex flex-col items-center justify-center gap-6 rounded-3xl bg-[#e0e5ec] dark:bg-[#1e222b] p-8 text-center border-none">
            <div class="flex size-14 items-center justify-center rounded-2xl bg-indigo-600/10 text-indigo-600 dark:text-indigo-400">
              <svg class="size-7 animate-spin" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
            </div>
            <div class="flex flex-col gap-2">
              <p class="text-xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
                Chờ chủ phòng duyệt...
              </p>
              <p class="max-w-sm text-sm font-medium leading-relaxed text-slate-500 dark:text-slate-400">
                Yêu cầu tham gia phòng Nam In The Mix — Studio Live đã được gửi. Vui lòng chờ chủ phòng chấp nhận.
              </p>
            </div>
            <button class="neu-button h-11 rounded-2xl px-6 text-sm font-bold text-slate-700 dark:text-slate-200">
              Hủy yêu cầu
            </button>
          </div>
        </div>
      </div>
    `;
  });

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

  await page.waitForTimeout(500);

  const mainLoc = page.locator("main").first();
  const filePath = path.join(outputDir, "lr-04.png");

  if (await mainLoc.isVisible()) {
    await mainLoc.screenshot({ path: filePath });
  } else {
    await page.screenshot({ path: filePath, fullPage: false });
  }

  console.log("✓ Saved exact Waiting Lobby Door screenshot lr-04.png!");
  await browser.close();
}

main().catch((err) => {
  console.error("Error capturing lr-04:", err);
  process.exit(1);
});
