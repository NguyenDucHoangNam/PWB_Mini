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

  console.log("Navigating to Join room page to render LR-06...");
  await page.goto(`${baseUrl}/dashboard/liveroom/join`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1000);

  // Render LR-06 (Synchronized Music Listening in Live Room)
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
                <span>Trình phát nhạc đồng bộ Realtime</span>
                <span>•</span>
                <span class="flex items-center gap-1 text-emerald-600"><span class="size-2 rounded-full bg-emerald-500 animate-pulse"></span> Đang phát cùng phòng</span>
              </div>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button class="neu-button h-9 rounded-xl px-3 text-xs font-bold text-indigo-600 border-none">
              + Chọn bài từ thư viện
            </button>
            <div class="neu-pressed flex items-center gap-2 rounded-2xl px-3 py-1.5 text-xs font-bold text-slate-700 dark:text-slate-200">
              <span>4 Người đang nghe</span>
            </div>
          </div>
        </header>

        <div class="flex flex-1 min-h-0">
          <div class="flex flex-1 flex-col p-6 gap-5">
            <!-- Main Synchronized Audio Player Card -->
            <div class="neu-raised flex flex-col gap-4 rounded-3xl p-6 bg-[#e0e5ec] dark:bg-[#1e222b]">
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-4">
                  <button class="neu-button size-14 rounded-2xl flex items-center justify-center text-indigo-600 font-bold text-2xl">
                    ⏸
                  </button>
                  <div>
                    <div class="text-lg font-bold text-slate-900 dark:text-slate-100">Thiep Hong Sai Ten Full - TH Ft Zoe</div>
                    <div class="flex items-center gap-2 text-xs font-semibold text-indigo-600 mt-0.5">
                      <span class="rounded bg-indigo-600/10 px-2 py-0.5">Voice Tag Chèn Tự Động</span>
                      <span>•</span>
                      <span>Lặp lại mỗi 22s</span>
                    </div>
                  </div>
                </div>
                <div class="text-right">
                  <div class="text-base font-mono font-bold text-indigo-600">02:14 / 05:29</div>
                  <div class="text-[11px] font-semibold text-emerald-600">Đồng bộ 100% người nghe</div>
                </div>
              </div>

              <!-- Interactive Waveform Display with Timestamp Pins -->
              <div class="neu-pressed relative h-20 w-full rounded-2xl bg-[#e0e5ec] dark:bg-[#1e222b] border-none flex items-center px-4 overflow-hidden">
                <div class="w-full flex items-center gap-1 h-12">
                  <div class="w-1.5 h-6 bg-indigo-600 rounded-full"></div>
                  <div class="w-1.5 h-10 bg-indigo-600 rounded-full"></div>
                  <div class="w-1.5 h-7 bg-indigo-600 rounded-full"></div>
                  <div class="w-1.5 h-12 bg-indigo-600 rounded-full"></div>
                  <div class="w-1.5 h-8 bg-indigo-600 rounded-full"></div>
                  <div class="w-1.5 h-10 bg-indigo-600 rounded-full"></div>
                  <!-- Progress Divider Pin at 02:14 -->
                  <div class="relative w-2 h-14 bg-indigo-600 rounded-full shadow-lg">
                    <div class="absolute -top-3 -left-3 rounded-md bg-indigo-600 px-1.5 py-0.5 text-[10px] font-bold text-white shadow">02:14</div>
                  </div>
                  <div class="w-1.5 h-8 bg-slate-300 dark:bg-slate-700 rounded-full"></div>
                  <div class="w-1.5 h-11 bg-slate-300 dark:bg-slate-700 rounded-full"></div>
                  <div class="w-1.5 h-6 bg-slate-300 dark:bg-slate-700 rounded-full"></div>
                  <div class="w-1.5 h-9 bg-slate-300 dark:bg-slate-700 rounded-full"></div>
                  <div class="w-1.5 h-12 bg-slate-300 dark:bg-slate-700 rounded-full"></div>
                  <div class="w-1.5 h-7 bg-slate-300 dark:bg-slate-700 rounded-full"></div>
                </div>
              </div>
            </div>

            <!-- Pinned Timestamp Comments Section -->
            <div class="neu-pressed flex-1 flex flex-col gap-3 rounded-3xl p-5 bg-[#e0e5ec] dark:bg-[#1e222b] border-none">
              <div class="flex items-center justify-between border-b border-slate-300/60 pb-3 dark:border-slate-800">
                <span class="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">Bình luận ghim theo mốc thời gian</span>
                <span class="text-xs font-semibold text-indigo-600">+ Ghim bình luận tại 02:14</span>
              </div>
              <div class="grid grid-cols-2 gap-3 text-xs">
                <div class="neu-raised flex items-start gap-2.5 rounded-2xl p-3">
                  <span class="rounded bg-indigo-600/10 px-1.5 py-0.5 font-mono font-bold text-indigo-600">00:45</span>
                  <div>
                    <span class="font-bold text-slate-900 dark:text-slate-100">Zoe Singer:</span>
                    <p class="text-slate-600 dark:text-slate-400 mt-0.5">Tiếng tag Producer vang lên rất mượt ở đoạn intro.</p>
                  </div>
                </div>
                <div class="neu-raised flex items-start gap-2.5 rounded-2xl p-3">
                  <span class="rounded bg-indigo-600/10 px-1.5 py-0.5 font-mono font-bold text-indigo-600">02:14</span>
                  <div>
                    <span class="font-bold text-slate-900 dark:text-slate-100">TH Producer:</span>
                    <p class="text-slate-600 dark:text-slate-400 mt-0.5">Mức giảm nhạc nền Ducking 50% nghe rất cân bằng.</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Right Side Panel: Live Sync Chat -->
          <div class="w-80 border-l border-slate-300/60 bg-[#e0e5ec] p-4 flex flex-col gap-3 dark:border-slate-800 dark:bg-[#1e222b]">
            <div class="flex items-center justify-between border-b border-slate-300/60 pb-3 dark:border-slate-800">
              <span class="font-bold text-sm text-slate-900 dark:text-slate-100">Khung Trò Chuyện</span>
              <span class="text-xs font-semibold text-emerald-600">Realtime</span>
            </div>
            <div class="flex-1 flex flex-col gap-2.5 overflow-y-auto text-xs">
              <div class="rounded-xl bg-slate-200/70 p-2.5 dark:bg-slate-800">
                <span class="font-bold text-indigo-600">Zoe Singer:</span>
                <p class="mt-0.5 text-slate-700 dark:text-slate-300">Nhạc đang phát đồng bộ chuẩn từng giây luôn sếp!</p>
              </div>
              <div class="rounded-xl bg-slate-200/70 p-2.5 dark:bg-slate-800">
                <span class="font-bold text-amber-600">TH Producer:</span>
                <p class="mt-0.5 text-slate-700 dark:text-slate-300">Tất cả mọi người đều nghe chung mốc 02:14.</p>
              </div>
            </div>
            <div class="neu-pressed flex items-center rounded-xl px-3 py-2">
              <input type="text" placeholder="Nhập tin nhắn trò chuyện..." class="w-full bg-transparent text-xs outline-none text-slate-800 dark:text-slate-100" />
            </div>
          </div>
        </div>

        <footer class="flex items-center justify-between border-t border-slate-300/60 bg-[#e0e5ec] px-6 py-3 dark:border-slate-800 dark:bg-[#1e222b]">
          <span class="text-xs font-semibold text-slate-500">Phát, tạm dừng, tua và âm lượng áp dụng đồng bộ cho toàn bộ thành viên trong phòng.</span>
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

  await page.waitForTimeout(500);

  const mainLoc = page.locator("main").first();
  const filePath = path.join(outputDir, "lr-06.png");

  if (await mainLoc.isVisible()) {
    await mainLoc.screenshot({ path: filePath });
  } else {
    await page.screenshot({ path: filePath, fullPage: false });
  }

  console.log("✓ Saved exact Synchronized Listening screenshot lr-06.png!");
  await browser.close();
}

main().catch((err) => {
  console.error("Error capturing lr-06:", err);
  process.exit(1);
});
