"use client";

import Link from "next/link";
import type { ReactNode } from "react";

interface StatCell {
  value: string | number;
  label: string;
}

interface StatCardProps {
  title: string;
  description: string;
  primary?: StatCell;
  secondary?: StatCell[];
  ctaLabel: string;
  ctaHref: string;
  footer?: ReactNode;
  children?: ReactNode;
}

export function StatCard({
  title,
  description,
  primary,
  secondary,
  ctaLabel,
  ctaHref,
  footer,
  children,
}: StatCardProps) {
  return (
    <div className="flex h-full flex-col gap-5 rounded-xl border border-neutral-200 bg-white p-5 sm:p-6 dark:border-neutral-800 dark:bg-black">
      <div className="flex flex-col gap-2">
        <h2 className="text-lg font-bold text-black dark:text-white">{title}</h2>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">{description}</p>
      </div>

      {(primary || (secondary && secondary.length > 0)) && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {primary && (
            <div className="rounded-lg border border-neutral-200 bg-neutral-50 p-4 dark:border-neutral-800 dark:bg-neutral-900/40">
              <div className="text-2xl font-bold tabular-nums text-black dark:text-white">
                {primary.value}
              </div>
              <div className="mt-1 text-xs font-medium text-neutral-500 dark:text-neutral-400">
                {primary.label}
              </div>
            </div>
          )}
          {secondary?.map((cell) => (
            <div
              key={cell.label}
              className="rounded-lg border border-neutral-200 p-4 dark:border-neutral-800"
            >
              <div className="text-xl font-bold tabular-nums text-black dark:text-white">
                {cell.value}
              </div>
              <div className="mt-1 text-xs font-medium text-neutral-500 dark:text-neutral-400">
                {cell.label}
              </div>
            </div>
          ))}
        </div>
      )}

      {children}

      <div className="mt-auto flex items-center justify-between gap-3">
        <Link
          href={ctaHref}
          className="text-sm font-semibold text-black underline-offset-4 hover:underline dark:text-white"
        >
          {ctaLabel}
        </Link>
        {footer}
      </div>
    </div>
  );
}
