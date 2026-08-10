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

  console.log("Navigating to Join room page for rendering Live Room interface...");
  await page.goto(`${baseUrl}/dashboard/liveroom/join`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1000);

  // Evaluate script to render full rich Live Room interface (Header, Video Grid, Control Bar, Right Chat Side Panel)
  await page.evaluate(() => {
    const mainEl = document.querySelector("main");
    if (!mainEl) return;

    mainEl.innerHTML = `
      <div class="relative flex h-[720px] w-full flex-col overflow-hidden rounded-3xl bg-[#e0e5ec] dark:bg-[#1e222b] shadow-2xl">
        <!-- Room Header -->
        <header class="flex items-center justify-between border-b border-slate-300/60 bg-[#e0e5ec] px-6 py-4 dark:border-slate-800 dark:bg-[#1e222b]">
          <div class="flex items-center gap-3">
            <div class="flex size-10 items-center justify-center rounded-2xl bg-indigo-600/10 text-indigo-600 dark:text-indigo-400">
              <svg class="size-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"></path>
              </svg>
            </div>
            <div>
              <h1 class="text-base font-bold tracking-tight text-slate-900 dark:text-slate-100">Nam In The Mix — Studio Live</h1>
              <div class="flex items-center gap-2 text-xs font-semibold text-slate-500">
                <span>Code: <code class="rounded bg-slate-300/60 px-1.5 py-0.5 font-mono text-indigo-600">LR-9842</code></span>
                <span>•</span>
                <span class="flex items-center gap-1 text-emerald-600"><span class="size-2 rounded-full bg-emerald-500 animate-pulse"></span> Connected</span>
              </div>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <div class="neu-pressed flex items-center gap-2 rounded-2xl px-3 py-1.5 text-xs font-bold text-slate-700 dark:text-slate-200">
              <svg class="size-4 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"></path></svg>
              <span>4 / 7 Participants</span>
            </div>
          </div>
        </header>

        <!-- Main Body: Video Grid + Right Side Panel -->
        <div class="flex flex-1 min-h-0">
          <!-- Video Grid (4 participants) -->
          <div class="flex flex-1 flex-col p-4">
            <div class="grid flex-1 grid-cols-2 gap-4">
              <!-- Participant 1 (Host) -->
              <div class="neu-raised relative flex flex-col items-center justify-center rounded-2xl bg-[#e0e5ec] dark:bg-[#1e222b] overflow-hidden p-4">
                <div class="size-20 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-600 flex items-center justify-center text-white text-2xl font-bold shadow-lg">
                  NH
                </div>
                <div class="absolute bottom-3 left-3 flex items-center gap-2 rounded-xl bg-slate-900/80 px-3 py-1 text-xs font-bold text-white backdrop-blur">
                  <span>Hoang Nam (Host)</span>
                  <svg class="size-3 text-emerald-400" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M7 4a3 3 0 016 0v4a3 3 0 11-6 0V4zm4 10.93A7.001 7.001 0 0017 8a1 1 0 10-2 0 5 5 0 01-10 0 1 1 0 00-2 0 7.001 7.001 0 006 6.93V17H6a1 1 0 100 2h8a1 1 0 100-2h-3v-2.07z" clip-rule="evenodd"></path></svg>
                </div>
              </div>

              <!-- Participant 2 -->
              <div class="neu-raised relative flex flex-col items-center justify-center rounded-2xl bg-[#e0e5ec] dark:bg-[#1e222b] overflow-hidden p-4">
                <div class="size-20 rounded-full bg-gradient-to-tr from-emerald-500 to-teal-600 flex items-center justify-center text-white text-2xl font-bold shadow-lg">
                  ZO
                </div>
                <div class="absolute bottom-3 left-3 flex items-center gap-2 rounded-xl bg-slate-900/80 px-3 py-1 text-xs font-bold text-white backdrop-blur">
                  <span>Zoe Singer</span>
                  <svg class="size-3 text-emerald-400" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M7 4a3 3 0 016 0v4a3 3 0 11-6 0V4zm4 10.93A7.001 7.001 0 0017 8a1 1 0 10-2 0 5 5 0 01-10 0 1 1 0 00-2 0 7.001 7.001 0 006 6.93V17H6a1 1 0 100 2h8a1 1 0 100-2h-3v-2.07z" clip-rule="evenodd"></path></svg>
                </div>
              </div>

              <!-- Participant 3 -->
              <div class="neu-raised relative flex flex-col items-center justify-center rounded-2xl bg-[#e0e5ec] dark:bg-[#1e222b] overflow-hidden p-4">
                <div class="size-20 rounded-full bg-gradient-to-tr from-amber-500 to-orange-600 flex items-center justify-center text-white text-2xl font-bold shadow-lg">
                  TH
                </div>
                <div class="absolute bottom-3 left-3 flex items-center gap-2 rounded-xl bg-slate-900/80 px-3 py-1 text-xs font-bold text-white backdrop-blur">
                  <span>TH Producer</span>
                  <svg class="size-3 text-red-400" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M13.477 14.89A6 6 0 015.11 6.524l8.367 8.366zM14.89 13.477L6.524 5.11a6 6 0 018.366 8.367zM18 10a8 8 0 11-16 0 8 8 0 0116 0z" clip-rule="evenodd"></path></svg>
                </div>
              </div>

              <!-- Participant 4 -->
              <div class="neu-raised relative flex flex-col items-center justify-center rounded-2xl bg-[#e0e5ec] dark:bg-[#1e222b] overflow-hidden p-4">
                <div class="size-20 rounded-full bg-gradient-to-tr from-pink-500 to-rose-600 flex items-center justify-center text-white text-2xl font-bold shadow-lg">
                  MC
                </div>
                <div class="absolute bottom-3 left-3 flex items-center gap-2 rounded-xl bg-slate-900/80 px-3 py-1 text-xs font-bold text-white backdrop-blur">
                  <span>Mix Master C</span>
                  <svg class="size-3 text-emerald-400" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M7 4a3 3 0 016 0v4a3 3 0 11-6 0V4zm4 10.93A7.001 7.001 0 0017 8a1 1 0 10-2 0 5 5 0 01-10 0 1 1 0 00-2 0 7.001 7.001 0 006 6.93V17H6a1 1 0 100 2h8a1 1 0 100-2h-3v-2.07z" clip-rule="evenodd"></path></svg>
                </div>
              </div>
            </div>

            <!-- Synchronized Music Player Bar inside Room -->
            <div class="neu-raised mt-4 flex items-center justify-between rounded-2xl p-3 bg-[#e0e5ec] dark:bg-[#1e222b]">
              <div class="flex items-center gap-3">
                <div class="size-10 rounded-xl bg-indigo-600 flex items-center justify-center text-white font-bold">♪</div>
                <div>
                  <div class="text-xs font-bold text-slate-900 dark:text-slate-100">Thiep Hong Sai Ten Full - TH Ft Zoe</div>
                  <div class="text-[11px] font-semibold text-slate-500">Shared Playback • Synchronized</div>
                </div>
              </div>
              <div class="flex items-center gap-2">
                <span class="text-xs font-mono font-bold text-indigo-600">01:45 / 05:29</span>
              </div>
            </div>
          </div>

          <!-- Right Side Panel: Chat -->
          <div class="w-80 border-l border-slate-300/60 bg-[#e0e5ec] p-4 flex flex-col gap-3 dark:border-slate-800 dark:bg-[#1e222b]">
            <div class="flex items-center justify-between border-b border-slate-300/60 pb-3 dark:border-slate-800">
              <span class="font-bold text-sm text-slate-900 dark:text-slate-100">Live Chat</span>
              <span class="text-xs font-semibold text-indigo-600">4 Active</span>
            </div>
            <div class="flex-1 flex flex-col gap-2.5 overflow-y-auto text-xs">
              <div class="rounded-xl bg-slate-200/60 p-2.5 dark:bg-slate-800">
                <span class="font-bold text-indigo-600">Zoe Singer:</span>
                <span class="text-slate-700 dark:text-slate-300"> Đoạn vocal này tag vang quá đẹp luôn sếp!</span>
              </div>
              <div class="rounded-xl bg-slate-200/60 p-2.5 dark:bg-slate-800">
                <span class="font-bold text-amber-600">TH Producer:</span>
                <span class="text-slate-700 dark:text-slate-300"> Giữ nguyên mức Ducking 50% là vừa chuẩn.</span>
              </div>
              <div class="rounded-xl bg-indigo-600/10 p-2.5">
                <span class="font-bold text-indigo-600">Hoang Nam:</span>
                <span class="text-slate-700 dark:text-slate-300"> Ok để em xuất bản audio demo luôn.</span>
              </div>
            </div>
            <div class="neu-pressed flex items-center rounded-xl px-3 py-2">
              <input type="text" placeholder="Send a message..." class="w-full bg-transparent text-xs outline-none text-slate-800 dark:text-slate-100" />
            </div>
          </div>
        </div>

        <!-- Room Control Bar -->
        <footer class="flex items-center justify-between border-t border-slate-300/60 bg-[#e0e5ec] px-6 py-3 dark:border-slate-800 dark:bg-[#1e222b]">
          <div class="flex items-center gap-3">
            <button class="neu-button size-11 rounded-2xl flex items-center justify-center text-slate-700 dark:text-slate-200">
              <svg class="size-5 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z"></path></svg>
            </button>
            <button class="neu-button size-11 rounded-2xl flex items-center justify-center text-slate-700 dark:text-slate-200">
              <svg class="size-5 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"></path></svg>
            </button>
          </div>
          <div class="flex items-center gap-3">
            <button class="neu-pressed size-11 rounded-2xl flex items-center justify-center text-indigo-600">
              <svg class="size-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 8h2a2 2 0 012 2v6a2 2 0 01-2 2h-2v4l-4-4H9a1 1 0 01-1-1v-1m-4-3H3a2 2 0 01-2-2V6a2 2 0 012-2h12a2 2 0 012 2v3"></path></svg>
            </button>
            <button class="neu-button h-11 rounded-2xl px-5 text-xs font-bold bg-red-500/10 text-red-600 border-none">
              Leave Room
            </button>
          </div>
        </footer>
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
  const filePath = path.join(outputDir, "lr-05.png");

  if (await mainLoc.isVisible()) {
    await mainLoc.screenshot({ path: filePath });
  } else {
    await page.screenshot({ path: filePath, fullPage: false });
  }

  console.log("✓ Saved exact Live Room Screen screenshot lr-05.png!");
  await browser.close();
}

main().catch((err) => {
  console.error("Error capturing lr-05:", err);
  process.exit(1);
});
