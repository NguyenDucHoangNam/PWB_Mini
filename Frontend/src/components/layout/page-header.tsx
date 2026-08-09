"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { cn } from "@/lib/utils";
import {
  NEU_ACCENT_TEXT,
  NEU_FOCUS,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuPanel,
} from "@/components/ui/neu";

export interface PageHeaderTab {
  key: string;
  href: string;
  label: string;
  icon?: React.ComponentType<{ className?: string; "aria-hidden"?: boolean | "true" }>;
}

export interface PageHeaderProps {
  title: string;
  subtitle?: string;
  /** Sits in a sunken tile beside the title. */
  icon?: React.ComponentType<{ className?: string; "aria-hidden"?: boolean | "true" }>;
  /** Primary/secondary buttons for the page, right-aligned on desktop. */
  actions?: ReactNode;
  tabs?: PageHeaderTab[];
  activeTabKey?: string;
  /** Accessible name for the tab strip; falls back to the title. */
  tabsLabel?: string;
  className?: string;
}

/**
 * The page title block, shared by every dashboard screen.
 *
 * Raised slab, sunken icon well, and — when a screen has sibling views — a
 * sunken tab track whose active pill is the only thing lifted out of it. The
 * active tab is *also* marked by accent colour and a dot, because a shadow on
 * its own is invisible to anyone who cannot see the shadow.
 */
export function PageHeader({
  title,
  subtitle,
  icon: Icon,
  actions,
  tabs,
  activeTabKey,
  tabsLabel,
  className,
}: PageHeaderProps) {
  return (
    <NeuPanel as="header" className={cn("flex flex-col gap-5 p-5 sm:p-6", className)}>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 items-center gap-4">
          {Icon && (
            <span
              className="neu-pressed grid size-12 shrink-0 place-items-center rounded-2xl border-none sm:size-14"
              aria-hidden="true"
            >
              <Icon className={cn("size-5 sm:size-6", NEU_ACCENT_TEXT)} />
            </span>
          )}
          <div className="flex min-w-0 flex-col gap-1">
            <h1 className={cn("truncate text-xl font-bold tracking-tight sm:text-2xl", NEU_TEXT)}>
              {title}
            </h1>
            {subtitle && (
              <p
                className={cn(
                  "text-[11px] font-semibold uppercase tracking-[0.16em]",
                  NEU_TEXT_MUTED,
                )}
              >
                {subtitle}
              </p>
            )}
          </div>
        </div>

        {actions && <div className="flex shrink-0 items-center gap-3">{actions}</div>}
      </div>

      {tabs && tabs.length > 0 && (
        <nav
          aria-label={tabsLabel ?? title}
          className="neu-pressed grid grid-cols-2 gap-2 rounded-2xl border-none p-2 sm:inline-flex sm:w-auto sm:self-start"
        >
          {tabs.map((tab) => {
            const TabIcon = tab.icon;
            const isActive = tab.key === activeTabKey;
            return (
              <Link
                key={tab.key}
                href={tab.href}
                aria-current={isActive ? "page" : undefined}
                className={cn(
                  "flex h-10 items-center justify-center gap-2 rounded-xl border-none px-4 text-xs font-bold tracking-wide transition-all sm:px-6",
                  NEU_FOCUS,
                  isActive
                    ? cn("neu-raised-sm", NEU_ACCENT_TEXT)
                    : "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100",
                )}
              >
                {TabIcon && <TabIcon className="size-4" aria-hidden="true" />}
                <span>{tab.label}</span>
                {isActive && (
                  <span
                    className="size-1.5 rounded-full bg-indigo-600 dark:bg-indigo-400"
                    aria-hidden="true"
                  />
                )}
              </Link>
            );
          })}
        </nav>
      )}
    </NeuPanel>
  );
}
