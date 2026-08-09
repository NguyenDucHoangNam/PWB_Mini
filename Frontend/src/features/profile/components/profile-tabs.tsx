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
      className="neu-pressed flex w-full gap-2 rounded-2xl bg-[#e0e5ec] p-2 dark:bg-[#1e222b]"
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
              "flex flex-1 items-center justify-center gap-2.5 rounded-xl px-4 py-3 text-sm font-semibold transition-all focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2",
              isActive
                ? "neu-raised text-indigo-600 dark:text-indigo-400 bg-[#e0e5ec] dark:bg-[#1e222b]"
                : "text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200",
            )}
          >
            <Icon className="size-4" aria-hidden="true" />
            <span>{labels[tab.labelKey]}</span>
            {isActive && (
              <span
                className="size-1.5 rounded-full bg-indigo-600 dark:bg-indigo-400"
                aria-hidden="true"
              />
            )}
          </button>
        );
      })}
    </div>
  );
}
