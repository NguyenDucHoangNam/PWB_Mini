"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";

type TabKey = "songs" | "voiceTags";

const TABS: { key: TabKey; href: string }[] = [
  { key: "songs", href: "/dashboard/songs" },
  { key: "voiceTags", href: "/dashboard/voice-tags" },
];

const TAB_ACTIONS: Record<TabKey, { href: string; i18nKey: string }> = {
  songs: { href: "/dashboard/songs/new", i18nKey: "uploadBtn" },
  voiceTags: { href: "/dashboard/voice-tags/new", i18nKey: "createBtn" },
};

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
  const action = TAB_ACTIONS[activeTab];
  const heading = activeTab === "songs" ? tSongs("title") : tVoiceTags("title");
  const subtitle = activeTab === "songs" ? tSongs("subtitle") : tVoiceTags("subtitle");

  return (
    <header className="flex flex-col gap-4 border-b border-border pb-4 sm:gap-5 sm:pb-5">
      <div className="flex flex-col gap-1">
        <h1 className="text-xl font-semibold tracking-tight text-foreground sm:text-2xl">
          {heading}
        </h1>
        <p className="text-sm leading-relaxed text-muted-foreground">{subtitle}</p>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <nav
          aria-label={heading}
          className="grid grid-cols-2 gap-1 rounded-lg border border-border bg-secondary p-1 sm:inline-flex sm:w-auto"
        >
          {TABS.map((tab) => {
            const isActive = activeTab === tab.key;
            return (
              <Link
                key={tab.key}
                href={tab.href}
                aria-current={isActive ? "page" : undefined}
                className={`key-press flex h-10 items-center justify-center rounded-md px-4 text-sm font-medium beat-16th transition-colors ease-hammer sm:h-7 sm:text-xs ${
                  isActive
                    ? "bg-background text-foreground shadow-xs"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                {t(`tabs.${tab.key}`)}
              </Link>
            );
          })}
        </nav>

        <Link href={action.href} className="w-full sm:w-auto">
          <Button size="lg" className="h-11 w-full font-semibold sm:h-9 sm:w-auto">
            {t(action.i18nKey)}
          </Button>
        </Link>
      </div>
    </header>
  );
}
