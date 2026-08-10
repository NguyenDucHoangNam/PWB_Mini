"use client";

import { useCallback, useEffect, useState, type MouseEvent } from "react";
import { useReducedMotion } from "framer-motion";
import { useTranslations } from "next-intl";
import { TECHNICAL_SECTIONS, TECHNICAL_SECTION_IDS } from "@/features/showcase/lib/technical-sections";

/* Matches ANCHOR_OFFSET: a heading counts as "reached" once it clears the sticky
   header plus this bar, which is where the reader's eye actually lands. */
const ACTIVATION_LINE = 176;

function useActiveSection(ids: readonly string[]): string {
  const [active, setActive] = useState(ids[0]);

  useEffect(() => {
    let frame = 0;
    let timer = 0;

    const read = () => {
      if (frame) window.cancelAnimationFrame(frame);
      if (timer) window.clearTimeout(timer);
      frame = 0;
      timer = 0;

      /* The last section is usually too short to ever cross the activation line, so the
         bottom of the document has to claim it explicitly. */
      const atBottom =
        window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4;

      if (atBottom) {
        setActive(ids[ids.length - 1]);
        return;
      }

      let current = ids[0];
      for (const id of ids) {
        const element = document.getElementById(id);
        if (element && element.getBoundingClientRect().top <= ACTIVATION_LINE) {
          current = id;
        }
      }
      setActive(current);
    };

    /* rAF keeps the indicator in step with the scroll while the page is on screen, but it
       is throttled to nothing in a background tab — and correctness must not hinge on the
       frame loop running. So a timer is armed alongside it and whichever lands first does
       the read, cancelling the other. */
    const schedule = () => {
      if (!frame) frame = window.requestAnimationFrame(read);
      if (!timer) timer = window.setTimeout(read, 120);
    };

    read();
    window.addEventListener("scroll", schedule, { passive: true });
    window.addEventListener("resize", schedule);

    return () => {
      if (frame) window.cancelAnimationFrame(frame);
      if (timer) window.clearTimeout(timer);
      window.removeEventListener("scroll", schedule);
      window.removeEventListener("resize", schedule);
    };
  }, [ids]);

  return active;
}

export function TechnicalNav() {
  const t = useTranslations("features.technical.nav");
  const prefersReducedMotion = useReducedMotion();
  const active = useActiveSection(TECHNICAL_SECTION_IDS);

  /* Plain hrefs stay the no-JS fallback; this only upgrades the jump to a smooth one
     and keeps the hash in the URL so the link is still shareable. */
  const handleJump = useCallback(
    (event: MouseEvent<HTMLAnchorElement>, id: string) => {
      const element = document.getElementById(id);
      if (!element) return;

      event.preventDefault();
      const from = window.scrollY;
      element.scrollIntoView({
        behavior: prefersReducedMotion ? "auto" : "smooth",
        block: "start",
      });
      window.history.replaceState(null, "", `#${id}`);

      /* A smooth scroll is driven by the compositor, so anything that stops it producing
         frames — a background tab, an extension, a browser that quietly ignores the hint —
         leaves the reader exactly where they were, with the nav looking broken. If nothing
         has moved by now the animation was never going to run, so take the jump instantly.
         Any movement at all, animated or from the reader scrolling away, cancels this. */
      window.setTimeout(() => {
        if (window.scrollY === from) {
          element.scrollIntoView({ behavior: "auto", block: "start" });
        }
      }, 600);
    },
    [prefersReducedMotion],
  );

  return (
    <nav aria-label={t("title")} className="sticky top-24 z-30">
      <div className="neu-raised rounded-full bg-[#e0e5ec] p-2 dark:bg-[#1e222b]">
        <ul className="neu-scroll-thin flex items-center gap-1.5 overflow-x-auto">
          {TECHNICAL_SECTIONS.map(({ id, icon: Icon }, position) => {
            const isActive = id === active;

            return (
              <li key={id} className="shrink-0">
                <a
                  href={`#${id}`}
                  onClick={(event) => handleJump(event, id)}
                  aria-current={isActive ? "true" : undefined}
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
                </a>
              </li>
            );
          })}
        </ul>
      </div>
    </nav>
  );
}
