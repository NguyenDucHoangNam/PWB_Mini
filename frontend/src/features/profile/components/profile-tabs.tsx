"use client";

import { useId } from "react";
import { ShieldCheck, User as UserIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import type { ProfileTabId } from "../hooks/use-profile-hash-tab";

interface ProfileTab {
  id: ProfileTabId;
  icon: React.ComponentType<{ className?: string }>;
  labelKey: "personal" | "security";
}

const PROFILE_TABS: readonly ProfileTab[] = [
  { id: "personal", icon: UserIcon, labelKey: "personal" },
  { id: "security", icon: ShieldCheck, labelKey: "security" },
] as const;

export interface ProfileTabsProps {
  activeTab: ProfileTabId;
  onChange: (tab: ProfileTabId) => void;
  labels: { personal: string; security: string };
}

export function ProfileTabs({ activeTab, onChange, labels }: ProfileTabsProps) {
  const baseId = useId();

  return (
    <div
      role="tablist"
      aria-orientation="horizontal"
      className="flex gap-1 border-b border-neutral-200 bg-neutral-50/60 px-2 dark:border-neutral-800 dark:bg-neutral-900/40 sm:px-3"
    >
      {PROFILE_TABS.map((tab) => {
        const Icon = tab.icon;
        const tabId = `${baseId}-tab-${tab.id}`;
        const panelId = `${baseId}-panel-${tab.id}`;
        const isActive = activeTab === tab.id;
        return (
          <button
            key={tab.id}
            id={tabId}
            role="tab"
            type="button"
            aria-selected={isActive}
            aria-controls={panelId}
            tabIndex={isActive ? 0 : -1}
            onClick={() => onChange(tab.id)}
            onKeyDown={(event) => {
              if (event.key === "ArrowRight" || event.key === "ArrowDown") {
                event.preventDefault();
                const currentIdx = PROFILE_TABS.findIndex((t) => t.id === activeTab);
                const nextIdx = (currentIdx + 1) % PROFILE_TABS.length;
                onChange(PROFILE_TABS[nextIdx].id);
              } else if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
                event.preventDefault();
                const currentIdx = PROFILE_TABS.findIndex((t) => t.id === activeTab);
                const nextIdx = (currentIdx - 1 + PROFILE_TABS.length) % PROFILE_TABS.length;
                onChange(PROFILE_TABS[nextIdx].id);
              }
            }}
            className={cn(
              "relative flex items-center gap-2 px-3 py-3 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-black focus-visible:ring-offset-2 dark:focus-visible:ring-white dark:focus-visible:ring-offset-neutral-950 sm:px-4",
              isActive
                ? "text-neutral-900 dark:text-neutral-50"
                : "text-neutral-500 hover:text-neutral-700 dark:text-neutral-400 dark:hover:text-neutral-200",
            )}
          >
            <Icon className="size-4" aria-hidden="true" />
            <span>{labels[tab.labelKey]}</span>
            {isActive && (
              <span className="absolute inset-x-3 bottom-0 h-0.5 rounded-full bg-neutral-900 dark:bg-neutral-50 sm:inset-x-4" />
            )}
          </button>
        );
      })}
    </div>
  );
}
