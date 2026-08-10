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

  await page.goto(`${baseUrl}/dashboard/liveroom/join`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1000);

  // 1. Render LR-06 (Music & Chat focused view)
  console.log("Rendering LR-06 (Music playback & Chat focus)...");
  await page.evaluate(() => {
    const mainEl = document.querySelector("main");
    if (!mainEl) return;

    mainEl.innerHTML = `
      <div class="relative flex h-[720px] w-full flex-col overflow-hidden rounded-3xl bg-[#e0e5ec] dark:bg-[#1e222b] shadow-2xl">
        <header class="flex items-center justify-between border-b border-slate-300/60 bg-[#e0e5ec] px-6 py-4 dark:border-slate-800 dark:bg-[#1e222b]">
          <div class="flex items-center gap-3">
            <div class="flex size-10 items-center justify-center rounded-2xl bg-indigo-600/10 text-indigo-600 dark:text-indigo-400">
              <svg class="size-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19V6l12-2v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 .895-2 3-2 3 .895 3 2zm12-2c0 1.105-1.343 2-3 2s-3-.895-3-2 .895-2 3-2 3 .895 3 2zM9 10l12-2"></path></svg>
            </div>
            <div>
              <h1 class="text-base font-bold tracking-tight text-slate-900 dark:text-slate-100">Nam In The Mix — Studio Live</h1>
              <div class="flex items-center gap-2 text-xs font-semibold text-slate-500">
                <span>Synchronized Audio Session</span>
                <span>•</span>
                <span class="flex items-center gap-1 text-emerald-600"><span class="size-2 rounded-full bg-emerald-500 animate-pulse"></span> Playing Live</span>
              </div>
            </div>
          </div>
          <div class="neu-pressed flex items-center gap-2 rounded-2xl px-3 py-1.5 text-xs font-bold text-slate-700 dark:text-slate-200">
            <span>4 / 7 Online</span>
          </div>
        </header>

        <div class="flex flex-1 min-h-0">
          <div class="flex flex-1 flex-col p-6 gap-6">
            <div class="grid flex-1 grid-cols-2 gap-4">
              <div class="neu-raised relative flex flex-col items-center justify-center rounded-2xl bg-[#e0e5ec] dark:bg-[#1e222b] overflow-hidden p-4">
                <div class="size-16 rounded-full bg-indigo-600 text-white flex items-center justify-center font-bold text-xl">NH</div>
                <span class="mt-2 text-xs font-bold text-slate-800 dark:text-slate-100">Hoang Nam (Host)</span>
              </div>
              <div class="neu-raised relative flex flex-col items-center justify-center rounded-2xl bg-[#e0e5ec] dark:bg-[#1e222b] overflow-hidden p-4">
                <div class="size-16 rounded-full bg-emerald-600 text-white flex items-center justify-center font-bold text-xl">ZO</div>
                <span class="mt-2 text-xs font-bold text-slate-800 dark:text-slate-100">Zoe Singer</span>
              </div>
            </div>

            <!-- Synchronized Music Player Waveform Box -->
            <div class="neu-pressed flex flex-col gap-3 rounded-2xl p-5 bg-[#e0e5ec] dark:bg-[#1e222b] border-none">
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-3">
                  <button class="neu-button size-12 rounded-2xl flex items-center justify-center text-indigo-600 font-bold text-lg">⏸</button>
                  <div>
                    <div class="text-sm font-bold text-slate-900 dark:text-slate-100">Thiep Hong Sai Ten Full - TH Ft Zoe</div>
                    <div class="text-xs font-medium text-indigo-600">Voice Tag: Producer Tag Nam in the mix (Interval: 22s)</div>
                  </div>
                </div>
                <span class="text-xs font-mono font-bold text-slate-600">02:14 / 05:29</span>
              </div>
              <div class="h-3 w-full rounded-full bg-slate-300 dark:bg-slate-700 overflow-hidden relative">
                <div class="h-full bg-gradient-to-r from-indigo-500 to-purple-600 w-[40%] rounded-full"></div>
              </div>
            </div>
          </div>

          <!-- Right Side Chat Panel -->
          <div class="w-80 border-l border-slate-300/60 bg-[#e0e5ec] p-4 flex flex-col gap-3 dark:border-slate-800 dark:bg-[#1e222b]">
            <div class="flex items-center justify-between border-b border-slate-300/60 pb-3 dark:border-slate-800">
              <span class="font-bold text-sm text-slate-900 dark:text-slate-100">Synchronized Chat</span>
              <span class="text-xs font-semibold text-emerald-600">Live Sync</span>
            </div>
            <div class="flex-1 flex flex-col gap-3 overflow-y-auto text-xs">
              <div class="rounded-xl bg-slate-200/70 p-3 dark:bg-slate-800">
                <span class="font-bold text-indigo-600">Zoe Singer:</span>
                <p class="mt-1 text-slate-700 dark:text-slate-300">Đoạn 02:14 nghe tag chèn khớp nhịp beat quá sếp ơi!</p>
              </div>
              <div class="rounded-xl bg-slate-200/70 p-3 dark:bg-slate-800">
                <span class="font-bold text-amber-600">TH Producer:</span>
                <p class="mt-1 text-slate-700 dark:text-slate-300">Âm lượng tag 80% nghe rõ chuẩn bài rồi.</p>
              </div>
              <div class="rounded-xl bg-indigo-600/10 p-3">
                <span class="font-bold text-indigo-600">Hoang Nam (Host):</span>
                <p class="mt-1 text-slate-700 dark:text-slate-300">Đang đồng bộ nhạc realtime cho cả phòng nghe cùng lúc.</p>
              </div>
            </div>
            <div class="neu-pressed flex items-center rounded-xl px-3 py-2.5">
              <input type="text" placeholder="Gửi tin nhắn..." class="w-full bg-transparent text-xs outline-none text-slate-800 dark:text-slate-100" />
            </div>
          </div>
        </div>

        <footer class="flex items-center justify-between border-t border-slate-300/60 bg-[#e0e5ec] px-6 py-3 dark:border-slate-800 dark:bg-[#1e222b]">
          <div class="flex items-center gap-3">
            <button class="neu-button size-10 rounded-xl text-xs font-bold text-slate-700">Mic On</button>
            <button class="neu-button size-10 rounded-xl text-xs font-bold text-slate-700">Cam On</button>
          </div>
          <button class="neu-button h-10 rounded-xl px-4 text-xs font-bold text-red-600">Rời phòng</button>
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
      ::-webkit-scrollbar { display: none !important; }
    `,
  }).catch(() => {});

  const mainLoc = page.locator("main").first();
  await mainLoc.screenshot({ path: path.join(outputDir, "lr-06.png") });
  console.log("✓ Saved LR-06 (Music & Chat) lr-06.png!");

  // 2. Render LR-07 (Participants Moderation Panel view)
  console.log("Rendering LR-07 (Participants Moderation Panel focus)...");
  await page.evaluate(() => {
    const mainEl = document.querySelector("main");
    if (!mainEl) return;

    mainEl.innerHTML = `
      <div class="relative flex h-[720px] w-full flex-col overflow-hidden rounded-3xl bg-[#e0e5ec] dark:bg-[#1e222b] shadow-2xl">
        <header class="flex items-center justify-between border-b border-slate-300/60 bg-[#e0e5ec] px-6 py-4 dark:border-slate-800 dark:bg-[#1e222b]">
          <div class="flex items-center gap-3">
            <div class="flex size-10 items-center justify-center rounded-2xl bg-indigo-600/10 text-indigo-600 dark:text-indigo-400">
              <svg class="size-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"></path></svg>
            </div>
            <div>
              <h1 class="text-base font-bold tracking-tight text-slate-900 dark:text-slate-100">Nam In The Mix — Studio Live</h1>
              <div class="text-xs font-semibold text-slate-500">Quản lý người tham gia & phân quyền chủ phòng</div>
            </div>
          </div>
        </header>

        <div class="flex flex-1 min-h-0">
          <div class="flex flex-1 flex-col p-6">
            <div class="grid flex-1 grid-cols-2 gap-4">
              <div class="neu-raised flex flex-col items-center justify-center rounded-2xl p-4">
                <div class="size-16 rounded-full bg-indigo-600 text-white flex items-center justify-center font-bold text-xl">NH</div>
                <span class="mt-2 text-xs font-bold text-slate-800">Hoang Nam (Host)</span>
              </div>
              <div class="neu-raised flex flex-col items-center justify-center rounded-2xl p-4">
                <div class="size-16 rounded-full bg-emerald-600 text-white flex items-center justify-center font-bold text-xl">ZO</div>
                <span class="mt-2 text-xs font-bold text-slate-800">Zoe Singer</span>
              </div>
            </div>
          </div>

          <!-- Right Side Participants Panel with Moderation Actions -->
          <div class="w-80 border-l border-slate-300/60 bg-[#e0e5ec] p-4 flex flex-col gap-4 dark:border-slate-800 dark:bg-[#1e222b]">
            <div class="flex items-center justify-between border-b border-slate-300/60 pb-3 dark:border-slate-800">
              <span class="font-bold text-sm text-slate-900 dark:text-slate-100">Người tham gia (4)</span>
              <span class="text-xs font-bold text-indigo-600">Chủ phòng</span>
            </div>

            <div class="flex flex-col gap-3 text-xs">
              <!-- Host -->
              <div class="neu-pressed flex items-center justify-between rounded-xl p-3 border-none">
                <div class="flex items-center gap-2.5">
                  <div class="size-8 rounded-full bg-indigo-600 text-white font-bold flex items-center justify-center">NH</div>
                  <div>
                    <div class="font-bold text-slate-900 dark:text-slate-100">Hoang Nam</div>
                    <div class="text-[10px] font-semibold text-indigo-600">Chủ phòng</div>
                  </div>
                </div>
                <span class="text-emerald-600 font-bold">Mic Bật</span>
              </div>

              <!-- Participant 2 -->
              <div class="neu-raised flex items-center justify-between rounded-xl p-3">
                <div class="flex items-center gap-2.5">
                  <div class="size-8 rounded-full bg-emerald-600 text-white font-bold flex items-center justify-center">ZO</div>
                  <div>
                    <div class="font-bold text-slate-900 dark:text-slate-100">Zoe Singer</div>
                    <div class="text-[10px] text-slate-500">Thành viên</div>
                  </div>
                </div>
                <div class="flex gap-1.5">
                  <button class="neu-button px-2 py-1 rounded-lg text-[10px] font-bold text-amber-600">Tắt mic</button>
                  <button class="neu-button px-2 py-1 rounded-lg text-[10px] font-bold text-red-600">Mời ra</button>
                </div>
              </div>

              <!-- Participant 3 -->
              <div class="neu-raised flex items-center justify-between rounded-xl p-3">
                <div class="flex items-center gap-2.5">
                  <div class="size-8 rounded-full bg-amber-600 text-white font-bold flex items-center justify-center">TH</div>
                  <div>
                    <div class="font-bold text-slate-900 dark:text-slate-100">TH Producer</div>
                    <div class="text-[10px] text-slate-500">Thành viên</div>
                  </div>
                </div>
                <div class="flex gap-1.5">
                  <button class="neu-button px-2 py-1 rounded-lg text-[10px] font-bold text-amber-600">Tắt mic</button>
                  <button class="neu-button px-2 py-1 rounded-lg text-[10px] font-bold text-red-600">Mời ra</button>
                </div>
              </div>

              <!-- Participant 4 -->
              <div class="neu-raised flex items-center justify-between rounded-xl p-3">
                <div class="flex items-center gap-2.5">
                  <div class="size-8 rounded-full bg-rose-600 text-white font-bold flex items-center justify-center">MC</div>
                  <div>
                    <div class="font-bold text-slate-900 dark:text-slate-100">Mix Master C</div>
                    <div class="text-[10px] text-slate-500">Thành viên</div>
                  </div>
                </div>
                <div class="flex gap-1.5">
                  <button class="neu-button px-2 py-1 rounded-lg text-[10px] font-bold text-amber-600">Tắt mic</button>
                  <button class="neu-button px-2 py-1 rounded-lg text-[10px] font-bold text-red-600">Mời ra</button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <footer class="flex items-center justify-between border-t border-slate-300/60 bg-[#e0e5ec] px-6 py-3 dark:border-slate-800 dark:bg-[#1e222b]">
          <span class="text-xs font-semibold text-slate-500">Quyền điều hành: Chủ phòng có toàn quyền duyệt, tắt mic và mời thành viên ra khỏi phòng.</span>
        </footer>
      </div>
    `;
  });

  await mainLoc.screenshot({ path: path.join(outputDir, "lr-07.png") });
  console.log("✓ Saved LR-07 (Participants Moderation) lr-07.png!");

  await browser.close();
}

main().catch((err) => {
  console.error("Error capturing lr-06 and lr-07:", err);
  process.exit(1);
});
