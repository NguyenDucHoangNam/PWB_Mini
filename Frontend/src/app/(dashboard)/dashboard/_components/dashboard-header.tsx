"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";

type TabKey = "songs" | "voiceTags";

const TABS: { key: TabKey; href: string }[] = [
  { key: "songs", href: "/dashboard/songs" },
  { key: "voiceTags", href: "/dashboard/voice-tags" },
];

function resolveActiveTab(pathname: string): TabKey {
  if (pathname.startsWith("/dashboard/voice-tags")) return "voiceTags";
  return "songs";
}

export function DashboardHeader() {
  const t = useTranslations("dashboard.voiceLibrary");
  const tSongs = useTranslations("voice.songs");
  const tVoiceTags = useTranslations("voice.voiceTags");
  const pathname = usePathname();

  const activeTab = resolveActiveTab(pathname);
  const heading = activeTab === "songs" ? tSongs("title") : tVoiceTags("title");
  const subtitle = activeTab === "songs" ? tSongs("subtitle") : tVoiceTags("subtitle");


  return (
    <header className="flex flex-col gap-4 border-b border-border pb-4 sm:gap-5 sm:pb-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-start gap-3">
          <div className="hidden h-12 w-1 shrink-0 rounded-full bg-foreground/80 sm:block" aria-hidden="true" />
          <div className="flex flex-col gap-0.5">
            <h1 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
              {heading}
            </h1>
            <p className="text-xs uppercase tracking-widest text-muted-foreground/70 sm:text-[11px]">
              {subtitle}
            </p>
          </div>
        </div>

        <nav
          aria-label={heading}
          className="grid h-11 grid-cols-2 items-center gap-1 rounded-lg border border-border bg-secondary p-1 sm:inline-flex sm:h-9 sm:w-auto"
        >
          {TABS.map((tab) => {
            const isActive = activeTab === tab.key;
            return (
              <Link
                key={tab.key}
                href={tab.href}
                aria-current={isActive ? "page" : undefined}
                className={`key-press flex h-9 items-center justify-center rounded-md px-5 text-xs font-semibold tracking-wide beat-16th transition-colors ease-hammer sm:h-7 ${
                  isActive
                    ? "border border-border bg-card text-foreground shadow-xs"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                {t(`tabs.${tab.key}`)}
              </Link>
            );
          })}
        </nav>
      </div>
    </header>
  );
}
