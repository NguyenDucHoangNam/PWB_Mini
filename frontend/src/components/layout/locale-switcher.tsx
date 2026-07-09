"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

export function LocaleSwitcher() {
  const router = useRouter();
  const [currentLocale, setCurrentLocale] = useState("vi");

  // Read current locale cookie value on mount
  useEffect(() => {
    const getLocaleCookie = () => {
      const match = document.cookie.match(/(^| )locale=([^;]+)/);
      return match ? match[2] : "vi";
    };
    setCurrentLocale(getLocaleCookie());
  }, []);

  const switchLocale = (newLocale: string) => {
    if (newLocale === currentLocale) return;

    // Set locale cookie for 1 year
    document.cookie = `locale=${newLocale}; path=/; max-age=31536000; SameSite=Lax`;
    setCurrentLocale(newLocale);

    // Refresh page to load new translation messages via next-intl
    window.location.reload();
  };

  return (
    <div className="flex items-center gap-2 font-mono text-sm font-semibold text-neutral-400 dark:text-neutral-500 select-none">
      <button
        onClick={() => switchLocale("vi")}
        type="button"
        className={`hover:text-black dark:hover:text-white transition-colors cursor-pointer h-9 px-1.5 flex items-center ${
          currentLocale === "vi" ? "text-black dark:text-white font-bold" : ""
        }`}
      >
        VI
      </button>
      <span className="text-neutral-200 dark:text-neutral-800">|</span>
      <button
        onClick={() => switchLocale("en")}
        type="button"
        className={`hover:text-black dark:hover:text-white transition-colors cursor-pointer h-9 px-1.5 flex items-center ${
          currentLocale === "en" ? "text-black dark:text-white font-bold" : ""
        }`}
      >
        EN
      </button>
    </div>
  );
}
