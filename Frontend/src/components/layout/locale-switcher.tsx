"use client";

import { useCallback, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";

import { DEFAULT_LOCALE } from "@/lib/config";

function readLocale(): string {
  if (typeof document === "undefined") return DEFAULT_LOCALE;
  const match = document.cookie.match(/(^| )locale=([^;]+)/);
  return match ? match[2] : DEFAULT_LOCALE;
}

function subscribeLocale(callback: () => void): () => void {
  if (typeof document === "undefined") return () => {};
  document.addEventListener("cookiechange", callback);
  window.addEventListener("app-locale-change", callback);
  return () => {
    document.removeEventListener("cookiechange", callback);
    window.removeEventListener("app-locale-change", callback);
  };
}

function getLocaleSnapshot(): string {
  return readLocale();
}

function getServerLocaleSnapshot(): string {
  return DEFAULT_LOCALE;
}

export function LocaleSwitcher() {
  const currentLocale = useSyncExternalStore(
    subscribeLocale,
    getLocaleSnapshot,
    getServerLocaleSnapshot,
  );
  const router = useRouter();

  const switchLocale = useCallback(
    (newLocale: string) => {
      if (newLocale === currentLocale) return;
      if (typeof document === "undefined") return;

      const secure = window.location.protocol === "https:" ? "; Secure" : "";
      document.cookie = `locale=${newLocale}; path=/; max-age=31536000; SameSite=Lax${secure}`;
      window.dispatchEvent(new Event("app-locale-change"));
      router.refresh();
    },
    [currentLocale, router],
  );

  return (
    <div className="neu-pressed flex items-center gap-1 rounded-full p-1 bg-[#e0e5ec] dark:bg-[#1e222b] font-mono text-xs font-bold select-none border-none">
      <button
        onClick={() => switchLocale("vi")}
        type="button"
        className={`flex h-7 items-center justify-center rounded-full px-3 transition-all focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 ${
          currentLocale === "vi"
            ? "neu-raised-sm text-indigo-600 dark:text-indigo-400 bg-[#e0e5ec] dark:bg-[#1e222b]"
            : "text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200"
        }`}
      >
        VI
      </button>
      <button
        onClick={() => switchLocale("en")}
        type="button"
        className={`flex h-7 items-center justify-center rounded-full px-3 transition-all focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 ${
          currentLocale === "en"
            ? "neu-raised-sm text-indigo-600 dark:text-indigo-400 bg-[#e0e5ec] dark:bg-[#1e222b]"
            : "text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200"
        }`}
      >
        EN
      </button>
    </div>
  );
}