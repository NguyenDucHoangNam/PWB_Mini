"use client";

import { useCallback, useRef, type KeyboardEvent } from "react";
import { useTranslations } from "next-intl";
import { TECHNICAL_SECTIONS } from "@/features/showcase/lib/technical-sections";

interface TechnicalNavProps {
  active: string;
  onSelect: (id: string) => void;
}

/* One panel is mounted at a time, so this is a tablist, not a table of contents: the bar
   is the only way back out of a section, which is why it stays stuck to the top. */
export function TechnicalNav({ active, onSelect }: TechnicalNavProps) {
  const t = useTranslations("features.technical.nav");
  const tabsRef = useRef<(HTMLButtonElement | null)[]>([]);

  /* Automatic activation: arrows switch the panel outright rather than only moving focus.
     Focus follows without scrolling — the panel swap already owns the scroll position. */
  const handleKeyDown = useCallback(
    (event: KeyboardEvent<HTMLUListElement>) => {
      const count = TECHNICAL_SECTIONS.length;
      const current = TECHNICAL_SECTIONS.findIndex((section) => section.id === active);

      let next = -1;
      if (event.key === "ArrowRight") next = (current + 1) % count;
      else if (event.key === "ArrowLeft") next = (current - 1 + count) % count;
      else if (event.key === "Home") next = 0;
      else if (event.key === "End") next = count - 1;

      if (next === -1) return;

      event.preventDefault();
      onSelect(TECHNICAL_SECTIONS[next].id);
      tabsRef.current[next]?.focus({ preventScroll: true });
    },
    [active, onSelect],
  );

  return (
    <div className="sticky top-24 z-30">
      <div className="neu-raised rounded-full bg-[#e0e5ec] p-2 dark:bg-[#1e222b]">
        <ul
          role="tablist"
          aria-label={t("title")}
          onKeyDown={handleKeyDown}
          className="neu-scroll-thin flex items-center gap-1.5 overflow-x-auto"
        >
          {TECHNICAL_SECTIONS.map(({ id, icon: Icon }, position) => {
            const isActive = id === active;

            return (
              <li key={id} className="shrink-0">
                <button
                  type="button"
                  role="tab"
                  id={`${id}-tab`}
                  ref={(node) => {
                    tabsRef.current[position] = node;
                  }}
                  aria-selected={isActive}
                  aria-controls={id}
                  tabIndex={isActive ? 0 : -1}
                  onClick={() => onSelect(id)}
                  className={[
                    "group flex items-center gap-2.5 rounded-full px-4 py-2.5 text-sm font-semibold beat-16th transition-all",
                    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600",
                    isActive
                      ? "neu-pressed-sm bg-[#e0e5ec] text-indigo-600 dark:bg-[#1e222b] dark:text-indigo-400"
                      : "text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-slate-100",
                  ].join(" ")}
                >
                  {/* Shadows carry no measurable contrast, so the active state also gets a
                      colour swap and a dot — cues that survive a contrast check. */}
                  <span
                    aria-hidden="true"
                    className={[
                      "size-1.5 shrink-0 rounded-full beat-16th transition-colors",
                      isActive
                        ? "bg-indigo-600 dark:bg-indigo-400"
                        : "bg-slate-400/60 dark:bg-slate-500/60",
                    ].join(" ")}
                  />
                  <Icon aria-hidden="true" className="size-4 shrink-0" />
                  <span className="whitespace-nowrap">{t(id)}</span>
                  <span className="font-mono text-[0.65rem] font-bold tabular-nums text-slate-400 dark:text-slate-500">
                    {String(position + 1).padStart(2, "0")}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}
