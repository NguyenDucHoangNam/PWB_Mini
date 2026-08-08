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
    <div className="flex items-center gap-2 font-mono text-sm font-semibold text-neutral-400 dark:text-neutral-500 select-none">
      <button
        onClick={() => switchLocale("vi")}
        type="button"
        className={`key-press hover:text-black dark:hover:text-white transition-colors cursor-pointer h-9 px-1.5 flex items-center ${
          currentLocale === "vi" ? "text-black dark:text-white font-bold" : ""
        }`}
      >
        VI
      </button>
      <span className="text-neutral-200 dark:text-neutral-800">|</span>
      <button
        onClick={() => switchLocale("en")}
        type="button"
        className={`key-press hover:text-black dark:hover:text-white transition-colors cursor-pointer h-9 px-1.5 flex items-center ${
          currentLocale === "en" ? "text-black dark:text-white font-bold" : ""
        }`}
      >
        EN
      </button>
    </div>
  );
}